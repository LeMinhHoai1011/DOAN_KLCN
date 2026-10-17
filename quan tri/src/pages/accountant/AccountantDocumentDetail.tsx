import { formatDateTime } from '../../utils/dateTime'
import DocumentArchivePanel from '../../components/DocumentArchivePanel';
import DocumentPaymentPanel from '../../components/DocumentPaymentPanel';
import { categoryTypeLabel } from '../../utils/accountingCategoryPresentation';
import BusinessStateCard from "../../components/BusinessStateCard";
import ProcessingHistoryCard from "../../components/ProcessingHistoryCard";
import type { PipelineHistory } from "../../services/documentService";
import type { DocumentAssessment } from "../../services/documentService";
﻿import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Download, Pencil, Save, X } from 'lucide-react';
import clsx from 'clsx';
import StatusBadge from '../../components/StatusBadge';
import classificationService from '../../services/classificationService';
import accountingCategoryService, { type AccountingCategory } from '../../services/accountingCategoryService';
import documentService, { mapDocument } from '../../services/documentService';
import invoiceService from '../../services/invoiceService';
import reconciliationService, { type Reconciliation } from '../../services/reconciliationService';
import type { ClassificationResponse } from '../../services/classificationService';
import type { DocumentResponse, DocumentType, ExtractedFieldResponse, OCRResultResponse, WorkflowAction } from '../../services/documentService';
import type { InvoiceResponse } from '../../services/invoiceService';
import { getEffectiveRole } from '../../services/authService';
import EmptyState from '../../components/ui/EmptyState';
import ErrorState from '../../components/ui/ErrorState';
import LoadingState from '../../components/ui/LoadingState';
import OcrDocumentPreview from '../../components/OcrDocumentPreview';
import VatInvoicePanel, { documentTypeLabel, emptyInvoiceForm, toInvoiceForm, toInvoiceUpdateRequest, type InvoiceFormData } from '../../components/VatInvoicePanel';
import { formatConfidence } from '../../utils/confidence';
import { listReadOutcome } from '../../services/readState';
import { hasInsufficientReconciliationData } from '../../utils/reconciliationPresentation';
import ReconciliationResultCard from '../../components/ReconciliationResultCard';

type DetailTab = 'overview' | 'items' | 'reconciliation' | 'details' | 'storage' | 'versions' | 'history';
type RemoteState = 'idle' | 'loading' | 'empty' | 'success' | 'error';

const money = (value: number | null | undefined) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(value)} đ`;
const rate = (value: number | null) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(Math.abs(value) <= 1 ? value * 100 : value)}%`;
const valueOrDash = (value: string | number | null | undefined) => value == null || value === '' ? '—' : String(value);

const humanizeFieldName = (name: string) => name
  .replace(/([a-z])([A-Z])/g, '$1 $2')
  .replace(/[_-]+/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()
  .replace(/^./, (character) => character.toUpperCase());

const distinctMeaningfulFields = (fields: ExtractedFieldResponse[]) => {
  const seen = new Set<string>();
  const core = new Set(['invoicenumber', 'invoiceseries', 'invoicedate', 'sellername', 'sellertaxcode', 'selleraddress',
    'sellerphone', 'buyername', 'buyertaxcode', 'buyeraddress', 'subtotal', 'vatamount', 'taxamount', 'totalamount',
    'paymentmethod', 'amountinwords', 'taxauthoritycode', 'signdate', 'items']);
  const ignored = new Set(['recruitment', 'advertisement', 'marketingtext', 'slogan']);
  return fields.filter((field) => {
    const name = field.fieldName?.trim();
    const value = field.fieldValue?.trim();
    if (!name || !value) return false;
    const key = name.toLocaleLowerCase();
    const canonical = key.replace(/[^a-z0-9]/g, '');
    if (core.has(canonical) || ignored.has(canonical)) return false;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
};

const companyRoleLabels: Record<string, string> = { SELLER: 'Người bán', BUYER: 'Người mua', ISSUER: 'Bên phát hành', RECIPIENT: 'Bên nhận', INTERNAL: 'Nội bộ', UNRELATED: 'Không liên quan', UNKNOWN: 'Chưa xác định' };
const directionLabels: Record<string, string> = { INCOMING: 'Chứng từ đầu vào', OUTGOING: 'Chứng từ đầu ra', INTERNAL: 'Nội bộ', UNKNOWN: 'Chưa xác định' };
const assessmentLabels: Record<string, string> = { INCOME: 'Thu', EXPENSE: 'Chi', TRANSFER: 'Chuyển khoản nội bộ', NON_FINANCIAL: 'Không phát sinh tài chính', UNKNOWN: 'Chưa xác định' };

const hasCompletedPipeline = (status: DocumentResponse['status']) => (
  status === 'PROCESSED' || status === 'NEED_REVIEW' || status === 'COMPLETED'
);

const isInvoiceDocument = (documentType: string | null) => {
  const normalized = documentType?.trim().toUpperCase().replace(/[- ]/g, '_');
  return normalized === 'INVOICE' || normalized === 'VAT_INVOICE' || Boolean(normalized?.endsWith('_INVOICE'));
};

const optionalRequest = <T,>(enabled: boolean, request: () => Promise<T>, fallback: T) => (
  enabled ? request() : Promise.resolve(fallback)
);

const AccountantDocumentDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const documentId = Number(id);
  const [document, setDocument] = useState<DocumentResponse | null>(null);
  const [ocr, setOcr] = useState<OCRResultResponse | null>(null);
  const [businessState, setBusinessState] = useState<import("../../services/documentService").DocumentBusinessState | null>(null);
  const [classification, setClassification] = useState<ClassificationResponse | null>(null);
  const [processingHistory, setProcessingHistory] = useState<PipelineHistory | null>(null);
  const [historyError, setHistoryError] = useState('');
  const [retrying, setRetrying] = useState(false);
  const [reloadVersion, setReloadVersion] = useState(0);
  const [assessment, setAssessment] = useState<DocumentAssessment | null>(null);
  const [assessmentError, setAssessmentError] = useState('');
  const [invoice, setInvoice] = useState<InvoiceResponse | null>(null);
  const [reconciliations, setReconciliations] = useState<Reconciliation[]>([]);
  const [extractedFields, setExtractedFields] = useState<ExtractedFieldResponse[]>([]);
  const [invoiceForm, setInvoiceForm] = useState<InvoiceFormData>(emptyInvoiceForm);
  const [classificationValue, setClassificationValue] = useState('');
  const [documentTypes, setDocumentTypes] = useState<DocumentType[]>([]);
  const [documentTypeId, setDocumentTypeId] = useState('');
  const [accountingCategories, setAccountingCategories] = useState<AccountingCategory[]>([]);
  const [categoryState, setCategoryState] = useState<RemoteState>('idle');
  const [classificationState, setClassificationState] = useState<RemoteState>('idle');
  const [isReconciling, setIsReconciling] = useState(false);
  const [reconciliationError, setReconciliationError] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [error, setError] = useState('');
  const [saveMessage, setSaveMessage] = useState('');
  const [saveError, setSaveError] = useState('');
  const [sectionErrors, setSectionErrors] = useState<string[]>([]);
  const [workflowNote, setWorkflowNote] = useState('');
  const [workflowAction, setWorkflowAction] = useState<WorkflowAction | null>(null);
  const [isWorkflowPending, setIsWorkflowPending] = useState(false);
  const [workflowError, setWorkflowError] = useState('');
  const [workflowMessage, setWorkflowMessage] = useState('');
  const [activeTab, setActiveTab] = useState<DetailTab>('overview');
  const role = getEffectiveRole();
  const hasCorrectionRole = role === 'ADMIN' || role === 'ACCOUNTANT';
  const isEmployeeView = role === 'EMPLOYEE' || role === 'USER';
  const basePath = role === 'ADMIN' ? '/admin' : role === 'EMPLOYEE' ? '/employee' : '/accountant';

  useEffect(() => {
    let isMounted = true;

    if (!Number.isInteger(documentId)) {
      setError('Mã chứng từ không hợp lệ');
      setIsLoading(false);
      return () => { isMounted = false; };
    }

    const loadDocumentDetail = async () => {
      let loadedDocument: DocumentResponse;
      try {
        loadedDocument = await documentService.getDocumentById(documentId);
      } catch {
        if (isMounted) {
          setError('Không thể tải thông tin chứng từ');
          setIsLoading(false);
        }
        return;
      }
      if (!isMounted) return;

      setDocument(loadedDocument);
      setProcessingHistory(null); setHistoryError('');
      try { const history = await documentService.getProcessingHistory(documentId); if (isMounted) setProcessingHistory(history); }
      catch { if (isMounted) setHistoryError('Không thể tải lịch sử xử lý.'); }
      if (!isMounted) return;
      setAssessment(null);
      setAssessmentError('');
      try {
        const result = await documentService.getDocumentAssessment(documentId);
        if (isMounted) setAssessment(result);
      } catch {
        if (isMounted) setAssessmentError('Không thể tải đánh giá OCR/AI.');
      }
      if (!isMounted) return;
      setDocumentTypeId(loadedDocument.typeId == null ? '' : String(loadedDocument.typeId));
      if (hasCorrectionRole) try {
        setDocumentTypes((await documentService.getDocumentTypes()).filter(type => type.active));
      } catch {
        setSectionErrors((current) => [...current, 'Không thể tải danh sách loại chứng từ.']);
      }
      if (hasCorrectionRole) try {
        setCategoryState('loading');
        const categories = await accountingCategoryService.getCategories(role === 'ADMIN' ? loadedDocument.companyId : undefined);
        setAccountingCategories(categories);
          setCategoryState(listReadOutcome(200, categories.length));
      } catch {
        setCategoryState('error');
        setSectionErrors((current) => [...current, 'Không thể tải danh sách nhóm nghiệp vụ của công ty.']);
      }
      setBusinessState(null);
      try { setBusinessState(await documentService.getBusinessState(documentId)); } catch { /* Explicit unavailable state, never guess approval or payment. */ }
      const completed = hasCompletedPipeline(loadedDocument.status);
      setClassificationState(completed ? 'loading' : 'empty');
      const shouldLoadOcr = loadedDocument.status !== 'UPLOADED';
      const shouldLoadInvoice = completed && isInvoiceDocument(loadedDocument.documentType);
      const [ocrResult, classificationResult, invoiceResult, extractedFieldsResult, reconciliationResult] = await Promise.allSettled([
        optionalRequest(shouldLoadOcr, () => documentService.getDocumentOCR(documentId), null),
        optionalRequest(completed, () => classificationService.getClassificationOrNull(documentId), null),
        optionalRequest(shouldLoadInvoice, () => invoiceService.getInvoiceByDocumentId(documentId), null),
        optionalRequest(completed, () => documentService.getDocumentExtractedFields(documentId), []),
        optionalRequest(completed, () => reconciliationService.getByDocument(documentId), []),
      ]);
      if (!isMounted) return;

      if (ocrResult.status === 'fulfilled' && ocrResult.value) setOcr(ocrResult.value);
      else if (completed) setSectionErrors((current) => [...current, 'Không thể tải kết quả OCR.']);
      if (classificationResult.status === 'rejected' && completed) setClassificationState('error');
      if (classificationResult.status === 'fulfilled') {
        if (classificationResult.value) {
          setClassification(classificationResult.value);
          setClassificationValue(classificationResult.value.accountingCategoryCode || classificationResult.value.category || '');
          setClassificationState('success');
        }
        else setClassificationState('empty');
      }
      else if (completed) setSectionErrors((current) => [...current, 'Không thể tải kết quả phân loại.']);
      if (invoiceResult.status === 'fulfilled' && invoiceResult.value) {
        setInvoice(invoiceResult.value);
        setInvoiceForm(toInvoiceForm(invoiceResult.value));
      }
      else if (shouldLoadInvoice) setSectionErrors((current) => [...current, 'Không thể tải dữ liệu hóa đơn.']);
      if (extractedFieldsResult.status === 'fulfilled') setExtractedFields(extractedFieldsResult.value);
      else if (completed) setSectionErrors((current) => [...current, 'Không thể tải các trường trích xuất bổ sung.']);

      if (reconciliationResult.status === 'fulfilled') setReconciliations(reconciliationResult.value);
      else if (completed) setSectionErrors((current) => [...current, 'Không thể tải dữ liệu đối soát.']);

      setIsLoading(false);
    };

    loadDocumentDetail().catch(() => {
      if (isMounted) {
        setError('Không thể tải thông tin chứng từ');
        setIsLoading(false);
      }
    });

    return () => { isMounted = false; };
  }, [documentId, reloadVersion]);

  const updateInvoiceForm = (field: keyof InvoiceFormData, value: string) => {
    setInvoiceForm((current) => ({ ...current, [field]: value }));
  };

  const runWorkflow = async (action: WorkflowAction) => {
    if (!document) return;
    if ((action === 'REJECT' || action === 'REQUEST_INFO') && !workflowNote.trim()) { setWorkflowError('Nhập lý do trước khi thực hiện.'); return; }
    setIsWorkflowPending(true); setWorkflowError(''); setWorkflowMessage('');
    try {
      await documentService.executeWorkflow(document.id, { action, note: workflowNote.trim() || undefined });
      const refreshed = await documentService.getDocumentById(document.id);
      setDocument(refreshed); setBusinessState(null); setBusinessState(await documentService.getBusinessState(document.id).catch(() => null)); setWorkflowNote(''); setWorkflowAction(null); setWorkflowMessage('Đã cập nhật workflow.'); setReloadVersion(value => value + 1);
    } catch { setWorkflowError('Không thể cập nhật workflow. Vui lòng thử lại.'); }
    finally { setIsWorkflowPending(false); }
  };

  const handleSave = async () => {
    if (!document || !isEditing) return;

    setIsSaving(true);
    setSaveMessage('');
    setSaveError('');

    try {
      if (invoice) {
        const updatedInvoice = await invoiceService.updateInvoice(invoice.id, toInvoiceUpdateRequest(document.id, invoiceForm, invoice));
        setInvoice(updatedInvoice);
        setInvoiceForm(toInvoiceForm(updatedInvoice));
      }

      if (classificationValue) {
        const updatedClassification = classification
          ? await classificationService.correctClassification(document.id, { category: classificationValue })
          : await classificationService.updateClassification(document.id, { category: classificationValue });
        setClassification(updatedClassification);
        setClassificationValue(updatedClassification.category || classificationValue);
      }

      if (documentTypeId && Number(documentTypeId) !== document.typeId) {
		const updatedDocument = await documentService.updateDocument(document.id, { typeId: Number(documentTypeId) });
		setDocument(updatedDocument);
		setDocumentTypeId(updatedDocument.typeId == null ? '' : String(updatedDocument.typeId));
	  }

      setBusinessState(null); setBusinessState(await documentService.getBusinessState(document.id).catch(() => null));
      setSaveMessage('Đã lưu thay đổi');
      setIsEditing(false);
    } catch {
      setSaveError('Không thể lưu thay đổi. Vui lòng thử lại.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleCorrectExtractedField = async (fieldId: number, fieldValue: string) => {
    await documentService.correctExtractedField(documentId, fieldId, fieldValue);
    try {
      setExtractedFields(await documentService.getDocumentExtractedFields(documentId));
    } catch {
      throw new Error('Đã lưu correction nhưng không thể tải lại dữ liệu. Vui lòng làm mới trang.');
    }
  };

  const retryCategories = async () => {
    if (!document || !hasCorrectionRole) return;
    setCategoryState('loading');
    try {
      const categories = await accountingCategoryService.getCategories(role === 'ADMIN' ? document.companyId : undefined);
      setAccountingCategories(categories);
      setCategoryState(listReadOutcome(200, categories.length));
    } catch { setCategoryState('error'); }
  };

  const retryClassification = async () => {
    if (!document || !hasCompletedPipeline(document.status)) return;
    setClassificationState('loading');
    try {
      const result = await classificationService.getClassificationOrNull(document.id);
      setClassification(result);
      setClassificationValue(result?.accountingCategoryCode || result?.category || '');
      setClassificationState(result ? 'success' : 'empty');
    } catch { setClassificationState('error'); }
  };

  const runReconciliation = async () => {
    if (!document || !hasCorrectionRole) return;
    setIsReconciling(true); setReconciliationError('');
    try { setReconciliations(await reconciliationService.run(document.id)); setBusinessState(null); setBusinessState(await documentService.getBusinessState(document.id)); }
    catch { setReconciliationError('Không thể chạy đối soát. Dữ liệu hóa đơn hiện tại vẫn được giữ nguyên.'); }
    finally { setIsReconciling(false); }
  };

  const hasUnsavedChanges = useMemo(() => {
    if (!isEditing) return false;
    const originalForm = invoice ? toInvoiceForm(invoice) : emptyInvoiceForm;
    const originalCategory = classification?.accountingCategoryCode || classification?.category || '';
    return JSON.stringify(invoiceForm) !== JSON.stringify(originalForm) || classificationValue !== originalCategory
	  || documentTypeId !== (document?.typeId == null ? '' : String(document.typeId));
  }, [classification, classificationValue, document, documentTypeId, invoice, invoiceForm, isEditing]);

  useEffect(() => {
    if (!hasUnsavedChanges) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [hasUnsavedChanges]);

  if (isLoading) {
    return <LoadingState label="Đang tải thông tin chứng từ..." />;
  }

  if (error || !document) {
    return <ErrorState message={error || 'Không tìm thấy chứng từ'} />;
  }

  const documentView = mapDocument(document);
  const classificationConfidence = formatConfidence(assessment?.classificationConfidence.value);
  const ocrConfidence = formatConfidence(assessment?.ocrConfidence.value);
  const canCorrect = hasCorrectionRole && document.reviewStatus !== 'APPROVED'
    && (document.status === 'PROCESSED' || document.status === 'NEED_REVIEW');
  const canReview = hasCorrectionRole && document.reviewStatus === 'PENDING' && (document.status === 'PROCESSED' || document.status === 'NEED_REVIEW');
  const canSubmit = isEmployeeView && document.status === 'UPLOADED' && document.reviewStatus === 'PENDING';
  const canResubmit = isEmployeeView && document.status === 'NEED_REVIEW' && (document.reviewStatus === 'REJECTED' || document.reviewStatus === 'CORRECTED');
  const dynamicFields = distinctMeaningfulFields(extractedFields);
  const displaySectionErrors = sectionErrors.filter(message => {
    const normalized = message.toLocaleLowerCase('vi');
    return !normalized.includes('nhóm nghiệp vụ') && !normalized.includes('phân loại');
  });
  return (
    <div className="flex h-auto min-h-[calc(100vh/var(--ui-zoom)_-_8rem)] flex-col gap-3 xl:h-[calc(100vh/var(--ui-zoom)_-_8rem)] xl:overflow-hidden">
      <div className="sticky top-0 z-30 flex flex-col gap-3 rounded-card border border-hairline bg-canvas/95 p-2 backdrop-blur sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-4">
          <button onClick={() => { if (!hasUnsavedChanges || window.confirm('Bạn có thay đổi chưa lưu. Rời trang và bỏ các thay đổi?')) navigate(`${basePath}/documents`); }} className="si-btn-ghost rounded-full p-2">
            <ArrowLeft size={20} />
          </button>
          <div className="min-w-0">
            <div className="flex items-center gap-3">
              <h1 className="max-w-[calc(55vw/var(--ui-zoom))] truncate font-sans text-[32px] leading-7 font-bold tracking-tight text-ink" title={`${document.id} - ${document.originalFileName}`}>{document.id} - {document.originalFileName}</h1>
              <StatusBadge status={documentView.status} />
            </div>
            <p className="mt-1 text-[18px] text-ink-soft">{isEmployeeView ? 'Thông tin chứng từ của bạn' : 'Đã tải lên vào'} {isEmployeeView ? '' : documentView.date}</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <button onClick={async () => { const url = await documentService.downloadDocument(document.id); const link = window.document.createElement('a'); link.href = url; link.download = document.originalFileName; link.click(); URL.revokeObjectURL(url); }} className="si-btn-secondary">
            <Download size={22} />
            <span>Tải file gốc</span>
          </button>
          {canCorrect && !isEditing && <button onClick={() => { setIsEditing(true); setSaveMessage(''); setSaveError(''); }} className="si-btn-primary">
            <Pencil size={22} />
            <span>Chỉnh sửa thủ công</span>
          </button>}
          {isEditing && <button onClick={() => { setInvoiceForm(invoice ? toInvoiceForm(invoice) : emptyInvoiceForm); setClassificationValue(classification?.category || ''); setDocumentTypeId(document.typeId == null ? '' : String(document.typeId)); setIsEditing(false); setSaveError(''); }} disabled={isSaving} className="si-btn-secondary">
            <X size={22} /><span>Hủy</span>
          </button>}
          {isEditing && <button onClick={handleSave} disabled={isSaving} className="si-btn-primary">
            <Save size={22} />
            <span>{isSaving ? 'Đang lưu...' : 'Lưu thay đổi'}</span>
          </button>}
        </div>
      </div>

      {saveMessage && <div className="rounded-control bg-parsed-surface border border-parsed/25 px-4 py-3 text-sm text-parsed">{saveMessage}</div>}
      {saveError && <div className="rounded-control bg-failed-surface border border-failed/25 px-4 py-3 text-sm text-failed">{saveError}</div>}
      {workflowMessage && <div className="rounded-control bg-parsed-surface border border-parsed/25 px-4 py-3 text-sm text-parsed">{workflowMessage}</div>}
      {workflowError && <div className="rounded-control bg-failed-surface border border-failed/25 px-4 py-3 text-sm text-failed">{workflowError}</div>}
      {displaySectionErrors.length > 0 && <div className="rounded-control border border-review/25 bg-review-surface px-4 py-3 text-sm text-review">{displaySectionErrors.join(' ')}</div>}

      {(canReview || canSubmit || canResubmit) && <div className="si-card p-4"><div className="flex flex-wrap items-center gap-2"><span className="si-label mr-2">Workflow</span>{canSubmit && <button disabled={isWorkflowPending} onClick={() => void runWorkflow('SUBMIT')} className="si-btn-primary">Gửi xử lý</button>}{canResubmit && <button disabled={isWorkflowPending} onClick={() => void runWorkflow('RESUBMIT')} className="si-btn-primary">Gửi lại</button>}{canReview && <><button disabled={isWorkflowPending} onClick={() => void runWorkflow('START_REVIEW')} hidden={businessState?.review === 'IN_REVIEW'} className="si-btn-secondary border-brand-200 text-brand-700">Bắt đầu review</button><button disabled={isWorkflowPending} onClick={() => void runWorkflow('APPROVE')} className="si-btn-secondary border-parsed/30 text-parsed">Duyệt</button><button disabled={isWorkflowPending} onClick={() => setWorkflowAction('REJECT')} className="si-btn-secondary border-failed/30 text-failed">Từ chối</button><button disabled={isWorkflowPending} onClick={() => setWorkflowAction('REQUEST_INFO')} className="si-btn-secondary border-review/30 text-review">Yêu cầu bổ sung</button></>}</div>{workflowAction && <div className="mt-3 flex flex-wrap gap-2"><input value={workflowNote} onChange={(event) => setWorkflowNote(event.target.value)} placeholder="Nhập lý do" className="si-input min-w-64 flex-1" /><button disabled={isWorkflowPending} onClick={() => void runWorkflow(workflowAction)} className="si-btn-primary">{isWorkflowPending ? 'Đang gửi...' : 'Xác nhận'}</button><button disabled={isWorkflowPending} onClick={() => { setWorkflowAction(null); setWorkflowNote(''); }} className="rounded-control px-3 py-2 text-sm text-ink-soft">Hủy</button></div>}</div>}

      {hasUnsavedChanges && <div className="rounded-control border border-review/30 bg-review-surface px-3 py-2 text-sm text-review">Có thay đổi chưa lưu. Dữ liệu vẫn được giữ khi đổi tab.</div>}

      <div className="grid min-h-0 flex-1 gap-4 xl:grid-cols-[minmax(0,11fr)_minmax(380px,9fr)] xl:overflow-hidden">
        <div className="h-[calc(68vh/var(--ui-zoom))] min-h-[520px] xl:h-full xl:min-h-0">
          <OcrDocumentPreview documentId={document.id} fileType={document.fileType} ocr={ocr}
            fields={extractedFields} documentType={document.documentType} canCorrectFields={canCorrect}
            onCorrectField={handleCorrectExtractedField} />
        </div>

        <div className="si-card flex min-h-[560px] flex-col overflow-hidden xl:min-h-0">
          <div className="sticky top-0 z-20 grid grid-cols-4 border-b border-hairline bg-surface/95 px-2 pt-2 backdrop-blur" role="tablist" aria-label="Thông tin chứng từ">
            {([['overview', 'Tổng quan'], ['items', 'Hàng hóa'], ['reconciliation', 'Đối soát'], ['details', 'Chi tiết'], ['storage', 'File & lưu trữ'], ['versions', 'Phiên bản'], ['history', 'Lịch sử xử lý']] as const).map(([key, label]) => <button key={key} type="button" role="tab" aria-selected={activeTab === key} onClick={() => setActiveTab(key)} className={clsx('relative border-b-2 px-2 py-3 text-[18px] font-semibold tracking-tight transition-colors', activeTab === key ? 'border-brand-600 text-brand-700' : 'border-transparent text-ink-muted hover:border-hairline-strong hover:text-ink')}>{label}</button>)}
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto p-4">
            {activeTab === 'overview' && <div className="space-y-4">
              <BusinessStateCard state={businessState} />
              {hasCorrectionRole&&<DocumentPaymentPanel documentId={document.id} companyId={document.companyId} canRun={hasCorrectionRole}/>}
              {hasCorrectionRole&&document.reviewStatus==='APPROVED'&&<div className="flex gap-2">{(['INCOME','EXPENSE'] as const).map(type=><button key={type} type="button" className={type==='INCOME'?'si-btn-primary':'si-btn-secondary'} onClick={()=>navigate(`/${role==='ADMIN'?'admin':'accountant'}/financial-transactions?documentId=${document.id}&companyId=${document.companyId??''}&type=${type}`)}>{type==='INCOME'?'Tạo phiếu Thu':'Tạo phiếu Chi'}</button>)}</div>}
              <ProcessingHistoryCard documentStatus={document.status} reviewStatus={document.reviewStatus} history={processingHistory} error={historyError} retrying={retrying} onRetry={() => {
                if (retrying) return;
                setRetrying(true); setHistoryError('');
                void documentService.retryProcessing(documentId).catch(() => setHistoryError('Xử lý chưa hoàn tất. Kiểm tra lịch sử lỗi hoặc quyền truy cập.'))
                  .finally(() => { setRetrying(false); setReloadVersion(value => value + 1); });
              }} />
              <section className="si-card p-4">
                <h3 className="si-section-title mb-3">Đánh giá OCR/AI</h3>
                <div className="grid grid-cols-1 gap-3 text-sm sm:grid-cols-3">
                  <div><p className="text-xs text-ink-muted">Độ tin cậy OCR</p><p>{ocrConfidence}</p><p className="text-xs text-ink-muted">{assessment?.ocrConfidence.source}</p></div>
                  <div><p className="text-xs text-ink-muted">Độ tin cậy trích xuất</p><p>{formatConfidence(assessment?.extractionConfidence.value)}</p></div>
                  <div><p className="text-xs text-ink-muted">Độ tin cậy phân loại</p><p>{classificationConfidence}</p><p className="text-xs text-ink-muted">{assessment?.classificationConfidence.source}</p></div>
                </div>
                <p className="mt-3 text-sm">Validation: {assessment?.validationStatus === 'VALID' ? 'Hợp lệ' : assessment?.validationStatus === 'NEED_REVIEW' ? 'Cần kiểm tra' : assessment?.validationStatus === 'FAILED' ? 'Xử lý thất bại' : 'Chưa xác định'}</p>
                {assessmentError && <p className="mt-2 text-sm text-review">{assessmentError}</p>}
                {(assessment?.reviewReasons.length ?? 0) > 0 && <div className="mt-3 text-sm"><p className="font-semibold">Lý do cần kiểm tra</p><ul className="mt-2 list-disc space-y-1 pl-5">{assessment?.reviewReasons.map((reason, index) => <li key={index}>{reason}</li>)}</ul></div>}
                <div className="mt-3 space-y-2">{assessment?.warnings.map((warning, index) => <div key={index} className={clsx('rounded-control border p-3 text-sm', warning.severity === 'CRITICAL' ? 'border-review/25 bg-review-surface text-review' : 'border-hairline')}><p className="font-medium">{warning.severity} · {warning.code}</p><p className="mt-1 break-words">{warning.message}</p>{warning.affectedField && <p className="mt-1 text-xs">Trường liên quan: {humanizeFieldName(warning.affectedField)}</p>}</div>)}</div>
              </section>
              <section className="si-card border-tax-vat/25 bg-tax-vat-surface p-4"><div className="flex items-start justify-between gap-4"><div><p className="si-label text-tax-vat">Loại chứng từ</p><p className="mt-1 font-sans text-[20px] font-bold tracking-tight text-ink">{document.documentType ? documentTypeLabel(document.documentType) : 'Chưa xác định'}</p><p className="mt-1 text-xs text-ink-muted">{classification?.aiGenerated === false ? 'Đã được kế toán chỉnh sửa' : document.documentType ? 'AI đề xuất từ OCR và Qwen3-VL' : 'AI chưa xác định được loại chứng từ'}</p></div><div className="text-right"><p className="text-xl font-bold text-tax-vat">{classificationConfidence}</p><p className="text-xs text-ink-muted">Độ tin cậy loại chứng từ</p></div></div>{isEditing && <label className="mt-4 block text-xs font-medium text-ink-soft">Loại chứng từ<select value={documentTypeId} onChange={event => setDocumentTypeId(event.target.value)} className="mt-1 w-full rounded-control border border-hairline-strong bg-surface px-3 py-2 text-sm"><option value="" disabled>Chưa xác định</option>{documentTypes.map(type => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label>}</section>
              <section className="rounded-card border border-brand-200 bg-brand-50/60 p-4">
                <div><p className="si-label text-brand-700">Nhóm nghiệp vụ kế toán</p><p className="mt-1 font-sans text-[20px] font-bold tracking-tight text-ink">{classification?.accountingCategoryName || classification?.category || 'Chưa phân loại'}</p><p className="mt-1 text-xs text-ink-muted">{classification?.accountingAccount ? `Tài khoản gợi ý · ${classification.accountingAccount}` : 'Độc lập với loại chứng từ'}</p></div>
                {isEditing && <label className="mt-4 block text-xs font-medium text-ink-soft">Hạng mục kế toán<select value={classificationValue} onChange={event => setClassificationValue(event.target.value)} className="mt-1 w-full rounded-control border border-hairline-strong bg-surface px-3 py-2 text-sm"><option value="">Chưa phân loại</option>{accountingCategories.map(category => <option key={category.id} value={category.categoryCode}>{category.categoryName} · {categoryTypeLabel(category.categoryType)}</option>)}</select></label>}
                {classificationState === 'empty' && <p className="mt-3 text-sm text-ink-soft">Chứng từ chưa được phân loại.</p>}
                {classificationState === 'loading' && <p className="mt-3 text-sm text-ink-muted">Đang tải kết quả phân loại...</p>}
                {classificationState === 'error' && <div className="mt-3 flex items-center justify-between gap-2 rounded-control bg-review-surface p-2 text-sm text-review"><span>Không thể tải kết quả phân loại.</span><button type="button" onClick={() => void retryClassification()} className="si-btn-secondary text-xs">Thử lại</button></div>}
                {classificationState === 'success' && classification?.reason && <p className="mt-3 text-sm text-ink-soft">{classification.reason}</p>}
                {isEditing && categoryState === 'loading' && <p className="mt-2 text-xs text-ink-muted">Đang tải danh mục...</p>}
                {isEditing && categoryState === 'empty' && <p className="mt-2 text-xs text-ink-soft">Công ty chưa có nhóm nghiệp vụ đang hoạt động.</p>}
                {isEditing && categoryState === 'error' && <div className="mt-2 flex items-center justify-between gap-2 text-xs text-review"><span>Không thể tải danh mục công ty.</span><button type="button" onClick={() => void retryCategories()} className="si-btn-secondary text-xs">Thử lại</button></div>}
              </section>
              <section className="grid grid-cols-3 gap-2"><div className="rounded-card bg-canvas p-3"><p className="text-xs text-ink-muted">Trước thuế</p><p className="mt-1 font-semibold">{money(invoice?.subtotal)}</p></div><div className="rounded-card bg-canvas p-3"><p className="text-xs text-ink-muted">Thuế GTGT</p><p className="mt-1 font-semibold">{money(invoice?.vatAmount)}</p></div><div className="rounded-card bg-brand-50 p-3"><p className="text-xs text-brand-600">Tổng tiền</p><p className="mt-1 font-bold text-brand-800">{money(invoice?.totalAmount)}</p></div></section>
              <section className="si-card p-4"><div className="grid grid-cols-2 gap-3 text-sm"><div><p className="text-xs text-ink-muted">Số hóa đơn</p><p className="font-medium">{valueOrDash(invoice?.invoiceNumber)}</p></div><div><p className="text-xs text-ink-muted">Ngày hóa đơn</p><p className="font-medium">{valueOrDash(invoice?.invoiceDate)}</p></div><div><p className="text-xs text-ink-muted">Người bán</p><p className="font-medium">{valueOrDash(invoice?.sellerName)}</p></div><div><p className="text-xs text-ink-muted">OCR confidence</p><p className="font-medium">{ocrConfidence}</p></div></div></section>
              {(displaySectionErrors.length > 0 || classification?.status === 'NEED_REVIEW') && <section className="si-card border-review/25 bg-review-surface p-4 text-sm text-review"><p className="font-semibold">Cần xử lý</p>{displaySectionErrors.map((message, index) => <p key={`${message}-${index}`} className="mt-2">{message}</p>)}</section>}
            </div>}

            {activeTab === 'items' && <div>{invoice?.items.length ? (
              <div className="border-hairline overflow-hidden rounded-card border">
                <div className="overflow-x-auto">
                  <table className="si-table min-w-[700px]">
                    <thead>
                      <tr>
                        <th>Hàng hóa / dịch vụ</th>
                        <th className="text-right">Số lượng</th>
                        <th className="text-right">Đơn giá</th>
                        <th className="text-right">Thuế suất</th>
                        <th className="text-right">Thành tiền</th>
                      </tr>
                    </thead>
                    <tbody>
                      {invoice.items.map((item, index) => (
                        <tr key={item.id ?? index}>
                          <td><p className="font-medium text-ink">{valueOrDash(item.productName)}</p><p className="text-xs text-ink-muted">{valueOrDash(item.unit)}</p></td>
                          <td className="si-num text-right text-[18px] text-ink-soft">{valueOrDash(item.quantity)}</td>
                          <td className="si-num text-right text-[18px] text-ink-soft">{money(item.unitPrice)}</td>
                          <td className="si-num text-right text-[18px] text-ink-soft">{rate(item.taxRate)}</td>
                          <td className="si-num text-right text-[18px] font-semibold text-ink">{money(item.amount)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            ) : <EmptyState title="Chưa có dữ liệu hàng hóa" description="Backend chưa trả về danh sách hàng hóa cho chứng từ này." />}</div>}

            {activeTab === 'reconciliation' && <div className="space-y-3"><div className="flex items-center justify-between gap-3"><p className="text-[17px] text-ink-muted">Kết quả đối soát được hệ thống tính toán và lưu lại.</p>{hasCorrectionRole && <button type="button" disabled={isReconciling || document.reviewStatus === 'APPROVED' || document.status === 'COMPLETED'} onClick={() => void runReconciliation()} className="si-btn-primary">{isReconciling ? 'Đang đối soát...' : reconciliations.length ? 'Chạy lại' : 'Chạy đối soát'}</button>}</div>{reconciliationError && <p className="rounded-control border-failed/25 bg-failed-surface border p-3 text-sm text-failed">{reconciliationError}</p>}{hasInsufficientReconciliationData(reconciliations.map(item => item.status)) && <p className="rounded-control border-review/25 bg-review-surface border p-3 text-sm text-review">Chưa đủ dữ liệu hàng hóa để đối soát. Giá trị chưa xác định được hiển thị “—”; xem chi tiết từng phép kiểm tra để bổ sung dữ liệu theo quy trình hiện có.</p>}{reconciliations.length ? reconciliations.map(item => <ReconciliationResultCard key={item.id ?? item.type} item={item} />) : <EmptyState title="Chưa có kết quả đối soát" description="Giao diện không tự suy diễn trạng thái khớp. Hãy chạy đối soát để hệ thống tính toán và lưu kết quả." />}</div>}

            {(['storage','versions','history'] as string[]).includes(activeTab) && <DocumentArchivePanel key={document.id + '-' + activeTab} documentId={document.id} mode={activeTab as 'storage'|'versions'|'history'} />}
            {activeTab === 'history' && <ProcessingHistoryCard history={processingHistory} error={historyError} retrying={retrying} documentStatus={document.status} reviewStatus={document.reviewStatus} onRetry={() => { setRetrying(true); documentService.retryProcessing(document.id).catch(() => setHistoryError('Xử lý chưa hoàn tất; kiểm tra lỗi hoặc quyền truy cập.')).finally(() => { setRetrying(false); setReloadVersion(n => n + 1); }); }} />}
            {activeTab === 'details' && <div className="space-y-4">{invoice ? isEditing ? <VatInvoicePanel invoice={invoice} form={invoiceForm} editable onChange={updateInvoiceForm} rawText={ocr?.rawText} /> : <>
              <section className="si-card p-4"><h3 className="si-section-title mb-3">Thông tin hóa đơn</h3><div className="grid grid-cols-2 gap-3 text-sm"><div><p className="text-xs text-ink-muted">Ký hiệu</p><p>{valueOrDash(invoice.invoiceSeries)}</p></div><div><p className="text-xs text-ink-muted">Số hóa đơn</p><p>{valueOrDash(invoice.invoiceNumber)}</p></div><div><p className="text-xs text-ink-muted">Ngày lập</p><p>{valueOrDash(invoice.invoiceDate)}</p></div><div><p className="text-xs text-ink-muted">Ngày ký</p><p>{valueOrDash(invoice.signDate)}</p></div></div></section>
              <section className="si-card p-4"><h3 className="si-section-title mb-3">Người bán</h3><p className="font-medium">{valueOrDash(invoice.sellerName)}</p><p className="mt-1 text-sm">MST: {valueOrDash(invoice.sellerTaxCode)} · ĐT: {valueOrDash(invoice.sellerPhone)}</p><p className="mt-1 text-sm text-ink-soft">{valueOrDash(invoice.sellerAddress)}</p></section>
              <section className="si-card p-4"><h3 className="si-section-title mb-3">Người mua</h3><p className="font-medium">{valueOrDash(invoice.buyerName)}</p><p className="mt-1 text-sm">MST: {valueOrDash(invoice.buyerTaxCode)}</p><p className="mt-1 text-sm text-ink-soft">{valueOrDash(invoice.buyerAddress)}</p></section>
              <section className="si-card p-4"><h3 className="si-section-title mb-3">Thanh toán và thuế</h3><div className="grid grid-cols-2 gap-3 text-sm"><div><p className="text-xs text-ink-muted">Phương thức</p><p>{valueOrDash(invoice.paymentMethod)}</p></div><div><p className="text-xs text-ink-muted">Mã cơ quan thuế</p><p>{valueOrDash(invoice.taxAuthorityCode)}</p></div><div className="col-span-2"><p className="text-xs text-ink-muted">Số tiền bằng chữ</p><p>{valueOrDash(invoice.amountInWords)}</p></div></div></section>
            </> : <EmptyState title="Chưa có dữ liệu hóa đơn" description="Backend chưa trả về thông tin hóa đơn cho chứng từ này." />}
              <section className="si-card p-4"><h3 className="si-section-title mb-3">Metadata</h3><div className="grid grid-cols-2 gap-3 text-sm"><div><p className="text-xs text-ink-muted">OCR engine</p><p>{valueOrDash(ocr?.ocrEngine)}</p></div><div><p className="text-xs text-ink-muted">Nguồn OCR</p><p>{valueOrDash(ocr?.sourceType)}</p></div><div><p className="text-xs text-ink-muted">Ngôn ngữ</p><p>{valueOrDash(ocr?.language)}</p></div><div><p className="text-xs text-ink-muted">Cập nhật</p><p>{formatDateTime(document.updatedAt)}</p></div></div></section>
              {dynamicFields.length > 0 && <section className="si-card p-4"><h3 className="si-section-title mb-3">Trường bổ sung</h3><div className="space-y-3">{dynamicFields.map(field => <div key={field.id}><p className="text-xs text-ink-muted">{humanizeFieldName(field.fieldName)} · {formatConfidence(field.confidence)}</p><p className="break-words text-sm">{field.fieldValue}</p></div>)}</div></section>}
              <details className="si-card p-4"><summary className="cursor-pointer font-medium text-ink-soft">OCR gốc</summary><pre className="mt-3 max-h-72 overflow-auto whitespace-pre-wrap break-words text-xs text-ink-soft">{ocr?.rawText || 'Chưa có nội dung OCR'}</pre></details>
            </div>}
          </div>
          {Boolean(false) && document && classification && invoice && ocr && <>
          <div className="si-card overflow-hidden border-brand-200 bg-gradient-to-br from-brand-50 to-surface p-5">
            <div className="flex items-start justify-between mb-4">
              <div>
                <h3 className="text-sm font-semibold text-brand-800 uppercase tracking-wider">AI Phân Loại {isEmployeeView && '(chỉ xem)'}</h3>
                <p className="text-sm text-ink-soft mt-1">Kết quả từ backend classification</p>
              </div>
              <div className="text-right">
                <div className={clsx('text-xl font-bold', classificationConfidence === '—' ? 'text-ink-muted' : 'text-parsed')}>
                  {classificationConfidence}
                </div>
                <div className="text-xs text-ink-muted">Độ tin cậy phân loại</div>
              </div>
            </div>
            <label className="block text-xs font-medium text-ink-muted mb-1">Loại nghiệp vụ / Chi phí</label>
            <select
              value={classificationValue}
              onChange={(event) => setClassificationValue(event.target.value)}
              disabled={!isEditing}
              className="w-full bg-surface border border-hairline-strong rounded-control px-3 py-2 text-sm font-medium text-ink focus:border-brand-600 focus:ring-2 focus:ring-brand-600/20 outline-none"
            >
              <option value="">Chưa có dữ liệu phân loại</option>
              {accountingCategories.map((category) => <option key={category.id} value={category.categoryCode}>{category.categoryName}</option>)}
            </select>
            {classification?.accountingAccount && <p className="mt-2 text-xs text-ink-soft">Tài khoản AI đề xuất: <span className="font-medium">{classification.accountingAccount}</span></p>}
            {!classification && <p className="text-xs text-ink-muted mt-2">Backend chưa có bản ghi classification cho chứng từ này.</p>}
            {classification && !classification.aiGenerated && <p className="mt-2 text-xs font-medium text-review">Đã chỉnh sửa thủ công</p>}
          </div>

          <div className="bg-surface rounded-panel border border-hairline shadow-elev1 p-5">
            <h3 className="text-lg font-semibold text-ink">Thông tin nhận diện</h3>
            <div className="mt-3 grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
              <span className="block text-xs font-medium text-ink-muted">Loại chứng từ</span>
              <span className="break-words text-ink">{documentTypeLabel(document.documentType)}</span>
              <div><span className="block text-xs font-medium text-ink-muted">Độ tin cậy OCR</span><span>{ocrConfidence}</span></div>
              {document.companyRole?.role && <div><span className="block text-xs font-medium text-ink-muted">Vai trò công ty</span><span>{companyRoleLabels[document.companyRole.role] || document.companyRole.role}</span>{formatConfidence(document.companyRole.confidence) && <span className="ml-2 text-xs text-ink-muted">{formatConfidence(document.companyRole.confidence)}</span>}{document.companyRole.reason && <p className="mt-1 break-words text-xs text-ink-muted">{document.companyRole.reason}</p>}</div>}
              {document.documentDirection && <div><span className="block text-xs font-medium text-ink-muted">Hướng chứng từ</span><span>{directionLabels[document.documentDirection] || document.documentDirection}</span></div>}
              {document.transactionAssessment?.type && <div><span className="block text-xs font-medium text-ink-muted">Đề xuất nghiệp vụ AI</span><span>{assessmentLabels[document.transactionAssessment.type] || document.transactionAssessment.type}</span>{formatConfidence(document.transactionAssessment.confidence) && <span className="ml-2 text-xs text-ink-muted">{formatConfidence(document.transactionAssessment.confidence)}</span>}{document.transactionAssessment.reason && <p className="mt-1 break-words text-xs text-ink-muted">{document.transactionAssessment.reason}</p>}</div>}
            </div>
          </div>

          <div className="bg-surface rounded-panel border border-hairline shadow-elev1 p-5 flex-1 xl:row-span-2">
            <div className="flex items-center justify-between mb-6">
              <h3 className="text-lg font-semibold text-ink">{document.documentType === 'VAT_INVOICE' ? 'Thông tin hóa đơn giá trị gia tăng' : 'Dữ liệu hóa đơn'}</h3>
              <span className="si-chip bg-subtle text-ink-soft">API backend</span>
            </div>
            {!isInvoiceDocument(document.documentType) ? (
              <p className="text-sm text-ink-muted">Không áp dụng cho loại chứng từ này.</p>
            ) : invoice ? (
              <VatInvoicePanel invoice={invoice} form={invoiceForm} editable={isEditing} onChange={updateInvoiceForm} rawText={ocr?.rawText} />
            ) : (
              <p className="text-sm text-ink-muted">Backend chưa có invoice cho chứng từ này.</p>
            )}
            {!invoice && ocr && <details className="mt-6 rounded-card border border-hairline p-4"><summary className="cursor-pointer font-medium text-ink-soft">Văn bản OCR gốc</summary><pre className="mt-3 max-h-64 overflow-auto whitespace-pre-wrap text-xs text-ink-soft">{ocr.rawText || 'Chưa có nội dung'}</pre></details>}
          </div>

          {dynamicFields.length > 0 && <div className="bg-surface rounded-panel border border-hairline shadow-elev1 p-5">
            <h3 className="text-lg font-semibold text-ink">Trường trích xuất bổ sung</h3>
            <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
              {dynamicFields.map((field) => {
                const confidenceValue = formatConfidence(field.confidence);
                return <div key={field.id} className="min-w-0 rounded-control border border-hairline p-3"><div className="text-xs font-medium text-ink-muted">{humanizeFieldName(field.fieldName)}</div><div className="mt-1 whitespace-pre-wrap break-words text-sm text-ink">{field.fieldValue}</div><div className="mt-2 text-xs text-ink-muted">Độ tin cậy: {confidenceValue}</div></div>;
              })}
            </div>
          </div>}
          </>}
        </div>
      </div>
    </div>
  );
};

export default AccountantDocumentDetail;

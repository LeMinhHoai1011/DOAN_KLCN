import ProcessingHistoryCard from '../../components/ProcessingHistoryCard';
import type { PipelineHistory } from '../../services/documentService';
import { assessmentMessage, reviewMessages } from '../../utils/assessmentPresentation';
import { fieldLabel } from '../../components/ocr-preview/ocrPreviewUtils';
import DocumentArchivePanel from '../../components/DocumentArchivePanel';
import DocumentPaymentPanel from '../../components/DocumentPaymentPanel';
import { accountingAccountError, categoryTypeLabel } from '../../utils/accountingCategoryPresentation';
import BusinessStateCard from "../../components/BusinessStateCard";


import type { DocumentAssessment } from "../../services/documentService";
import { useEffect, useMemo, useRef, useState } from 'react';
import { createActiveReviewTimer, isReviewDecision } from '../../utils/activeReviewTimer';
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
import { getEffectiveRole, getErrorMessage } from '../../services/authService';
import EmptyState from '../../components/ui/EmptyState';
import ErrorState from '../../components/ui/ErrorState';
import LoadingState from '../../components/ui/LoadingState';
import ExtractedFieldPanel from '../../components/ocr-preview/ExtractedFieldPanel';
import OcrDocumentPreview from '../../components/OcrDocumentPreview';
import { fieldVerificationService, type FieldVerificationReport, type FieldVerificationView } from '../../services/fieldVerificationService';
import { aiInputModeLabel, indexVerifications, toPreviewFocus, verificationStatusLabel, withoutItemVerifications } from '../../utils/fieldVerificationPresentation';
import InvoiceItemsPanel from '../../components/InvoiceItemsPanel';
import VatInvoicePanel, { documentTypeLabel, emptyInvoiceForm, toInvoiceForm, toInvoiceUpdateRequest, type InvoiceFormData } from '../../components/VatInvoicePanel';
import { formatConfidence } from '../../utils/confidence';
import { listReadOutcome } from '../../services/readState';
import { approvalBlockReason, hasInsufficientReconciliationData, withoutErrorCode } from '../../utils/reconciliationPresentation';
import ReconciliationResultCard from '../../components/ReconciliationResultCard';
import ReconciliationDecision from '../../components/ReconciliationDecision';

type DetailTab = 'overview' | 'items' | 'reconciliation' | 'storage';
type RemoteState = 'idle' | 'loading' | 'empty' | 'success' | 'error';

const money = (value: number | null | undefined) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(value)} đ`;
const valueOrDash = (value: string | number | null | undefined) => value == null || value === '' ? '—' : String(value);

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



  const [reloadVersion, setReloadVersion] = useState(0);
  const [processingHistory, setProcessingHistory] = useState<PipelineHistory | null>(null);
  const [historyError, setHistoryError] = useState('');
  const [retrying, setRetrying] = useState(false);
  const [assessment, setAssessment] = useState<DocumentAssessment | null>(null);
  const [assessmentError, setAssessmentError] = useState('');
  const [invoice, setInvoice] = useState<InvoiceResponse | null>(null);
  const [reconciliations, setReconciliations] = useState<Reconciliation[]>([]);
  const [extractedFields, setExtractedFields] = useState<ExtractedFieldResponse[]>([]);
  const [invoiceForm, setInvoiceForm] = useState<InvoiceFormData>(emptyInvoiceForm);
  const [classificationValue, setClassificationValue] = useState('');
  const [accountingAccount, setAccountingAccount] = useState('');
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
  const [itemsDirty, setItemsDirty] = useState(false);
  const [error, setError] = useState('');
  const [saveMessage, setSaveMessage] = useState('');
  const [saveError, setSaveError] = useState('');
  const [sectionErrors, setSectionErrors] = useState<string[]>([]);
  const [workflowNote, setWorkflowNote] = useState('');
  const [workflowAction, setWorkflowAction] = useState<WorkflowAction | null>(null);
  const [isWorkflowPending, setIsWorkflowPending] = useState(false);
  const [workflowError, setWorkflowError] = useState('');
  const [workflowMessage, setWorkflowMessage] = useState('');
  const [focusedField, setFocusedField] = useState<ExtractedFieldResponse | null>(null);
  const [verification, setVerification] = useState<FieldVerificationReport | null>(null);
  const [verificationError, setVerificationError] = useState('');
  const [isRechecking, setIsRechecking] = useState(false);
  const [archiveTab, setArchiveTab] = useState<'storage' | 'versions' | 'history'>('storage');
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
      setIsLoading(true); setError(''); setSectionErrors([]);
      setOcr(null); setClassification(null); setInvoice(null); setExtractedFields([]);
      setReconciliations([]); setFocusedField(null); setClassificationValue(''); setAccountingAccount('');
      setInvoiceForm(emptyInvoiceForm);
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
          setAccountingAccount(classificationResult.value.accountingAccount || '');
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

      setVerification(null); setVerificationError('');
      if (completed) {
        try { const report = await fieldVerificationService.get(documentId); if (isMounted) setVerification(report); }
        catch { if (isMounted) setVerificationError('Không thể tải kết quả kiểm chứng theo trường.'); }
      }

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

  // Measured visible review time, sent with the reviewer's decision for the AI-vs-manual effort report.
  const reviewTimer = useRef(createActiveReviewTimer(Date.now(), globalThis.document?.visibilityState !== 'hidden'));
  useEffect(() => {
    reviewTimer.current.reset(Date.now(), globalThis.document.visibilityState !== 'hidden');
    const onVisibility = () => reviewTimer.current.setVisible(globalThis.document.visibilityState !== 'hidden', Date.now());
    globalThis.document.addEventListener('visibilitychange', onVisibility);
    return () => globalThis.document.removeEventListener('visibilitychange', onVisibility);
  }, [documentId]);

  const runWorkflow = async (action: WorkflowAction) => {
    if (!document) return;
    if ((action === 'REJECT' || action === 'REQUEST_INFO') && !workflowNote.trim()) { setWorkflowError('Nhập lý do trước khi thực hiện.'); return; }
    setIsWorkflowPending(true); setWorkflowError(''); setWorkflowMessage('');
    try {
      const activeReviewMs = isReviewDecision(action) ? reviewTimer.current.elapsed(Date.now()) : undefined;
      await documentService.executeWorkflow(document.id, { action, note: workflowNote.trim() || undefined, activeReviewMs });
      if (activeReviewMs !== undefined) reviewTimer.current.reset(Date.now(), globalThis.document.visibilityState !== 'hidden');
      const refreshed = await documentService.getDocumentById(document.id);
      setDocument(refreshed); setBusinessState(null); setBusinessState(await documentService.getBusinessState(document.id).catch(() => null)); setWorkflowNote(''); setWorkflowAction(null); setWorkflowMessage('Đã cập nhật workflow.'); setReloadVersion(value => value + 1);
    } catch (failure) { setWorkflowError(withoutErrorCode(getErrorMessage(failure, 'Không thể cập nhật workflow. Vui lòng thử lại.'))); }
    finally { setIsWorkflowPending(false); }
  };

  /** Re-checks current values after a manual edit; never changes any value. */
  const recheckFields = async () => {
    setIsRechecking(true); setVerificationError('');
    try { setVerification(await fieldVerificationService.recompute(documentId)); }
    catch { setVerificationError('Không thể kiểm chứng lại. Kết quả hiển thị có thể đã cũ.'); }
    finally { setIsRechecking(false); }
  };

  const verifications = useMemo(() => indexVerifications(verification), [verification]);

  const locateVerification = (view: FieldVerificationView) => {
    const focus = toPreviewFocus(view, fieldLabel(view.fieldKey.replace(/^items\[\d+\]\./, '')));
    if (focus) setFocusedField(focus);
  };

  const handleSave = async () => {
    if (!document || !isEditing) return;

    const accountError = accountingAccountError(accountingAccount);
    if (accountError) { setSaveMessage(''); setSaveError(accountError); return; }

    setIsSaving(true);
    setSaveMessage('');
    setSaveError('');

    try {
      if (invoice) {
        const updatedInvoice = await invoiceService.updateInvoice(invoice.id, toInvoiceUpdateRequest(document.id, invoiceForm, invoice));
        setInvoice(updatedInvoice);
        setInvoiceForm(toInvoiceForm(updatedInvoice));
      }

      const accountChanged = accountingAccount.trim() !== (classification?.accountingAccount || '');
      if (classificationValue || accountChanged) {
        const request = { category: classificationValue || undefined, accountingAccount: accountChanged ? accountingAccount.trim() : undefined };
        const updatedClassification = classification
          ? await classificationService.correctClassification(document.id, request)
          : await classificationService.updateClassification(document.id, request);
        setClassification(updatedClassification);
        setAccountingAccount(updatedClassification.accountingAccount || '');
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
      void recheckFields();
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : 'Không thể lưu thay đổi. Vui lòng thử lại.');
    } finally {
      setIsSaving(false);
    }
  };

  /** Header drafts stay untouched; only the stored items, derived state and index-keyed badges change. */
  const handleItemsSaved = (updated: InvoiceResponse) => {
    setInvoice(updated);
    setVerification(withoutItemVerifications);
    void recheckFields();
    void documentService.getBusinessState(documentId).then(setBusinessState).catch(() => setBusinessState(null));
  };

  const handleCorrectExtractedField = async (fieldId: number, fieldValue: string) => {
    await documentService.correctExtractedField(documentId, fieldId, fieldValue);
    void recheckFields();
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
      setAccountingAccount(result?.accountingAccount || '');
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

  const decideReconciliation = async (id: number, status: 'ACCEPTED' | 'NEED_REVIEW', comment?: string) => {
    const updated = await reconciliationService.decide(documentId, id, status, comment);
    setReconciliations(current => current.map(item => item.id === id ? updated : item));
    void documentService.getBusinessState(documentId).then(setBusinessState).catch(() => setBusinessState(null));
  };

  const hasUnsavedChanges = useMemo(() => {
    if (itemsDirty) return true;
    if (!isEditing) return false;
    const originalForm = invoice ? toInvoiceForm(invoice) : emptyInvoiceForm;
    const originalCategory = classification?.accountingCategoryCode || classification?.category || '';
    return JSON.stringify(invoiceForm) !== JSON.stringify(originalForm) || classificationValue !== originalCategory
	  || accountingAccount.trim() !== (classification?.accountingAccount || '')
	  || documentTypeId !== (document?.typeId == null ? '' : String(document.typeId));
  }, [accountingAccount, classification, classificationValue, document, documentTypeId, invoice, invoiceForm, isEditing, itemsDirty]);

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
  const visibleReviewReasons = reviewMessages(assessment?.reviewReasons ?? []);
  const classificationConfidence = formatConfidence(assessment?.classificationConfidence.value);
  const ocrConfidence = formatConfidence(assessment?.ocrConfidence.value);
  const canCorrect = hasCorrectionRole && document.reviewStatus !== 'APPROVED'
    && (document.status === 'PROCESSED' || document.status === 'NEED_REVIEW');
  /** Opens edit mode on the field so the accountant can correct it while comparing with the highlighted image. */
  const editVerification = (view: FieldVerificationView) => {
    locateVerification(view);
    if (!canCorrect) return;
    if (view.itemIndex != null) { setActiveTab('items'); return; }
    setIsEditing(true); setSaveMessage(''); setSaveError('');
    const key = view.fieldKey;
    window.setTimeout(() => {
      const input = globalThis.document.getElementById(`invoice-field-${key}`);
      input?.scrollIntoView({ block: 'center' }); (input as HTMLInputElement | null)?.focus();
    }, 50);
  };
  const canReview = hasCorrectionRole && document.reviewStatus === 'PENDING' && (document.status === 'PROCESSED' || document.status === 'NEED_REVIEW');
  const approvalBlock = canReview ? approvalBlockReason(businessState, Boolean(invoice)) : null;
  const canSubmit = isEmployeeView && document.status === 'UPLOADED' && document.reviewStatus === 'PENDING';
  const canResubmit = isEmployeeView && document.status === 'NEED_REVIEW' && (document.reviewStatus === 'REJECTED' || document.reviewStatus === 'CORRECTED');

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
          {isEditing && <button onClick={() => { setInvoiceForm(invoice ? toInvoiceForm(invoice) : emptyInvoiceForm); setClassificationValue(classification?.category || ''); setAccountingAccount(classification?.accountingAccount || ''); setDocumentTypeId(document.typeId == null ? '' : String(document.typeId)); setIsEditing(false); setSaveError(''); }} disabled={isSaving} className="si-btn-secondary">
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

      {(canReview || canSubmit || canResubmit) && <div className="si-card p-4"><div className="flex flex-wrap items-center gap-2"><span className="si-label mr-2">Kiểm duyệt</span>{canSubmit && <button disabled={isWorkflowPending} onClick={() => void runWorkflow('SUBMIT')} className="si-btn-primary">Gửi xử lý</button>}{canResubmit && <button disabled={isWorkflowPending} onClick={() => void runWorkflow('RESUBMIT')} className="si-btn-primary">Gửi lại</button>}{canReview && <><button disabled={isWorkflowPending} onClick={() => void runWorkflow('START_REVIEW')} hidden={businessState?.review === 'IN_REVIEW'} className="si-btn-secondary border-brand-200 text-brand-700">Bắt đầu kiểm duyệt</button><button disabled={isWorkflowPending || Boolean(approvalBlock)} title={approvalBlock ?? undefined} onClick={() => void runWorkflow('APPROVE')} className="si-btn-secondary border-parsed/30 text-parsed">Duyệt</button><button disabled={isWorkflowPending} onClick={() => setWorkflowAction('REJECT')} className="si-btn-secondary border-failed/30 text-failed">Từ chối</button><button disabled={isWorkflowPending} onClick={() => setWorkflowAction('REQUEST_INFO')} className="si-btn-secondary border-review/30 text-review">Yêu cầu bổ sung</button></>}</div>{approvalBlock && <p className="mt-2 flex flex-wrap items-center gap-2 text-sm text-review"><span>{approvalBlock}</span><button type="button" onClick={() => setActiveTab('reconciliation')} className="si-btn-secondary !px-2 !py-1 text-xs">Mở Đối soát</button></p>}{workflowAction && <div className="mt-3 flex flex-wrap gap-2"><input value={workflowNote} onChange={(event) => setWorkflowNote(event.target.value)} placeholder="Nhập lý do" className="si-input min-w-64 flex-1" /><button disabled={isWorkflowPending} onClick={() => void runWorkflow(workflowAction)} className="si-btn-primary">{isWorkflowPending ? 'Đang gửi...' : 'Xác nhận'}</button><button disabled={isWorkflowPending} onClick={() => { setWorkflowAction(null); setWorkflowNote(''); }} className="rounded-control px-3 py-2 text-sm text-ink-soft">Hủy</button></div>}</div>}

      {hasUnsavedChanges && <div className="rounded-control border border-review/30 bg-review-surface px-3 py-2 text-sm text-review">Có thay đổi chưa lưu. Dữ liệu vẫn được giữ khi đổi tab.</div>}

      <div className="grid min-h-0 flex-1 gap-4 xl:grid-cols-[minmax(0,11fr)_minmax(420px,9fr)] xl:overflow-hidden">
        <div className="h-[calc(68vh/var(--ui-zoom))] min-h-[520px] xl:h-full xl:min-h-0">
          <OcrDocumentPreview documentId={document.id} fileType={document.fileType} ocr={ocr}
            showFieldPanel={false} focusedField={focusedField} fields={extractedFields} documentType={document.documentType} canCorrectFields={canCorrect}
            onCorrectField={handleCorrectExtractedField} />
        </div>

        <div className="si-card flex min-h-[560px] flex-col overflow-hidden xl:min-h-0">
          <div className="sticky top-0 z-20 grid grid-cols-4 border-b border-hairline bg-surface/95 px-2 pt-2 backdrop-blur" role="tablist" aria-label="Thông tin chứng từ">
            {([['overview', 'Kiểm tra dữ liệu'], ['items', 'Hàng hóa'], ['reconciliation', 'Đối soát'], ['storage', 'Hồ sơ & lịch sử']] as const).map(([key, label]) => <button key={key} type="button" role="tab" aria-selected={activeTab === key} onClick={() => setActiveTab(key)} className={clsx('relative border-b-2 px-2 py-3 text-sm font-semibold tracking-tight transition-colors', activeTab === key ? 'border-brand-600 text-brand-700' : 'border-transparent text-ink-muted hover:border-hairline-strong hover:text-ink')}>{label}</button>)}
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto p-4">
            {activeTab === 'overview' && <div className="space-y-4">
              <section className="si-card p-4">
                <h3 className="si-section-title mb-3">Đánh giá OCR/AI</h3>
                <div className="grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
                  <div><p className="text-xs text-ink-muted">Độ tin cậy OCR</p><p>{ocrConfidence}</p></div>

                  <div><p className="text-xs text-ink-muted">Độ tin cậy phân loại</p><p>{classificationConfidence}</p></div>
                </div>
                <p className="mt-3 text-sm">Kiểm tra dữ liệu: {assessment?.validationStatus === 'VALID' ? 'Hợp lệ' : assessment?.validationStatus === 'NEED_REVIEW' ? 'Cần kiểm tra' : assessment?.validationStatus === 'FAILED' ? 'Xử lý thất bại' : 'Chưa xác định'}</p>
                <p className="mt-2 text-xs text-ink-muted">Độ tin cậy do AI ước tính, không phải độ chính xác đã đo.</p>
                {verification && verification.fields.length > 0 && <div className="mt-3 rounded-control border border-hairline p-3 text-sm">
                  <div className="flex flex-wrap items-center justify-between gap-2"><p className="font-semibold">Kiểm chứng theo trường</p>
                    {canCorrect && <button type="button" disabled={isRechecking} onClick={() => void recheckFields()} className="si-btn-secondary !px-2 !py-1 text-xs">{isRechecking ? 'Đang kiểm chứng…' : 'Kiểm chứng lại'}</button>}</div>
                  <p className="mt-1 flex flex-wrap gap-x-3 gap-y-1">{(['VERIFIED', 'WARNING', 'UNVERIFIED', 'MISSING'] as const).map(status => <span key={status}>{verificationStatusLabel[status]}: <strong>{verification.counts[status] ?? 0}</strong></span>)}</p>
                  <p className="mt-1 text-xs text-ink-muted">{aiInputModeLabel(verification.aiInputMode)} · {verification.ruleVersion}. {verification.statusNote} {verification.scoreNote}</p>
                  {verification.stale && <p className="mt-1 text-xs text-review">Dữ liệu đã được sửa sau lần kiểm chứng gần nhất — bấm “Kiểm chứng lại”.</p>}
                </div>}
                {verificationError && <p className="mt-2 text-sm text-review">{verificationError}</p>}
                {assessmentError && <p className="mt-2 text-sm text-review">{assessmentError}</p>}
                {visibleReviewReasons.length > 0 && <div className="mt-3 text-sm"><p className="font-semibold">Lý do cần kiểm tra</p><ul className="mt-2 list-disc space-y-1 pl-5">{visibleReviewReasons.map((reason, index) => <li key={index}>{reason}</li>)}</ul></div>}
                {Boolean(assessment?.warnings.length) && <details className="mt-3"><summary className="cursor-pointer text-sm text-ink-muted">Chi tiết cảnh báo ({assessment?.warnings.length ?? 0})</summary><div className="mt-3 space-y-2">{assessment?.warnings.map((warning, index) => <div key={index} className={clsx('rounded-control border p-3 text-sm', warning.severity === 'CRITICAL' ? 'border-review/25 bg-review-surface text-review' : 'border-hairline')}><p className="font-medium">{warning.severity === 'CRITICAL' ? 'Cần kiểm tra' : 'Thông tin'}</p><p className="mt-1 break-words">{assessmentMessage(warning.message, warning.code)}</p>{warning.affectedField && <p className="mt-1 text-xs">Trường liên quan: {fieldLabel(warning.affectedField)}</p>}</div>)}</div></details>}
              </section>
              <section className="si-card border-tax-vat/25 bg-tax-vat-surface p-4"><div className="flex items-start justify-between gap-4"><div><p className="si-label text-tax-vat">Loại chứng từ</p><p className="mt-1 font-sans text-[20px] font-bold tracking-tight text-ink">{document.documentType ? documentTypeLabel(document.documentType) : 'Chưa xác định'}</p><p className="mt-1 text-xs text-ink-muted">{classification?.aiGenerated === false ? 'Đã được kế toán chỉnh sửa' : document.documentType ? 'AI đề xuất từ chứng từ' : 'AI chưa xác định được loại chứng từ'}</p></div></div>{isEditing && <label className="mt-4 block text-xs font-medium text-ink-soft">Loại chứng từ<select value={documentTypeId} onChange={event => setDocumentTypeId(event.target.value)} className="mt-1 w-full rounded-control border border-hairline-strong bg-surface px-3 py-2 text-sm"><option value="" disabled>Chưa xác định</option>{documentTypes.map(type => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label>}</section>
              <section className="rounded-card border border-brand-200 bg-brand-50/60 p-4">
                <div><p className="si-label text-brand-700">Nhóm nghiệp vụ kế toán</p><p className="mt-1 font-sans text-[20px] font-bold tracking-tight text-ink">{classification?.accountingCategoryName || classification?.category || 'Chưa phân loại'}</p><p className="mt-1 text-xs text-ink-muted">{classification?.accountingAccount ? `${classification.aiGenerated ? 'Tài khoản gợi ý' : 'Tài khoản kế toán'} · ${classification.accountingAccount}` : 'Độc lập với loại chứng từ'}</p></div>
                {isEditing && <label className="mt-4 block text-xs font-medium text-ink-soft">Hạng mục kế toán<select value={classificationValue} onChange={event => setClassificationValue(event.target.value)} className="mt-1 w-full rounded-control border border-hairline-strong bg-surface px-3 py-2 text-sm"><option value="">Chưa phân loại</option>{accountingCategories.map(category => <option key={category.id} value={category.categoryCode}>{category.categoryName} · {categoryTypeLabel(category.categoryType)}</option>)}</select></label>}
                {isEditing && <label className="mt-3 block text-xs font-medium text-ink-soft">Tài khoản kế toán<input value={accountingAccount} onChange={event => setAccountingAccount(event.target.value)} inputMode="numeric" placeholder="Ví dụ: 6421" className="si-input mt-1" /></label>}
                {classificationState === 'empty' && <p className="mt-3 text-sm text-ink-soft">Chứng từ chưa được phân loại.</p>}
                {classificationState === 'loading' && <p className="mt-3 text-sm text-ink-muted">Đang tải kết quả phân loại...</p>}
                {classificationState === 'error' && <div className="mt-3 flex items-center justify-between gap-2 rounded-control bg-review-surface p-2 text-sm text-review"><span>Không thể tải kết quả phân loại.</span><button type="button" onClick={() => void retryClassification()} className="si-btn-secondary text-xs">Thử lại</button></div>}
                {isEditing && categoryState === 'loading' && <p className="mt-2 text-xs text-ink-muted">Đang tải danh mục...</p>}
                {isEditing && categoryState === 'empty' && <p className="mt-2 text-xs text-ink-soft">Công ty chưa có nhóm nghiệp vụ đang hoạt động.</p>}
                {isEditing && categoryState === 'error' && <div className="mt-2 flex items-center justify-between gap-2 text-xs text-review"><span>Không thể tải danh mục công ty.</span><button type="button" onClick={() => void retryCategories()} className="si-btn-secondary text-xs">Thử lại</button></div>}
              </section>
              <section className="grid grid-cols-3 gap-2"><div className="rounded-card bg-canvas p-3"><p className="text-xs text-ink-muted">Trước thuế</p><p className="mt-1 font-semibold">{money(invoice?.subtotal)}</p></div><div className="rounded-card bg-canvas p-3"><p className="text-xs text-ink-muted">Thuế GTGT</p><p className="mt-1 font-semibold">{money(invoice?.vatAmount)}</p></div><div className="rounded-card bg-brand-50 p-3"><p className="text-xs text-brand-600">Tổng tiền</p><p className="mt-1 font-bold text-brand-800">{money(invoice?.totalAmount)}</p></div></section>
              <section className="si-card p-4"><div className="grid grid-cols-2 gap-3 text-sm"><div><p className="text-xs text-ink-muted">Số hóa đơn</p><p className="font-medium">{valueOrDash(invoice?.invoiceNumber)}</p></div><div><p className="text-xs text-ink-muted">Ngày hóa đơn</p><p className="font-medium">{valueOrDash(invoice?.invoiceDate)}</p></div><div><p className="text-xs text-ink-muted">Người bán</p><p className="font-medium">{valueOrDash(invoice?.sellerName)}</p></div><div><p className="text-xs text-ink-muted">Người mua</p><p className="font-medium">{valueOrDash(invoice?.buyerName)}</p></div></div></section>
              <details open={isEditing || undefined} className="si-card p-4"><summary className="cursor-pointer font-medium">Thông tin hóa đơn và chỉnh sửa</summary><div className="mt-3">{invoice ? <VatInvoicePanel invoice={invoice} form={invoiceForm} editable={isEditing} onChange={updateInvoiceForm} rawText={ocr?.rawText} verifications={verifications} onLocate={locateVerification} onEdit={editVerification} /> : <p>Chưa có dữ liệu hóa đơn.</p>}</div></details>
              {extractedFields.length > 0 && <details className="si-card overflow-hidden"><summary className="cursor-pointer p-4 font-medium">Trường trích xuất ({extractedFields.length})</summary><ExtractedFieldPanel verifications={verifications} onLocate={locateVerification} fields={extractedFields} documentType={document.documentType} selectedId={focusedField?.id} canCorrect={canCorrect} onSelect={field => setFocusedField(current => current?.id === field.id ? null : field)} onHover={() => {}} onCorrect={handleCorrectExtractedField} /></details>}
              <BusinessStateCard state={businessState} />

            </div>}

            <div hidden={activeTab !== 'items'}><InvoiceItemsPanel invoice={invoice} canEdit={canCorrect} needsReconciliation={Boolean(businessState?.reconciliationStale) || businessState?.reconciliation === 'NOT_RUN'} onSaved={handleItemsSaved} onDirtyChange={setItemsDirty} onOpenReconciliation={() => setActiveTab('reconciliation')} /></div>

            {activeTab === 'reconciliation' && <div className="space-y-3">{hasCorrectionRole && <DocumentPaymentPanel documentId={document.id} companyId={document.companyId} canRun={hasCorrectionRole} onUpdated={() => { void documentService.getBusinessState(document.id).then(setBusinessState).catch(() => setBusinessState(null)); }} />}{hasCorrectionRole && document.reviewStatus === 'APPROVED' && <div className="flex gap-2">{(['INCOME','EXPENSE'] as const).map(type => <button key={type} type="button" className="si-btn-secondary" onClick={() => navigate(`/${role === 'ADMIN' ? 'admin' : 'accountant'}/financial-transactions?documentId=${document.id}&companyId=${document.companyId ?? ''}&type=${type}`)}>{type === 'INCOME' ? 'Tạo phiếu Thu' : 'Tạo phiếu Chi'}</button>)}</div>}<h3 className="si-section-title">Đối soát chứng từ</h3><div className="flex items-center justify-between gap-3"><p className="text-sm text-ink-muted">Kết quả đối soát được hệ thống tính toán và lưu lại.</p>{hasCorrectionRole && <button type="button" disabled={isReconciling || document.reviewStatus === 'APPROVED' || document.status === 'COMPLETED'} onClick={() => void runReconciliation()} className="si-btn-primary">{isReconciling ? 'Đang đối soát...' : reconciliations.length ? 'Chạy lại' : 'Chạy đối soát'}</button>}</div>{reconciliationError && <p className="rounded-control border-failed/25 bg-failed-surface border p-3 text-sm text-failed">{reconciliationError}</p>}{hasInsufficientReconciliationData(reconciliations.map(item => item.status)) && <p className="rounded-control border-review/25 bg-review-surface border p-3 text-sm text-review">Chưa đủ dữ liệu hàng hóa để đối soát. Giá trị chưa xác định được hiển thị “—”; xem chi tiết từng phép kiểm tra để bổ sung dữ liệu theo quy trình hiện có.</p>}{businessState?.reconciliationStale && <p className="rounded-control border-review/25 bg-review-surface border p-3 text-sm text-review">Hóa đơn đã thay đổi sau lần đối soát gần nhất. Kết quả dưới đây đã cũ, cần chạy lại đối soát.</p>}{reconciliations.length ? reconciliations.map(item => <ReconciliationResultCard key={item.id ?? item.type} item={item}>{canCorrect && !businessState?.reconciliationStale && <ReconciliationDecision key={item.status} item={item} onDecide={(status, comment) => decideReconciliation(item.id, status, comment)} />}</ReconciliationResultCard>) : <EmptyState title="Chưa có kết quả đối soát" description="Giao diện không tự suy diễn trạng thái khớp. Hãy chạy đối soát để hệ thống tính toán và lưu kết quả." />}</div>}

            {activeTab === 'storage' && <div className="space-y-4"><div className="flex flex-wrap gap-2">{([['storage','File & lưu trữ'],['versions','Phiên bản'],['history','Lịch sử']] as const).map(([key,label]) => <button type="button" key={key} onClick={() => setArchiveTab(key)} className={archiveTab === key ? 'si-btn-primary' : 'si-btn-secondary'}>{label}</button>)}</div><DocumentArchivePanel key={document.id + '-' + archiveTab} documentId={document.id} mode={archiveTab} /><details className="si-card p-4"><summary className="cursor-pointer">Thông tin kỹ thuật và OCR gốc</summary>{archiveTab === 'history' && <ProcessingHistoryCard history={processingHistory} error={historyError} retrying={retrying} documentStatus={document.status} reviewStatus={document.reviewStatus} onRetry={() => { setRetrying(true); documentService.retryProcessing(document.id).catch(() => setHistoryError('Không thể xử lý lại.')).finally(() => { setRetrying(false); setReloadVersion(n => n + 1); }); }} />}<details className="mt-3"><summary>Cảnh báo kỹ thuật gốc</summary><pre className="mt-2 max-h-60 overflow-auto whitespace-pre-wrap break-words text-xs">{[...(assessment?.reviewReasons ?? []), ...(classification?.reason ? [classification.reason] : [])].join('\n') || 'Không có cảnh báo'}</pre></details><p className="mt-2 text-sm">Bộ nhận dạng: {ocr?.ocrEngine || 'Chưa xác định'}</p><pre className="mt-3 max-h-72 overflow-auto whitespace-pre-wrap text-xs">{ocr?.rawText || 'Chưa có nội dung OCR'}</pre></details></div>}

          </div>

        </div>
      </div>
    </div>
  );
};

export default AccountantDocumentDetail;

import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Download, Pencil, Save, X } from 'lucide-react';
import clsx from 'clsx';
import StatusBadge from '../../components/StatusBadge';
import classificationService from '../../services/classificationService';
import accountingCategoryService, { type AccountingCategory } from '../../services/accountingCategoryService';
import documentService, { mapDocument } from '../../services/documentService';
import invoiceService from '../../services/invoiceService';
import type { ClassificationResponse } from '../../services/classificationService';
import type { DocumentResponse, ExtractedFieldResponse, OCRResultResponse, WorkflowAction } from '../../services/documentService';
import type { InvoiceResponse } from '../../services/invoiceService';
import { getEffectiveRole } from '../../services/authService';
import ErrorState from '../../components/ui/ErrorState';
import LoadingState from '../../components/ui/LoadingState';
import OcrDocumentPreview from '../../components/OcrDocumentPreview';
import VatInvoicePanel, { documentTypeLabel, emptyInvoiceForm, toInvoiceForm, toInvoiceUpdateRequest, type InvoiceFormData } from '../../components/VatInvoicePanel';
import { formatConfidence } from '../../utils/confidence';

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
  const [classification, setClassification] = useState<ClassificationResponse | null>(null);
  const [invoice, setInvoice] = useState<InvoiceResponse | null>(null);
  const [extractedFields, setExtractedFields] = useState<ExtractedFieldResponse[]>([]);
  const [invoiceForm, setInvoiceForm] = useState<InvoiceFormData>(emptyInvoiceForm);
  const [classificationValue, setClassificationValue] = useState('');
  const [accountingCategories, setAccountingCategories] = useState<AccountingCategory[]>([]);
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
      try {
        setAccountingCategories(await accountingCategoryService.getCategories(role === 'ADMIN' ? loadedDocument.companyId : undefined));
      } catch {
        setSectionErrors((current) => [...current, 'Không thể tải danh sách nhóm nghiệp vụ của công ty.']);
      }
      const completed = hasCompletedPipeline(loadedDocument.status);
      const shouldLoadOcr = loadedDocument.status !== 'UPLOADED';
      const shouldLoadInvoice = completed && isInvoiceDocument(loadedDocument.documentType);
      const [ocrResult, classificationResult, invoiceResult, extractedFieldsResult] = await Promise.allSettled([
        optionalRequest(shouldLoadOcr, () => documentService.getDocumentOCR(documentId), null),
        optionalRequest(completed, () => classificationService.getClassification(documentId), null),
        optionalRequest(shouldLoadInvoice, () => invoiceService.getInvoiceByDocumentId(documentId), null),
        optionalRequest(completed, () => documentService.getDocumentExtractedFields(documentId), []),
      ]);
      if (!isMounted) return;

      if (ocrResult.status === 'fulfilled' && ocrResult.value) setOcr(ocrResult.value);
      else if (completed) setSectionErrors((current) => [...current, 'Không thể tải kết quả OCR.']);
      if (classificationResult.status === 'fulfilled') {
        if (classificationResult.value) {
          setClassification(classificationResult.value);
          setClassificationValue(classificationResult.value.accountingCategoryCode || classificationResult.value.category || '');
        }
      }
      else if (completed) setSectionErrors((current) => [...current, 'Không thể tải kết quả phân loại.']);
      if (invoiceResult.status === 'fulfilled' && invoiceResult.value) {
        setInvoice(invoiceResult.value);
        setInvoiceForm(toInvoiceForm(invoiceResult.value));
      }
      else if (shouldLoadInvoice) setSectionErrors((current) => [...current, 'Không thể tải dữ liệu hóa đơn.']);
      if (extractedFieldsResult.status === 'fulfilled') setExtractedFields(extractedFieldsResult.value);
      else if (completed) setSectionErrors((current) => [...current, 'Không thể tải các trường trích xuất bổ sung.']);

      setIsLoading(false);
    };

    loadDocumentDetail().catch(() => {
      if (isMounted) {
        setError('Không thể tải thông tin chứng từ');
        setIsLoading(false);
      }
    });

    return () => { isMounted = false; };
  }, [documentId]);

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
      setDocument(refreshed); setWorkflowNote(''); setWorkflowAction(null); setWorkflowMessage('Đã cập nhật workflow.');
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

  if (isLoading) {
    return <LoadingState label="Đang tải thông tin chứng từ..." />;
  }

  if (error || !document) {
    return <ErrorState message={error || 'Không tìm thấy chứng từ'} />;
  }

  const documentView = mapDocument(document);
  const classificationConfidence = formatConfidence(classification?.confidence);
  const ocrConfidence = formatConfidence(ocr?.confidence);
  const canCorrect = hasCorrectionRole && document.reviewStatus !== 'APPROVED'
    && (document.status === 'PROCESSED' || document.status === 'NEED_REVIEW');
  const canReview = hasCorrectionRole && (document.status === 'PROCESSED' || document.status === 'NEED_REVIEW');
  const canSubmit = isEmployeeView && document.status === 'UPLOADED' && document.reviewStatus === 'PENDING';
  const canResubmit = isEmployeeView && document.status === 'NEED_REVIEW' && (document.reviewStatus === 'REJECTED' || document.reviewStatus === 'CORRECTED');
  const dynamicFields = distinctMeaningfulFields(extractedFields);
  return (
    <div className="space-y-4 h-[calc(100vh-8rem)] flex flex-col">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <button onClick={() => navigate(`${basePath}/documents`)} className="p-2 hover:bg-slate-200 rounded-full transition-colors text-slate-600">
            <ArrowLeft size={20} />
          </button>
          <div className="min-w-0">
            <div className="flex items-center gap-3">
              <h1 className="max-w-[55vw] truncate text-2xl font-bold text-slate-800" title={`${document.id} - ${document.originalFileName}`}>{document.id} - {document.originalFileName}</h1>
              <StatusBadge status={documentView.status} />
            </div>
            <p className="text-slate-500 text-sm mt-1">{isEmployeeView ? 'Thông tin chứng từ của bạn' : 'Đã tải lên vào'} {isEmployeeView ? '' : documentView.date}</p>
          </div>
        </div>
        <div className="flex gap-3">
          <button onClick={async () => { const url = await documentService.downloadDocument(document.id); const link = window.document.createElement('a'); link.href = url; link.download = document.originalFileName; link.click(); URL.revokeObjectURL(url); }} className="px-4 py-2 flex items-center gap-2 bg-white border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 transition-colors">
            <Download size={18} />
            <span>Tải file gốc</span>
          </button>
          {canCorrect && !isEditing && <button onClick={() => { setIsEditing(true); setSaveMessage(''); setSaveError(''); }} className="px-4 py-2 flex items-center gap-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors shadow-sm">
            <Pencil size={18} />
            <span>Chỉnh sửa thủ công</span>
          </button>}
          {isEditing && <button onClick={() => { setInvoiceForm(invoice ? toInvoiceForm(invoice) : emptyInvoiceForm); setClassificationValue(classification?.category || ''); setIsEditing(false); setSaveError(''); }} disabled={isSaving} className="px-4 py-2 flex items-center gap-2 bg-white border border-slate-300 text-slate-700 rounded-lg hover:bg-slate-50 disabled:opacity-60">
            <X size={18} /><span>Hủy</span>
          </button>}
          {isEditing && <button onClick={handleSave} disabled={isSaving} className="px-4 py-2 flex items-center gap-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-60 transition-colors shadow-sm">
            <Save size={18} />
            <span>{isSaving ? 'Đang lưu...' : 'Lưu thay đổi'}</span>
          </button>}
        </div>
      </div>

      {saveMessage && <div className="rounded-lg bg-emerald-50 border border-emerald-200 px-4 py-3 text-sm text-emerald-700">{saveMessage}</div>}
      {saveError && <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">{saveError}</div>}
      {workflowMessage && <div className="rounded-lg bg-emerald-50 border border-emerald-200 px-4 py-3 text-sm text-emerald-700">{workflowMessage}</div>}
      {workflowError && <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">{workflowError}</div>}
      {sectionErrors.length > 0 && <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">{sectionErrors.join(' ')}</div>}

      {(canReview || canSubmit || canResubmit) && <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"><div className="flex flex-wrap items-center gap-2"><span className="mr-2 text-sm font-medium text-slate-700">Workflow</span>{canSubmit && <button disabled={isWorkflowPending} onClick={() => void runWorkflow('SUBMIT')} className="rounded-lg bg-blue-600 px-3 py-2 text-sm text-white disabled:opacity-50">Gửi xử lý</button>}{canResubmit && <button disabled={isWorkflowPending} onClick={() => void runWorkflow('RESUBMIT')} className="rounded-lg bg-blue-600 px-3 py-2 text-sm text-white disabled:opacity-50">Gửi lại</button>}{canReview && <><button disabled={isWorkflowPending} onClick={() => void runWorkflow('START_REVIEW')} className="rounded-lg border border-blue-300 px-3 py-2 text-sm text-blue-700 disabled:opacity-50">Bắt đầu review</button><button disabled={isWorkflowPending} onClick={() => void runWorkflow('APPROVE')} className="rounded-lg border border-emerald-300 px-3 py-2 text-sm text-emerald-700 disabled:opacity-50">Duyệt</button><button disabled={isWorkflowPending} onClick={() => setWorkflowAction('REJECT')} className="rounded-lg border border-red-300 px-3 py-2 text-sm text-red-700 disabled:opacity-50">Từ chối</button><button disabled={isWorkflowPending} onClick={() => setWorkflowAction('REQUEST_INFO')} className="rounded-lg border border-amber-300 px-3 py-2 text-sm text-amber-700 disabled:opacity-50">Yêu cầu bổ sung</button></>}</div>{workflowAction && <div className="mt-3 flex flex-wrap gap-2"><input value={workflowNote} onChange={(event) => setWorkflowNote(event.target.value)} placeholder="Nhập lý do" className="min-w-64 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm" /><button disabled={isWorkflowPending} onClick={() => void runWorkflow(workflowAction)} className="rounded-lg bg-blue-600 px-3 py-2 text-sm text-white disabled:opacity-50">{isWorkflowPending ? 'Đang gửi...' : 'Xác nhận'}</button><button disabled={isWorkflowPending} onClick={() => { setWorkflowAction(null); setWorkflowNote(''); }} className="rounded-lg px-3 py-2 text-sm text-slate-600">Hủy</button></div>}</div>}

      <div className="min-h-0 flex-1 space-y-6 overflow-y-auto pr-1">
        <div className="h-[min(72vh,760px)] min-h-[620px]">
          <OcrDocumentPreview documentId={document.id} fileType={document.fileType} ocr={ocr}
            fields={extractedFields} documentType={document.documentType} canCorrectFields={canCorrect}
            onCorrectField={handleCorrectExtractedField} />
        </div>

        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          <div className="bg-white rounded-2xl border border-blue-200 shadow-sm overflow-hidden p-5 bg-gradient-to-br from-blue-50 to-white">
            <div className="flex items-start justify-between mb-4">
              <div>
                <h3 className="text-sm font-semibold text-blue-800 uppercase tracking-wider">AI Phân Loại {isEmployeeView && '(chỉ xem)'}</h3>
                <p className="text-sm text-slate-600 mt-1">Kết quả từ backend classification</p>
              </div>
              <div className="text-right">
                <div className={clsx('text-xl font-bold', classificationConfidence === '—' ? 'text-slate-500' : 'text-emerald-600')}>
                  {classificationConfidence}
                </div>
                <div className="text-xs text-slate-500">Độ tin cậy phân loại</div>
              </div>
            </div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Loại nghiệp vụ / Chi phí</label>
            <select
              value={classificationValue}
              onChange={(event) => setClassificationValue(event.target.value)}
              disabled={!isEditing}
              className="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-sm font-medium text-slate-800 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none"
            >
              <option value="">Chưa có dữ liệu phân loại</option>
              {accountingCategories.map((category) => <option key={category.id} value={category.categoryCode}>{category.categoryName}</option>)}
            </select>
            {classification?.accountingAccount && <p className="mt-2 text-xs text-slate-600">Tài khoản AI đề xuất: <span className="font-medium">{classification.accountingAccount}</span></p>}
            {!classification && <p className="text-xs text-slate-500 mt-2">Backend chưa có bản ghi classification cho chứng từ này.</p>}
            {classification && !classification.aiGenerated && <p className="mt-2 text-xs font-medium text-amber-700">Đã chỉnh sửa thủ công</p>}
          </div>

          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
            <h3 className="text-lg font-semibold text-slate-800">Thông tin nhận diện</h3>
            <div className="mt-3 grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
              <span className="block text-xs font-medium text-slate-500">Loại chứng từ</span>
              <span className="break-words text-slate-800">{documentTypeLabel(document.documentType)}</span>
              <div><span className="block text-xs font-medium text-slate-500">Độ tin cậy OCR</span><span>{ocrConfidence}</span></div>
              {document.companyRole?.role && <div><span className="block text-xs font-medium text-slate-500">Vai trò công ty</span><span>{companyRoleLabels[document.companyRole.role] || document.companyRole.role}</span>{formatConfidence(document.companyRole.confidence) && <span className="ml-2 text-xs text-slate-500">{formatConfidence(document.companyRole.confidence)}</span>}{document.companyRole.reason && <p className="mt-1 break-words text-xs text-slate-500">{document.companyRole.reason}</p>}</div>}
              {document.documentDirection && <div><span className="block text-xs font-medium text-slate-500">Hướng chứng từ</span><span>{directionLabels[document.documentDirection] || document.documentDirection}</span></div>}
              {document.transactionAssessment?.type && <div><span className="block text-xs font-medium text-slate-500">Đề xuất nghiệp vụ AI</span><span>{assessmentLabels[document.transactionAssessment.type] || document.transactionAssessment.type}</span>{formatConfidence(document.transactionAssessment.confidence) && <span className="ml-2 text-xs text-slate-500">{formatConfidence(document.transactionAssessment.confidence)}</span>}{document.transactionAssessment.reason && <p className="mt-1 break-words text-xs text-slate-500">{document.transactionAssessment.reason}</p>}</div>}
            </div>
          </div>

          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5 flex-1 xl:row-span-2">
            <div className="flex items-center justify-between mb-6">
              <h3 className="text-lg font-semibold text-slate-800">{document.documentType === 'VAT_INVOICE' ? 'Thông tin hóa đơn giá trị gia tăng' : 'Dữ liệu hóa đơn'}</h3>
              <span className="text-xs px-2.5 py-1 bg-slate-100 text-slate-600 rounded-md font-medium">API backend</span>
            </div>
            {!isInvoiceDocument(document.documentType) ? (
              <p className="text-sm text-slate-500">Không áp dụng cho loại chứng từ này.</p>
            ) : invoice ? (
              <VatInvoicePanel invoice={invoice} form={invoiceForm} editable={isEditing} onChange={updateInvoiceForm} rawText={ocr?.rawText} />
            ) : (
              <p className="text-sm text-slate-500">Backend chưa có invoice cho chứng từ này.</p>
            )}
            {!invoice && ocr && <details className="mt-6 rounded-xl border border-slate-200 p-4"><summary className="cursor-pointer font-medium text-slate-700">Văn bản OCR gốc</summary><pre className="mt-3 max-h-64 overflow-auto whitespace-pre-wrap text-xs text-slate-600">{ocr.rawText || 'Chưa có nội dung'}</pre></details>}
          </div>

          {dynamicFields.length > 0 && <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
            <h3 className="text-lg font-semibold text-slate-800">Trường trích xuất bổ sung</h3>
            <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
              {dynamicFields.map((field) => {
                const confidenceValue = formatConfidence(field.confidence);
                return <div key={field.id} className="min-w-0 rounded-lg border border-slate-200 p-3"><div className="text-xs font-medium text-slate-500">{humanizeFieldName(field.fieldName)}</div><div className="mt-1 whitespace-pre-wrap break-words text-sm text-slate-800">{field.fieldValue}</div><div className="mt-2 text-xs text-slate-500">Độ tin cậy: {confidenceValue}</div></div>;
              })}
            </div>
          </div>}
        </div>
      </div>
    </div>
  );
};

export default AccountantDocumentDetail;

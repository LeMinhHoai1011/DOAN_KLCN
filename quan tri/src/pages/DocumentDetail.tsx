import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Download, Pencil, Save, X } from 'lucide-react';
import clsx from 'clsx';
import StatusBadge from '../components/StatusBadge';
import classificationService from '../services/classificationService';
import accountingCategoryService, { type AccountingCategory } from '../services/accountingCategoryService';
import documentService, { mapDocument } from '../services/documentService';
import invoiceService from '../services/invoiceService';
import type { ClassificationResponse } from '../services/classificationService';
import type { DocumentResponse, OCRResultResponse } from '../services/documentService';
import type { ExtractedFieldResponse } from '../services/documentService';
import OcrDocumentPreview from '../components/OcrDocumentPreview';
import type { InvoiceResponse } from '../services/invoiceService';
import VatInvoicePanel, { emptyInvoiceForm, toInvoiceForm, toInvoiceUpdateRequest, type InvoiceFormData } from '../components/VatInvoicePanel';
import { formatConfidence } from '../utils/confidence';
import { getCurrentUser, getEffectiveRole } from '../services/authService';

const hasCompletedPipeline = (status: DocumentResponse['status']) => (
  status === 'PROCESSED' || status === 'NEED_REVIEW' || status === 'COMPLETED'
);

const isInvoiceDocument = (documentType: string | null) => {
  const normalized = documentType?.trim().toUpperCase().replace(/[- ]/g, '_');
  return normalized === 'INVOICE' || normalized === 'VAT_INVOICE' || Boolean(normalized?.endsWith('_INVOICE'));
};

const DocumentDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const documentId = Number(id);
  const [document, setDocument] = useState<DocumentResponse | null>(null);
  const [ocr, setOcr] = useState<OCRResultResponse | null>(null);
  const [extractedFields, setExtractedFields] = useState<ExtractedFieldResponse[]>([]);
  const [classification, setClassification] = useState<ClassificationResponse | null>(null);
  const [invoice, setInvoice] = useState<InvoiceResponse | null>(null);
  const [invoiceForm, setInvoiceForm] = useState<InvoiceFormData>(emptyInvoiceForm);
  const [classificationValue, setClassificationValue] = useState('');
  const [accountingCategories, setAccountingCategories] = useState<AccountingCategory[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [error, setError] = useState('');
  const [saveMessage, setSaveMessage] = useState('');
  const [saveError, setSaveError] = useState('');

  useEffect(() => {
    let isMounted = true;

    if (!Number.isInteger(documentId)) {
      setError('Mã chứng từ không hợp lệ');
      setIsLoading(false);
      return () => {
        isMounted = false;
      };
    }

    const loadDocumentDetail = async () => {
      let loadedDocument: DocumentResponse;
      try {
        loadedDocument = await documentService.getDocumentById(documentId);
      } catch {
        if (!isMounted) return;
        setError('Không thể tải thông tin chứng từ');
        setIsLoading(false);
        return;
      }
      if (!isMounted) return;

      setDocument(loadedDocument);
      try {
        setAccountingCategories(await accountingCategoryService.getCategories(getEffectiveRole() === 'ADMIN' ? loadedDocument.companyId : undefined));
      } catch {
        setAccountingCategories([]);
      }
      const completed = hasCompletedPipeline(loadedDocument.status);
      const [ocrResult, classificationResult, invoiceResult, extractedResult] = await Promise.allSettled([
        loadedDocument.status === 'UPLOADED' ? Promise.resolve(null) : documentService.getDocumentOCR(documentId),
        completed ? classificationService.getClassification(documentId) : Promise.resolve(null),
        completed && isInvoiceDocument(loadedDocument.documentType)
          ? invoiceService.getInvoiceByDocumentId(documentId) : Promise.resolve(null),
        completed ? documentService.getDocumentExtractedFields(documentId) : Promise.resolve([]),
      ]);
      if (!isMounted) return;

      if (ocrResult.status === 'fulfilled' && ocrResult.value) {
        setOcr(ocrResult.value);
      }

      if (classificationResult.status === 'fulfilled' && classificationResult.value) {
        setClassification(classificationResult.value);
        setClassificationValue(classificationResult.value.accountingCategoryCode || classificationResult.value.category || '');
      }

      if (invoiceResult.status === 'fulfilled' && invoiceResult.value) {
        setInvoice(invoiceResult.value);
        setInvoiceForm(toInvoiceForm(invoiceResult.value));
      }

      if (extractedResult.status === 'fulfilled') setExtractedFields(extractedResult.value);

      setIsLoading(false);
    };

    loadDocumentDetail().catch(() => {
      if (isMounted) {
        setError('Không thể tải thông tin chứng từ');
        setIsLoading(false);
      }
    });

    return () => {
      isMounted = false;
    };
  }, [documentId]);

  const updateInvoiceForm = (field: keyof InvoiceFormData, value: string) => {
    setInvoiceForm((current) => ({ ...current, [field]: value }));
  };

  const handleSave = async () => {
    if (!document) {
      return;
    }

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

  if (isLoading) {
    return <div className="p-6 text-center text-slate-500">Đang tải thông tin chứng từ...</div>;
  }

  if (error || !document) {
    return <div className="p-6 text-center text-red-600">{error || 'Không tìm thấy chứng từ'}</div>;
  }

  const documentView = mapDocument(document);
  const classificationConfidence = formatConfidence(classification?.confidence);
  const canCorrect = document.reviewStatus !== 'APPROVED'
    && (document.status === 'PROCESSED' || document.status === 'NEED_REVIEW');
  const canCorrectExtractedFields = canCorrect && ['ADMIN', 'ACCOUNTANT'].includes(getEffectiveRole(getCurrentUser()) ?? '');

  const handleCorrectExtractedField = async (fieldId: number, fieldValue: string) => {
    await documentService.correctExtractedField(document.id, fieldId, fieldValue);
    try {
      const refreshedFields = await documentService.getDocumentExtractedFields(document.id);
      setExtractedFields(refreshedFields);
    } catch {
      throw new Error('Đã lưu correction nhưng không thể tải lại dữ liệu. Vui lòng làm mới trang.');
    }
  };

  return (
    <div className="space-y-4 h-[calc(100vh-8rem)] flex flex-col">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <button onClick={() => navigate(-1)} className="p-2 hover:bg-slate-200 rounded-full transition-colors text-slate-600">
            <ArrowLeft size={20} />
          </button>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold text-slate-800">{document.id} - {document.originalFileName}</h1>
              <StatusBadge status={documentView.status} />
            </div>
            <p className="text-slate-500 text-sm mt-1">Đã tải lên vào {documentView.date}</p>
          </div>
        </div>
        <div className="flex gap-3">
          <button className="px-4 py-2 flex items-center gap-2 bg-white border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 transition-colors">
            <Download size={18} />
            <span>Tải file gốc</span>
          </button>
          {canCorrect && !isEditing && <button onClick={() => { setIsEditing(true); setSaveMessage(''); setSaveError(''); }} className="px-4 py-2 flex items-center gap-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors shadow-sm">
            <Pencil size={18} /><span>Chỉnh sửa thủ công</span>
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

      <div className="min-h-0 flex-1 space-y-6 overflow-y-auto pr-1">
        <div className="h-[min(72vh,760px)] min-h-[620px]">
          <OcrDocumentPreview documentId={document.id} fileType={document.fileType} ocr={ocr}
            fields={extractedFields} documentType={document.documentType} canCorrectFields={canCorrectExtractedFields}
            onCorrectField={handleCorrectExtractedField} />
        </div>

        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          <div className="bg-white rounded-2xl border border-blue-200 shadow-sm overflow-hidden p-5 bg-gradient-to-br from-blue-50 to-white">
            <div className="flex items-start justify-between mb-4">
              <div>
                <h3 className="text-sm font-semibold text-blue-800 uppercase tracking-wider">AI Phân Loại</h3>
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

          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5 flex-1">
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
        </div>
      </div>
    </div>
  );
};

export default DocumentDetail;

import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Download, Save } from 'lucide-react';
import clsx from 'clsx';
import StatusBadge from '../components/StatusBadge';
import classificationService from '../services/classificationService';
import documentService, { mapDocument } from '../services/documentService';
import invoiceService from '../services/invoiceService';
import type { ClassificationResponse } from '../services/classificationService';
import type { DocumentResponse, OCRResultResponse } from '../services/documentService';
import type { ExtractedFieldResponse } from '../services/documentService';
import OcrDocumentPreview from '../components/OcrDocumentPreview';
import type { InvoiceResponse } from '../services/invoiceService';

interface InvoiceFormData {
  supplier: string;
  taxCode: string;
  invoiceNo: string;
  address: string;
  subTotal: string;
  vatAmount: string;
}

const emptyInvoiceForm: InvoiceFormData = {
  supplier: '',
  taxCode: '',
  invoiceNo: '',
  address: '',
  subTotal: '',
  vatAmount: '',
};

const toInvoiceForm = (invoice: InvoiceResponse): InvoiceFormData => ({
  supplier: invoice.sellerName || '',
  taxCode: invoice.sellerTaxCode || '',
  invoiceNo: invoice.invoiceNumber || '',
  address: invoice.sellerAddress || '',
  subTotal: invoice.subtotal === null ? '' : String(invoice.subtotal),
  vatAmount: invoice.vatAmount === null ? '' : String(invoice.vatAmount),
});

const parseAmount = (value: string) => {
  if (!value.trim()) {
    return null;
  }

  const amount = Number(value)
  return Number.isNaN(amount) ? null : amount;
};

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
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
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
        setClassificationValue(classificationResult.value.category || '');
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
        const updatedInvoice = await invoiceService.updateInvoice(invoice.id, {
          documentId: document.id,
          invoiceNumber: invoiceForm.invoiceNo || null,
          invoiceDate: invoice.invoiceDate,
          sellerName: invoiceForm.supplier || null,
          sellerTaxCode: invoiceForm.taxCode || null,
          sellerAddress: invoiceForm.address || null,
          buyerName: invoice.buyerName,
          buyerTaxCode: invoice.buyerTaxCode,
          buyerAddress: invoice.buyerAddress,
          subtotal: parseAmount(invoiceForm.subTotal),
          vatAmount: parseAmount(invoiceForm.vatAmount),
          totalAmount: invoice.totalAmount,
          items: invoice.items,
        });
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
  const confidence = ocr?.confidence;

  const InputField = ({ label, value, field }: { label: string; value: string; field: keyof InvoiceFormData }) => (
    <div className="mb-4">
      <label className="block text-sm font-medium text-slate-700 mb-1">{label}</label>
      <input
        type="text"
        value={value}
        onChange={(event) => updateInvoiceForm(field, event.target.value)}
        className="w-full border border-slate-300 bg-white rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
      />
    </div>
  );

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
          <button onClick={handleSave} disabled={isSaving} className="px-4 py-2 flex items-center gap-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-60 transition-colors shadow-sm">
            <Save size={18} />
            <span>{isSaving ? 'Đang lưu...' : 'Lưu thay đổi'}</span>
          </button>
        </div>
      </div>

      {saveMessage && <div className="rounded-lg bg-emerald-50 border border-emerald-200 px-4 py-3 text-sm text-emerald-700">{saveMessage}</div>}
      {saveError && <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">{saveError}</div>}

      <div className="flex-1 grid grid-cols-1 lg:grid-cols-2 gap-6 min-h-0">
        <div className="bg-slate-800 rounded-2xl flex flex-col overflow-hidden border border-slate-300 shadow-sm relative">
          <div className="absolute top-4 right-4 bg-black/50 text-white px-3 py-1 rounded-full text-xs backdrop-blur-md">1 / 1</div>
          <div className="flex-1 flex items-center justify-center p-8 overflow-auto">
            <OcrDocumentPreview documentId={document.id} fileType={document.fileType} ocr={ocr} fields={extractedFields} />
          </div>
        </div>

        <div className="flex flex-col gap-4 overflow-y-auto pr-2">
          <div className="bg-white rounded-2xl border border-blue-200 shadow-sm overflow-hidden p-5 bg-gradient-to-br from-blue-50 to-white">
            <div className="flex items-start justify-between mb-4">
              <div>
                <h3 className="text-sm font-semibold text-blue-800 uppercase tracking-wider">AI Phân Loại</h3>
                <p className="text-sm text-slate-600 mt-1">Kết quả từ backend classification</p>
              </div>
              <div className="text-right">
                <div className={clsx('text-xl font-bold', confidence === null || confidence === undefined ? 'text-slate-500' : 'text-emerald-600')}>
                  {confidence === null || confidence === undefined ? 'N/A' : `${confidence}%`}
                </div>
                <div className="text-xs text-slate-500">Độ chính xác OCR</div>
              </div>
            </div>
            <label className="block text-xs font-medium text-slate-500 mb-1">Loại nghiệp vụ / Chi phí</label>
            <select
              value={classificationValue}
              onChange={(event) => setClassificationValue(event.target.value)}
              className="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-sm font-medium text-slate-800 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none"
            >
              <option value="">Chưa có dữ liệu phân loại</option>
              <option value="Chi phí văn phòng">Chi phí văn phòng</option>
              <option value="Chi phí vận chuyển">Chi phí vận chuyển</option>
              <option value="Chi phí tiếp khách">Chi phí tiếp khách</option>
              <option value="Chi phí nguyên vật liệu">Chi phí nguyên vật liệu</option>
              <option value="Chi phí dịch vụ">Chi phí dịch vụ</option>
              <option value="Tài sản">Tài sản</option>
              <option value="Khác">Khác</option>
            </select>
            {!classification && <p className="text-xs text-slate-500 mt-2">Backend chưa có bản ghi classification cho chứng từ này.</p>}
          </div>

          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5 flex-1">
            <div className="flex items-center justify-between mb-6">
              <h3 className="text-lg font-semibold text-slate-800">Dữ liệu invoice</h3>
              <span className="text-xs px-2.5 py-1 bg-slate-100 text-slate-600 rounded-md font-medium">API backend</span>
            </div>
            {!isInvoiceDocument(document.documentType) ? (
              <p className="text-sm text-slate-500">Không áp dụng cho loại chứng từ này.</p>
            ) : invoice ? (
              <div className="space-y-4">
                <InputField label="Nhà cung cấp" value={invoiceForm.supplier} field="supplier" />
                <InputField label="Mã số thuế" value={invoiceForm.taxCode} field="taxCode" />
                <InputField label="Số hóa đơn" value={invoiceForm.invoiceNo} field="invoiceNo" />
                <InputField label="Tổng tiền trước thuế" value={invoiceForm.subTotal} field="subTotal" />
                <InputField label="Tiền thuế VAT" value={invoiceForm.vatAmount} field="vatAmount" />
                <InputField label="Địa chỉ" value={invoiceForm.address} field="address" />
                <div className="grid grid-cols-2 gap-4 text-sm">
                  <div><span className="block text-xs font-medium text-slate-500 mb-1">Số điện thoại</span><span className="text-slate-500">Backend chưa cung cấp</span></div>
                  <div><span className="block text-xs font-medium text-slate-500 mb-1">Hình thức thanh toán</span><span className="text-slate-500">Backend chưa cung cấp</span></div>
                </div>
              </div>
            ) : (
              <p className="text-sm text-slate-500">Backend chưa có invoice cho chứng từ này.</p>
            )}
            {ocr && <div className="mt-6 border-t border-slate-200 pt-4 text-sm text-slate-600">OCR raw text đã nhận từ backend: {ocr.rawText || 'Chưa có nội dung'}</div>}
          </div>
        </div>
      </div>
    </div>
  );
};

export default DocumentDetail;

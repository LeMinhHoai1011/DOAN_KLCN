import { formatDateTime, formatDate } from './dateTime.ts'
const actions: Record<string, string> = {
  UPLOAD: 'Tải chứng từ lên', FILE_VERSION_UPLOAD: 'Tải phiên bản file mới', DOCUMENT_UPDATE: 'Cập nhật thông tin chứng từ',
  OCR_EXTRACT: 'Lưu kết quả OCR', OCR_EDIT: 'Chỉnh sửa văn bản OCR', AI_EXTRACT: 'Lưu dữ liệu trích xuất và phân loại AI', AI_REVIEW_REQUIRED: 'AI yêu cầu kiểm tra dữ liệu',
  INVOICE_CREATE: 'Tạo dữ liệu hóa đơn', INVOICE_UPDATE: 'Chỉnh sửa dữ liệu hóa đơn', INVOICE_DELETE: 'Gỡ dữ liệu hóa đơn (giữ snapshot)', FIELD_CORRECTION: 'Chỉnh sửa trường trích xuất',
  CLASSIFICATION_UPDATE: 'Cập nhật phân loại', CLASSIFICATION_CORRECTION: 'Điều chỉnh phân loại', CLASSIFICATION_APPROVE: 'Xác nhận phân loại', CLASSIFICATION_REVIEW: 'Yêu cầu kiểm tra phân loại',
  RECONCILIATION: 'Chạy đối soát', RECONCILIATION_DECISION: 'Ghi nhận quyết định đối soát', PROCESS_START: 'Bắt đầu xử lý OCR/AI', REPROCESS: 'Xử lý lại OCR/AI', PROCESS_COMPLETED: 'Hoàn tất xử lý', PROCESS_FAILED: 'Kết thúc lượt xử lý cần kiểm tra',
  WORKFLOW_SUBMIT: 'Gửi chứng từ', WORKFLOW_START_REVIEW: 'Bắt đầu kiểm duyệt', WORKFLOW_APPROVE: 'Phê duyệt chứng từ', WORKFLOW_REJECT: 'Từ chối chứng từ', WORKFLOW_REQUEST_INFO: 'Yêu cầu bổ sung thông tin', WORKFLOW_RESUBMIT: 'Gửi lại chứng từ',
  SUBMIT: 'Gửi chứng từ', START_REVIEW: 'Bắt đầu kiểm duyệt', APPROVE: 'Phê duyệt chứng từ', REJECT: 'Từ chối chứng từ', REQUEST_INFO: 'Yêu cầu bổ sung thông tin', RESUBMIT: 'Gửi lại chứng từ',
  PAYMENT_CREATE: 'Tạo phiếu thu/chi', PAYMENT_UPDATE: 'Sửa phiếu thu/chi', PAYMENT_CANCEL: 'Hủy phiếu thu/chi',
}
export const archiveActionLabel = (code: string) => actions[code] ?? (code.startsWith('AI_DOCUMENT') ? 'Bước xử lý OCR/AI' : code.startsWith('CLASSIFICATION') ? 'Xử lý phân loại' : code.startsWith('OCR') ? 'Xử lý OCR' : 'Sự kiện xử lý chứng từ')
export const storageStatusLabel = (status: string) => ({ AVAILABLE: 'File hiện có trong kho lưu trữ', MISSING: 'Không tìm thấy file trong kho lưu trữ', UNAVAILABLE: 'Chưa kiểm tra được kết nối kho lưu trữ', NOT_LINKED: 'Chưa có liên kết file lưu trữ' } as Record<string,string>)[status] ?? 'Chưa xác định'
export const archiveActorLabel = (id: number | null) => id == null ? 'Chưa ghi nhận người thực hiện' : `Người thực hiện #${id}`
export const archiveTimeLabel = (at: string | null) => at ? formatDateTime(at) : 'Chưa ghi nhận thời điểm'
export const archiveBytes = (bytes: number | null) => bytes == null ? 'Chưa xác định' : `${new Intl.NumberFormat('vi-VN').format(bytes)} byte`
export interface SnapshotChange { path: string; before: string; after: string }
const flatten = (value: unknown, prefix = '', output: Record<string,string> = {}) => {
  if (value !== null && typeof value === 'object') {
    for (const [key, child] of Object.entries(value)) flatten(child, prefix ? `${prefix}.${key}` : key, output)
  } else output[prefix || 'value'] = value == null ? '—' : String(value)
  return output
}
export const snapshotChanges = (before: unknown, after: unknown): SnapshotChange[] => {
  const a = flatten(before), b = flatten(after)
  return [...new Set([...Object.keys(a), ...Object.keys(b)])].filter(key => a[key] !== b[key] && key !== 'ocr.layoutJson' && key !== 'schemaVersion').sort()
    .map(key => ({ path: key, before: a[key] ?? '—', after: b[key] ?? '—' }))
}
const fields: Record<string,string> = { aiProposal:'Đề xuất AI của lượt xử lý (không tự ghi đè dữ liệu xác nhận)', document:'Chứng từ', invoice:'Hóa đơn', ocr:'OCR', classification:'Phân loại', items:'Sản phẩm', fields:'Trường trích xuất', id:'ID', originalFileName:'Tên file', fileType:'MIME', fileSize:'Dung lượng', documentType:'Loại chứng từ', status:'Trạng thái', reviewStatus:'Kiểm duyệt', createdAt:'Thời điểm tạo', updatedAt:'Thời điểm cập nhật', invoiceNumber:'Số hóa đơn', invoiceDate:'Ngày hóa đơn', sellerName:'Người bán', buyerName:'Người mua', totalAmount:'Tổng thanh toán', subtotal:'Tổng trước thuế', vatAmount:'Thuế GTGT', productName:'Tên sản phẩm', quantity:'Số lượng', unit:'Đơn vị', unitPrice:'Đơn giá', amount:'Thành tiền', taxRate:'Thuế suất', taxAmount:'Tiền thuế', fieldName:'Tên trường', fieldValue:'Giá trị AI', effectiveValue:'Giá trị sử dụng', source:'Nguồn', confidence:'Độ tin cậy', rawText:'Văn bản OCR', fileVersionId:'Phiên bản file tại thời điểm ghi nhận', modelName:'Nhà cung cấp AI', modelVersion:'Model/phiên bản', predictedLabel:'Đề xuất AI', category:'Danh mục kế toán', aiGenerated:'Do AI tạo', reconciliations:'Đối soát', latestJob:'Lượt xử lý', startedAt:'Bắt đầu', completedAt:'Kết thúc' }
export const snapshotFieldLabel = (key: string) => key.split('.').map(k => /^\d+$/.test(k) ? `Dòng ${Number(k)+1}` : fields[k] ?? k.replace(/([a-z])([A-Z])/g,'$1 $2')).join(' / ')
export const snapshotValueLabel = (path: string, value: string) => {
  if (/(?:invoiceDate|signDate|transactionDate|contractDate|voucherDate|receiptDate)$/.test(path)) return formatDate(value,value)
  if (/(?:createdAt|updatedAt|startedAt|completedAt)$/.test(path)) return formatDateTime(value,value)
  if (!/(?:status|reviewStatus)$/.test(path)) return value
  return ({ UPLOADED:'Đã tải lên', PROCESSING:'Đang xử lý', PROCESSED:'Đã xử lý', NEED_REVIEW:'Cần kiểm tra', COMPLETED:'Hoàn tất', APPROVED:'Đã duyệt', REJECTED:'Đã từ chối', CORRECTED:'Đã chỉnh sửa', PENDING:'Đang chờ', CLASSIFIED:'Đã phân loại', ACCEPTED:'Đã xác nhận', SUCCESS:'Thành công', FAILED:'Thất bại', RUNNING:'Đang chạy', MATCHED:'Khớp', MISMATCH:'Sai lệch', INSUFFICIENT_DATA:'Chưa đủ dữ liệu', ACTIVE:'Đang hoạt động', CANCELLED:'Đã hủy' } as Record<string,string>)[value] ?? value
}

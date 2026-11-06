import type { Comparison, Label, LabelState, LiveSample, SampleStatus } from '../services/liveEvaluationService'

export const fieldLabels: Record<string, string> = {
  invoiceNumber: 'Số hóa đơn', invoiceSeries: 'Ký hiệu', invoiceDate: 'Ngày hóa đơn', sellerName: 'Tên người bán', sellerTaxCode: 'MST người bán',
  sellerAddress: 'Địa chỉ người bán', buyerName: 'Tên người mua', buyerTaxCode: 'MST người mua', buyerAddress: 'Địa chỉ người mua',
  subtotal: 'Tổng trước thuế', vatAmount: 'Tiền thuế', totalAmount: 'Tổng thanh toán', paymentMethod: 'Hình thức thanh toán',
}
export const itemColumns = [
  { key: 'productName', label: 'Tên hàng' }, { key: 'unit', label: 'ĐVT' }, { key: 'quantity', label: 'Số lượng' }, { key: 'unitPrice', label: 'Đơn giá' },
  { key: 'taxRate', label: 'Thuế suất (%)' }, { key: 'taxAmount', label: 'Tiền thuế' }, { key: 'amount', label: 'Thành tiền' },
]
export const stateLabels: Record<LabelState, string> = { UNCHECKED: 'Chưa kiểm tra', PRESENT: 'Có trên chứng từ', ABSENT: 'Không có trên chứng từ', UNREADABLE: 'Không đọc được' }
export const statusLabels: Record<SampleStatus, string> = { SELECTED: 'Chưa gán nhãn', DRAFT: 'Đang gán nhãn', CONFIRMED: 'Đã xác nhận' }
export const outcomeLabels: Record<Comparison['outcome'], string> = {
  CORRECT: 'Đúng', INCORRECT: 'Sai', MISSING: 'AI bỏ sót', SPURIOUS: 'AI thừa', EXCLUDED: 'Không tính', NOT_APPLICABLE: 'Không áp dụng',
}

/** The tax-rate input is a percentage; storing the sign keeps 1% from being read as the fraction 1. */
export const toStoredRow = (cells: Record<string, string>) => {
  const row = Object.fromEntries(Object.entries(cells).map(([key, value]) => [key, value.trim()]).filter(([, value]) => value))
  return row.taxRate && !row.taxRate.endsWith('%') ? { ...row, taxRate: `${row.taxRate}%` } : row
}
export const toEditableRow = (row: Record<string, string>) => row.taxRate ? { ...row, taxRate: row.taxRate.replace(/\s*%$/, '') } : row

const needsWork = (label: Label) => label.state === 'UNCHECKED' || (label.state === 'PRESENT' && !label.value?.trim())

/** Names what still blocks confirmation, mirroring the backend check. */
export const missingLabels = (sample: Pick<LiveSample, 'documentType' | 'fields' | 'category' | 'itemsState' | 'items'>) => [
  ...(sample.documentType ? [] : ['Loại chứng từ']),
  ...Object.entries(sample.fields).filter(([, label]) => needsWork(label)).map(([key]) => fieldLabels[key] ?? key),
  ...(needsWork(sample.category) ? ['Nhóm chi phí'] : []),
  ...(sample.itemsState === 'UNCHECKED' || (sample.itemsState === 'PRESENT' && sample.items.length === 0) ? ['Bảng hàng hóa'] : []),
]

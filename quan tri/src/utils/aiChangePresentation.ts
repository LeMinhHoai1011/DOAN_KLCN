import type { AiChange, AiChangeKind } from '../services/evaluationService'

const labels: Record<string, string> = {
  predictedLabel: 'Loại chứng từ', category: 'Nhóm chi phí', accountingAccount: 'Tài khoản kế toán',
  invoiceNumber: 'Số hóa đơn', invoiceSeries: 'Ký hiệu', invoiceDate: 'Ngày lập', signDate: 'Ngày ký',
  sellerName: 'Tên người bán', sellerTaxCode: 'MST người bán', sellerAddress: 'Địa chỉ người bán', sellerPhone: 'SĐT người bán',
  buyerName: 'Tên người mua', buyerTaxCode: 'MST người mua', buyerAddress: 'Địa chỉ người mua',
  subtotal: 'Tổng trước thuế', vatAmount: 'Tiền thuế', totalAmount: 'Tổng thanh toán',
  paymentMethod: 'Hình thức thanh toán', amountInWords: 'Số tiền bằng chữ', taxAuthorityCode: 'Mã cơ quan thuế',
  'items.productName': 'Tên hàng', 'items.quantity': 'Số lượng', 'items.unit': 'ĐVT', 'items.unitPrice': 'Đơn giá',
  'items.taxRate': 'Thuế suất', 'items.taxAmount': 'Tiền thuế dòng', 'items.amount': 'Thành tiền',
}

export const aiFieldLabel = (field: string) => labels[field] ?? field
export const aiChangeLabel = (change: Pick<AiChange, 'field' | 'row'>) =>
  change.row == null ? aiFieldLabel(change.field) : `Dòng ${change.row} · ${aiFieldLabel(change.field)}`
export const aiChangeKindLabel: Record<AiChangeKind, string> = { WRONG: 'AI sai', MISSING: 'AI thiếu', EXTRA: 'AI thừa' }
export const aiChangeValue = (value: string | null) => value ?? '(trống)'

/** A pending document proves nothing yet: only an accountant's approval confirms the untouched values. */
export const aiChangeSummary = (compared: number, correct: number, changes: number, approved: boolean) => {
  if (!compared) return 'Chưa có dữ liệu để so sánh.'
  const counted = `Kế toán sửa ${changes}/${compared} giá trị`
  return approved ? `${counted}, giữ nguyên ${correct}.` : `${counted}. Chưa duyệt nên kết quả chưa được tính vào độ chính xác.`
}

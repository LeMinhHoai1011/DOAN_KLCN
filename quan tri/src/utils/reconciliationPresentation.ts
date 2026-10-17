import type { ReconciliationStatus } from '../services/reconciliationService'

export const reconciliationMoney = (value: number | null | undefined) =>
  value == null ? '—' : Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 2 })

export const reconciliationStatusLabel = (status: string | null | undefined) => ({
  MATCH: 'Khớp',
  MATCHED: 'Khớp',
  MISMATCH: 'Sai lệch',
  PASS: 'Đạt',
  FAIL: 'Không đạt',
  INSUFFICIENT_DATA: 'Chưa đủ dữ liệu',
  NEED_REVIEW: 'Cần kiểm tra',
} as Record<string, string>)[status?.trim().toUpperCase() ?? ''] ?? 'Chưa xác định'

export const reconciliationStatusClass = (status: string | null | undefined) => {
  const normalized = status?.trim().toUpperCase()
  if (['MATCH', 'MATCHED', 'PASS'].includes(normalized ?? '')) return 'si-chip border-parsed/30 bg-parsed-surface text-parsed'
  if (['MISMATCH', 'FAIL'].includes(normalized ?? '')) return 'si-chip border-failed/30 bg-failed-surface text-failed'
  return 'si-chip border-review-surface bg-review-surface text-review'
}

export const hasInsufficientReconciliationData = (statuses: ReconciliationStatus[]) =>
  statuses.includes('INSUFFICIENT_DATA')

const ruleNames: Record<string, string> = {
  ITEM_AMOUNT: 'Thành tiền sản phẩm',
  ITEM_VAT: 'Thuế GTGT sản phẩm',
  SUBTOTAL: 'Tổng tiền trước thuế',
  VAT_TOTAL: 'Tổng thuế GTGT',
  TOTAL: 'Tổng tiền thanh toán',
  ITEMS_PRESENT: 'Thông tin sản phẩm',
}
const ruleDescriptions: Record<string, string> = {
  ITEM_AMOUNT: 'Kiểm tra thành tiền bằng số lượng nhân với đơn giá.',
  ITEM_VAT: 'Kiểm tra tiền thuế GTGT theo thành tiền và thuế suất.',
  SUBTOTAL: 'So sánh tổng thành tiền các sản phẩm với tổng tiền trước thuế.',
  VAT_TOTAL: 'So sánh tổng thuế GTGT của các sản phẩm với tổng thuế trên hóa đơn, hỗ trợ nhiều mức thuế suất.',
  TOTAL: 'Kiểm tra tổng tiền thanh toán bằng tổng tiền trước thuế cộng thuế GTGT.',
  ITEMS_PRESENT: 'Kiểm tra chứng từ có thông tin sản phẩm để thực hiện đối soát.',
}
const normalizedRule = (code: string | null | undefined) => code?.trim().toUpperCase() ?? ''
export const reconciliationRuleLabel = (code: string | null | undefined) => {
  const normalized = normalizedRule(code)
  const item = /^ITEM_(\d+)_(AMOUNT|VAT)$/.exec(normalized)
  if (item) return `${ruleNames[`ITEM_${item[2]}`]} ${item[1]}`
  return ruleNames[normalized] ?? 'Kiểm tra đối soát bổ sung'
}
export const reconciliationRuleDescription = (code: string | null | undefined) =>
  ruleDescriptions[normalizedRule(code).replace(/^ITEM_\d+_/, 'ITEM_')] ?? 'Kiểm tra tính nhất quán của dữ liệu hóa đơn/chứng từ.'

/** Display only: never mutate API comments, rule codes, amounts or decisions. */
export const reconciliationCommentLabel = (comment: string | null | undefined) => {
  if (!comment?.trim()) return null
  const rounding = /rounding\s*=\s*HALF_UP\s+scale\s*=\s*2/gi
  // Known generated explanations are rendered separately using the rule description above.
  const generated = /Thành tiền dòng = số lượng|VAT dòng theo thuế suất|Tổng dòng so với tổng trước thuế|Tổng VAT dòng|Tổng trước thuế \+ VAT so với thanh toán/.test(comment)
  if (generated) {
    const notes = []
    if (/\bINSUFFICIENT_DATA\b/.test(comment)) notes.push('Chưa đủ dữ liệu để đối soát.')
    if (rounding.test(comment)) notes.push('Làm tròn đến 2 chữ số thập phân.')
    return notes.join(' ') || null
  }
  return comment
    .replace(rounding, 'Làm tròn đến 2 chữ số thập phân')
    .replace(/\b(?:ITEM_\d+_(?:AMOUNT|VAT)|ITEM_AMOUNT|ITEM_VAT|SUBTOTAL|VAT_TOTAL|TOTAL|ITEMS_PRESENT)\b/g, code => reconciliationRuleLabel(code))
    .replace(/\b(?:MATCHED|MATCH|MISMATCH|PASS|FAIL|INSUFFICIENT_DATA|NEED_REVIEW)\b/g, code => reconciliationStatusLabel(code))
    .replace(/\bManual decision\b/gi, 'Quyết định kiểm tra thủ công')
    .replace(/\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b/g, 'Nội dung đối soát bổ sung')
    .replace(/\brounding\s*=\s*[^;]+/gi, 'Thông tin làm tròn cần kiểm tra')
    .trim()
}

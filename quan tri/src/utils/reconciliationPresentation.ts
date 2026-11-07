import type { Reconciliation, ReconciliationStatus } from '../services/reconciliationService'
import type { InvoiceResponse } from '../services/invoiceService'
import { invoiceQuantity, roundToDong } from './invoiceNumber.ts'

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
  ACCEPTED: 'Đã chấp nhận',
} as Record<string, string>)[status?.trim().toUpperCase() ?? ''] ?? 'Chưa xác định'

export const reconciliationStatusClass = (status: string | null | undefined) => {
  const normalized = status?.trim().toUpperCase()
  if (['MATCH', 'MATCHED', 'PASS'].includes(normalized ?? '')) return 'si-chip border-parsed/30 bg-parsed-surface text-parsed'
  if (normalized === 'ACCEPTED') return 'si-chip border-brand-200 bg-brand-50 text-brand-700'
  if (['MISMATCH', 'FAIL'].includes(normalized ?? '')) return 'si-chip border-failed/30 bg-failed-surface text-failed'
  return 'si-chip border-review-surface bg-review-surface text-review'
}

/** History is kept per run, newest first; only the latest run decides the document's state. */
export const latestReconciliationRun = (results: Reconciliation[]) => results.filter(result => result.batchId === results[0].batchId)

/** A matched check needs no decision; an accepted one can only be reopened. */
export const reconciliationDecision = (status: ReconciliationStatus) => status === 'MATCHED' ? null : status === 'ACCEPTED' ? 'REOPEN' : 'ACCEPT'

/** Mirrors the backend approval gate for an early explanation; the backend remains the authority. */
export const approvalBlockReason = (state: { reconciliation: string; reconciliationStale: boolean } | null, hasInvoice: boolean) => {
  if (!state || !hasInvoice) return null
  if (state.reconciliation === 'NOT_RUN') return 'Cần chạy đối soát trước khi duyệt.'
  if (state.reconciliationStale) return 'Hóa đơn đã thay đổi sau lần đối soát gần nhất, cần chạy lại đối soát.'
  return ['MATCHED', 'ACCEPTED'].includes(state.reconciliation) ? null : 'Còn kết quả đối soát chưa xử lý. Hãy sửa dữ liệu rồi chạy lại, hoặc chấp nhận kèm lý do.'
}

/** Server messages may start with a machine code ("RECONCILIATION_REQUIRED: ..."). */
export const withoutErrorCode = (message: string) => message.replace(/^[A-Z][A-Z0-9_]{2,63}:\s*/, '')

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

type InvoiceFigures = Pick<InvoiceResponse, 'items' | 'subtotal' | 'vatAmount'>
const MAX_SHOWN_TERMS = 6
const percent = (rate: number) => Number(((Math.abs(rate) <= 1 ? rate : rate / 100) * 100).toFixed(4))
const sumExpression = (values: number[]) => values.length > MAX_SHOWN_TERMS
  ? `Tổng ${values.length} dòng` : values.map(reconciliationMoney).join(' + ')

/** Same formulas as InvoiceReconciliationRules; recomputed values only explain, the API result stays the authority. */
const calculationSteps = (type: string, invoice: InvoiceFigures) => {
  const item = /^ITEM_(\d+)_(AMOUNT|VAT)$/.exec(type)
  if (item) {
    const row = invoice.items[Number(item[1]) - 1]
    if (!row) return null
    if (item[2] === 'AMOUNT') return row.quantity == null || row.unitPrice == null ? null
      : { expression: `${invoiceQuantity(row.quantity)} × ${reconciliationMoney(row.unitPrice)}`, result: row.quantity * row.unitPrice, toDong: true }
    if (row.amount == null || row.taxRate == null) return null
    const rate = percent(row.taxRate)
    return { expression: `${reconciliationMoney(row.amount)} × ${reconciliationMoney(rate)}%`, result: row.amount * rate / 100, toDong: true }
  }
  if (type === 'SUBTOTAL' || type === 'VAT_TOTAL') {
    const values = invoice.items.map(row => type === 'SUBTOTAL' ? row.amount : row.taxAmount)
    if (!values.length || values.some(value => value == null)) return null
    const numbers = values as number[]
    return { expression: sumExpression(numbers), result: numbers.reduce((total, value) => total + value, 0) }
  }
  if (type === 'TOTAL') return invoice.subtotal == null || invoice.vatAmount == null ? null
    : { expression: `${reconciliationMoney(invoice.subtotal)} + ${reconciliationMoney(invoice.vatAmount)}`, result: invoice.subtotal + invoice.vatAmount }
  return null
}

/** Null when the invoice no longer reproduces the stored result (edited after the run), so a stale formula is never shown. */
export const reconciliationCalculation = (item: Reconciliation, invoice: InvoiceFigures | null | undefined) => {
  if (!invoice || item.actualAmount == null) return null
  const steps = calculationSteps(normalizedRule(item.type), invoice)
  if (!steps) return null
  const exact = Math.round(steps.result * 100) / 100
  const final = 'toDong' in steps ? roundToDong(exact) : exact
  if (Math.abs(final - item.actualAmount) > 0.011) return null
  return exact === final ? `${steps.expression} = ${reconciliationMoney(final)} đ`
    : `${steps.expression} = ${reconciliationMoney(exact)} → làm tròn ${reconciliationMoney(final)} đ`
}

export const reconciliationDifferenceCalculation = (item: Reconciliation) =>
  item.actualAmount == null || item.expectedAmount == null || item.differenceAmount == null ? null
    : `${reconciliationMoney(item.actualAmount)} − ${reconciliationMoney(item.expectedAmount)} = ${reconciliationMoney(item.differenceAmount)} đ`

const WHOLE_DONG_NOTE = 'Làm tròn đến đồng: phần lẻ từ 0,5 trở lên được làm tròn lên.'

/** A matched check can still show a small difference; say why it does not count as a mismatch. */
export const reconciliationRoundingNote = (item: Reconciliation) =>
  item.status === 'MATCHED' && item.differenceAmount ? 'Lệch dưới 1 đ do làm tròn, được tính là khớp.' : null

/** Display only: never mutate API comments, rule codes, amounts or decisions. */
export const reconciliationCommentLabel = (comment: string | null | undefined) => {
  if (!comment?.trim()) return null
  const rounding = /rounding\s*=\s*HALF_UP\s+scale\s*=\s*2/gi
  const wholeDong = /rounding\s*=\s*HALF_UP\s+scale\s*=\s*0/gi
  // Known generated explanations are rendered separately using the rule description above.
  const generated = /Thành tiền dòng = số lượng|VAT dòng theo thuế suất|Tổng dòng so với tổng trước thuế|Tổng VAT dòng|Tổng trước thuế \+ VAT so với thanh toán/.test(comment)
  if (generated) {
    const notes = []
    if (/\bINSUFFICIENT_DATA\b/.test(comment)) notes.push('Chưa đủ dữ liệu để đối soát.')
    if (rounding.test(comment)) notes.push('Làm tròn đến 2 chữ số thập phân.')
    if (wholeDong.test(comment)) notes.push(WHOLE_DONG_NOTE)
    return notes.join(' ') || null
  }
  return comment
    .replace(rounding, 'Làm tròn đến 2 chữ số thập phân')
    .replace(wholeDong, WHOLE_DONG_NOTE)
    .replace(/\b(?:ITEM_\d+_(?:AMOUNT|VAT)|ITEM_AMOUNT|ITEM_VAT|SUBTOTAL|VAT_TOTAL|TOTAL|ITEMS_PRESENT)\b/g, code => reconciliationRuleLabel(code))
    .replace(/\b(?:MATCHED|MATCH|MISMATCH|PASS|FAIL|INSUFFICIENT_DATA|NEED_REVIEW|ACCEPTED)\b/g, code => reconciliationStatusLabel(code))
    .replace(/\bManual decision\b/gi, 'Quyết định kiểm tra thủ công')
    .replace(/\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b/g, 'Nội dung đối soát bổ sung')
    .replace(/\brounding\s*=\s*[^;]+/gi, 'Thông tin làm tròn cần kiểm tra')
    .trim()
}

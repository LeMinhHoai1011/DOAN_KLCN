import type { RetailAdjustment, RetailInvoiceDetails } from '../services/invoiceService'
import { invoiceNumberInput, parseInvoiceNumber } from './invoiceNumber.ts'

export const RETAIL_INVOICE = 'RETAIL_INVOICE'

/** Document type code of a retail/POS bill (code wins over the legacy free-text type). */
export const isRetailDocument = (document: { documentTypeCode?: string | null; documentType?: string | null } | null | undefined) =>
  (document?.documentTypeCode ?? document?.documentType ?? '').trim().toUpperCase() === RETAIL_INVOICE

export type Treatment = RetailAdjustment['treatment']
export type LineBasis = RetailInvoiceDetails['lineAmountBasis']

export const treatmentLabels: Record<Treatment, string> = {
  INCLUDED: 'Đã gồm trong tổng', SEPARATE: 'Cộng/trừ riêng', NOT_PRESENT: 'Không có', UNKNOWN: 'Chưa rõ',
}
export const lineBasisLabels: Record<LineBasis, string> = {
  BEFORE_DISCOUNT: 'Trước giảm giá', AFTER_DISCOUNT: 'Sau giảm giá', UNKNOWN: 'Chưa rõ',
}
export const adjustmentLabels = { billDiscount: 'Giảm giá cả hóa đơn', tax: 'Thuế', fees: 'Phí (phục vụ, ship...)' } as const
export type AdjustmentKey = keyof typeof adjustmentLabels

/** Editable text form of the retail details; amounts are typed in Vietnamese format (1.234,5). */
export type RetailDraft = {
  transactionTime: string
  lineAmountBasis: LineBasis
  /** false = not verified yet (null on the server); true = list below is complete (rows not listed have no discount). */
  lineDiscountsChecked: boolean
  lineDiscounts: { rowNumber: string; amount: string }[]
  billDiscount: { treatment: Treatment; amount: string }
  tax: { treatment: Treatment; amount: string }
  fees: { treatment: Treatment; amount: string }
  tenderedAmount: string
  changeAmount: string
}

const unknownAdjustment = { treatment: 'UNKNOWN' as Treatment, amount: '' }

export const toRetailDraft = (details: RetailInvoiceDetails | null | undefined): RetailDraft => ({
  transactionTime: details?.transactionTime ?? '',
  lineAmountBasis: details?.lineAmountBasis ?? 'UNKNOWN',
  lineDiscountsChecked: details?.lineDiscounts != null,
  lineDiscounts: (details?.lineDiscounts ?? []).map(item => ({ rowNumber: String(item.rowNumber), amount: invoiceNumberInput(item.amount) })),
  billDiscount: details?.billDiscount ? { treatment: details.billDiscount.treatment ?? 'UNKNOWN', amount: invoiceNumberInput(details.billDiscount.amount) } : unknownAdjustment,
  tax: details?.tax ? { treatment: details.tax.treatment ?? 'UNKNOWN', amount: invoiceNumberInput(details.tax.amount) } : unknownAdjustment,
  fees: details?.fees ? { treatment: details.fees.treatment ?? 'UNKNOWN', amount: invoiceNumberInput(details.fees.amount) } : unknownAdjustment,
  tenderedAmount: invoiceNumberInput(details?.tenderedAmount ?? null),
  changeAmount: invoiceNumberInput(details?.changeAmount ?? null),
})

const amount = (raw: string, label: string, errors: string[]) => {
  try {
    const value = parseInvoiceNumber(raw)
    if (value != null && value < 0) { errors.push(`${label}: không được âm.`); return null }
    return value
  } catch {
    errors.push(`${label}: số tiền không hợp lệ.`); return null
  }
}

/**
 * Draft → request, with the same rules the server applies before saving (RetailInvoiceRules.problems):
 * every "chưa rõ" must be decided, line discounts must be checked, rows must exist and not repeat.
 */
export const fromRetailDraft = (draft: RetailDraft, rowCount: number): { details: RetailInvoiceDetails | null; errors: string[] } => {
  const errors: string[] = []
  const time = draft.transactionTime.trim()
  if (time && !/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(time)) errors.push('Giờ giao dịch phải theo dạng HH:mm hoặc HH:mm:ss.')
  if (draft.lineAmountBasis === 'UNKNOWN') errors.push('Chọn thành tiền dòng là trước hay sau giảm giá.')
  let lineDiscounts: RetailInvoiceDetails['lineDiscounts'] = null
  if (!draft.lineDiscountsChecked) errors.push('Xác nhận đã kiểm tra giảm giá từng dòng.')
  else {
    const seen = new Set<number>()
    lineDiscounts = []
    for (const line of draft.lineDiscounts) {
      const row = Number(line.rowNumber)
      if (!Number.isInteger(row) || row < 1 || row > rowCount) { errors.push(`Giảm giá dòng: dòng ${line.rowNumber || '?'} không có trong bảng hàng hóa.`); continue }
      if (seen.has(row)) { errors.push(`Giảm giá dòng: dòng ${row} bị nhập hai lần.`); continue }
      seen.add(row)
      const value = amount(line.amount, `Giảm giá dòng ${row}`, errors)
      if (value == null) { if (!line.amount.trim()) errors.push(`Giảm giá dòng ${row}: thiếu số tiền.`); continue }
      lineDiscounts.push({ rowNumber: row, amount: value })
    }
  }
  const adjustment = (key: AdjustmentKey): RetailAdjustment => {
    const item = draft[key], label = adjustmentLabels[key]
    if (item.treatment === 'UNKNOWN') errors.push(`${label}: chọn đã gồm, cộng/trừ riêng hay không có.`)
    const value = item.treatment === 'NOT_PRESENT' ? null : amount(item.amount, label, errors)
    if (item.treatment === 'SEPARATE' && value == null && !errors.some(error => error.startsWith(label))) errors.push(`${label}: cần số tiền khi cộng/trừ riêng.`)
    return { treatment: item.treatment, amount: value }
  }
  const details: RetailInvoiceDetails = {
    transactionTime: time || null,
    lineAmountBasis: draft.lineAmountBasis,
    lineDiscounts,
    billDiscount: adjustment('billDiscount'), tax: adjustment('tax'), fees: adjustment('fees'),
    tenderedAmount: amount(draft.tenderedAmount, 'Tiền khách đưa', errors),
    changeAmount: amount(draft.changeAmount, 'Tiền thối lại', errors),
  }
  return { details: errors.length ? null : details, errors }
}

/** Server messages look like "RETAIL_CODE: text; RETAIL_CODE: text" — keep the Vietnamese text only. */
export const retailServerErrors = (message: string) =>
  message.split(/;\s*(?=RETAIL_)/).map(part => part.replace(/^RETAIL_[A-Z_]+:\s*/, '').trim()).filter(Boolean)

export const retailMoney = (value: number | null | undefined) =>
  value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(value)} đ`

export const adjustmentSummary = (item: RetailAdjustment | null | undefined) => {
  if (!item) return treatmentLabels.UNKNOWN
  const label = treatmentLabels[item.treatment ?? 'UNKNOWN']
  return item.amount != null && item.treatment !== 'NOT_PRESENT' ? `${label} · ${retailMoney(item.amount)}` : label
}

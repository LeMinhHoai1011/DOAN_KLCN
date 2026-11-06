import type { InvoiceItem } from '../services/invoiceService'
import { invoiceNumberInput, parseInvoiceNumber } from './invoiceNumber.ts'

export interface InvoiceItemDraft {
  key: number; productName: string; unit: string; quantity: string
  unitPrice: string; taxRate: string; taxAmount: string; amount: string
}

export type InvoiceItemPayload = Omit<InvoiceItem, 'id'>

/** Same tolerance as backend reconciliation, so a hint here means the check will not match. */
const TOLERANCE = 0.01
let nextKey = 1

/** Stored rates may be fractions (0.1) or percentages (10); the backend reads values up to 1 as fractions. */
const percentInput = (value: number | null) => value == null ? ''
  : invoiceNumberInput(Math.round((Math.abs(value) <= 1 ? value * 100 : value) * 100) / 100)

export const emptyItemDraft = (): InvoiceItemDraft => ({
  key: nextKey++, productName: '', unit: '', quantity: '', unitPrice: '', taxRate: '', taxAmount: '', amount: '',
})

export const toItemDrafts = (items: InvoiceItem[]): InvoiceItemDraft[] => items.map(item => ({
  key: nextKey++, productName: item.productName ?? '', unit: item.unit ?? '', quantity: invoiceNumberInput(item.quantity),
  unitPrice: invoiceNumberInput(item.unitPrice), taxRate: percentInput(item.taxRate),
  taxAmount: invoiceNumberInput(item.taxAmount), amount: invoiceNumberInput(item.amount),
}))

const comparable = (drafts: InvoiceItemDraft[]) => JSON.stringify(drafts.map(({ key: _key, ...draft }) => draft))
export const itemDraftsChanged = (drafts: InvoiceItemDraft[], items: InvoiceItem[]) => comparable(drafts) !== comparable(toItemDrafts(items))

const parsed = (raw: string) => { try { return parseInvoiceNumber(raw) } catch { return null } }

/** Throws a message naming the row and field; negative money is kept for discount rows. */
export const toItemPayloads = (drafts: InvoiceItemDraft[]): InvoiceItemPayload[] => drafts.map((draft, index) => {
  const row = `Dòng ${index + 1}`
  const number = (raw: string, label: string) => {
    try { return parseInvoiceNumber(raw) }
    catch (error) { throw new Error(`${row} - ${label}: ${(error as Error).message}`) }
  }
  const productName = draft.productName.trim()
  if (!productName) throw new Error(`${row}: Tên hàng hóa không được để trống.`)
  const quantity = number(draft.quantity, 'Số lượng')
  if (quantity != null && quantity < 0) throw new Error(`${row} - Số lượng: Không được nhỏ hơn 0.`)
  const percent = number(draft.taxRate, 'Thuế suất')
  if (percent != null && (percent < 0 || percent > 100 || !Number.isInteger(percent)))
    throw new Error(`${row} - Thuế suất: Nhập số phần trăm nguyên từ 0 đến 100.`)
  return {
    productName, unit: draft.unit.trim() || null, quantity, unitPrice: number(draft.unitPrice, 'Đơn giá'),
    // A fraction stays unambiguous for 1%, which the backend would otherwise read as 100%.
    taxRate: percent == null ? null : percent / 100,
    taxAmount: number(draft.taxAmount, 'Tiền thuế'), amount: number(draft.amount, 'Thành tiền'),
  }
})

/** Hints only: values are never recalculated for the accountant. */
export const itemDraftHints = (draft: InvoiceItemDraft) => {
  const hints: string[] = []
  const quantity = parsed(draft.quantity), unitPrice = parsed(draft.unitPrice), amount = parsed(draft.amount)
  const percent = parsed(draft.taxRate), taxAmount = parsed(draft.taxAmount)
  if (quantity != null && unitPrice != null && amount != null && Math.abs(quantity * unitPrice - amount) > TOLERANCE)
    hints.push('Thành tiền khác số lượng × đơn giá.')
  if (amount != null && percent != null && taxAmount != null && Math.abs(amount * percent / 100 - taxAmount) > TOLERANCE)
    hints.push('Tiền thuế khác thành tiền × thuế suất.')
  return hints
}

/** Row totals against the invoice header; a blank cell leaves that sum unknown instead of counting as 0. */
export const itemDraftTotals = (drafts: InvoiceItemDraft[]) => {
  const sum = (field: 'amount' | 'taxAmount') => {
    const values = drafts.map(draft => parsed(draft[field]))
    return !values.length || values.some(value => value == null) ? null : values.reduce<number>((total, value) => total + (value as number), 0)
  }
  return { amount: sum('amount'), taxAmount: sum('taxAmount') }
}

export const totalDiffers = (sum: number | null, header: number | null) =>
  sum != null && header != null && Math.abs(sum - header) > TOLERANCE

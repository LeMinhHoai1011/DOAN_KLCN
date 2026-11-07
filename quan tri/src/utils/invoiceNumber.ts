/**
 * One convention for every invoice number: money groups thousands with a dot and marks decimals with a comma;
 * quantities never use a dot. Stored form and edit boxes carry no grouping, only a decimal comma.
 */
const MONEY_ERROR = 'Số tiền không hợp lệ. Dấu chấm ngăn nghìn, dấu phẩy phần lẻ. Ví dụ: 3.008.239 hoặc 3008238,66.'
const QUANTITY_ERROR = 'Số lượng không dùng dấu chấm. Phần lẻ ghi bằng dấu phẩy. Ví dụ: 12,63.'

const toNumber = (value: string) => {
  const number = Number(value.replace(/\./g, '').replace(',', '.'))
  if (!Number.isFinite(number) || Math.abs(number) > Number.MAX_SAFE_INTEGER) throw new Error('Giá trị số vượt phạm vi hỗ trợ.')
  return number
}
const compact = (raw: string) => raw.trim().replace(/[  ]/g, '')

export const parseInvoiceNumber = (raw: string): number | null => {
  const value = compact(raw)
  if (!value) return null
  if (!/^[+-]?(?:\d+|\d{1,3}(?:\.\d{3})+)(?:,\d+)?$/.test(value)) throw new Error(MONEY_ERROR)
  return toNumber(value)
}

/** A dot here is rejected, not guessed: 12.630 would otherwise silently become 12630. */
export const parseInvoiceQuantity = (raw: string): number | null => {
  const value = compact(raw)
  if (!value) return null
  if (!/^[+-]?\d+(?:,\d+)?$/.test(value)) throw new Error(QUANTITY_ERROR)
  return toNumber(value)
}

/** Whole dong with a fraction from 0,5 rounded up, as BigDecimal HALF_UP does on the backend. */
export const roundToDong = (value: number) => Math.sign(value) * Math.round(Math.round(Math.abs(value) * 1e6) / 1e6)

export const invoiceNumberInput = (value: number | null) => value == null ? '' : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 20, useGrouping: false }).format(value)
export const invoiceQuantity = (value: number | null | undefined) => value == null ? '—' : invoiceNumberInput(value)

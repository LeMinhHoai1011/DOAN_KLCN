/** Input convention: Vietnamese grouping dot and decimal comma; ungrouped JSON-style decimals accepted. */
export const parseInvoiceNumber = (raw: string): number | null => {
  let value = raw.trim().replace(/[ \u00a0]/g, '')
  if (!value) return null
  if (/^[+-]?\d{1,3}(?:\.\d{3})+,\d+$/.test(value)) value = value.replace(/\./g, '').replace(',', '.')
  else if (/^[+-]?\d{1,3}(?:,\d{3})+\.\d+$/.test(value)) value = value.replace(/,/g, '')
  else if (/^[+-]?\d+,\d+$/.test(value)) value = value.replace(',', '.')
  else if (/^[+-]?\d{1,3}(?:\.\d{3})+$/.test(value)) value = value.replace(/\./g, '')
  else if (!/^[+-]?\d+(?:\.\d+)?$/.test(value)) throw new Error('Dấu phân cách số không hợp lệ. Ví dụ: 80.000,000 = 80.000 đồng.')
  const number = Number(value)
  if (!Number.isFinite(number) || Math.abs(number) > Number.MAX_SAFE_INTEGER) throw new Error('Giá trị số vượt phạm vi hỗ trợ.')
  return number
}
export const invoiceNumberInput = (value: number | null) => value == null ? '' : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 20, useGrouping: false }).format(value)

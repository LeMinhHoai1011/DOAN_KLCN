import test from 'node:test'
import assert from 'node:assert/strict'
import { parseInvoiceNumber, parseInvoiceQuantity, invoiceNumberInput, invoiceQuantity, roundToDong } from './invoiceNumber.ts'
import { reviewMessages, assessmentMessage } from './assessmentPresentation.ts'
test('Vietnamese decimal money and quantities do not gain three zeroes', () => {
  assert.equal(parseInvoiceNumber('80.000,000'), 80000)
  assert.equal(parseInvoiceNumber('45.000,000'), 45000)
  assert.equal(parseInvoiceNumber('842.400'), 842400)
  assert.equal(parseInvoiceNumber('3,00'), 3)
  assert.equal(parseInvoiceNumber('3.008.238,66'), 3008238.66)
  assert.equal(parseInvoiceNumber(''), null)
  assert.throws(() => parseInvoiceNumber('8.00.000'))
  assert.throws(() => parseInvoiceNumber('80,000.000'))
  assert.throws(() => parseInvoiceNumber('12.63'))
  assert.throws(() => parseInvoiceNumber('not a number'))
  for (const number of [0, 80.125, 80000, 842400.5]) assert.equal(parseInvoiceNumber(invoiceNumberInput(number)), number)
})
test('quantities use only a decimal comma, in display and in input', () => {
  assert.equal(invoiceQuantity(12.63), '12,63')
  assert.equal(invoiceQuantity(1500), '1500')
  assert.equal(invoiceQuantity(null), '—')
  assert.equal(parseInvoiceQuantity('12,63'), 12.63)
  assert.equal(parseInvoiceQuantity('1500'), 1500)
  assert.equal(parseInvoiceQuantity(''), null)
  assert.throws(() => parseInvoiceQuantity('12.630'), /dấu phẩy/)
  assert.throws(() => parseInvoiceQuantity('1.500,5'))
})
test('rounds to whole dong from 0,5 upward', () => {
  assert.equal(roundToDong(3008238.66), 3008239)
  assert.equal(roundToDong(240659.12), 240659)
  assert.equal(roundToDong(1501.5), 1502)
  assert.equal(roundToDong(1501.49), 1501)
  assert.equal(roundToDong(-1501.5), -1502)
})
test('review list excludes classification evidence and translates row mismatches', () => {
  const result = reviewMessages(['DOCUMENT_TYPE_REASON: visible VAT evidence', 'ITEM_AMOUNT_MISMATCH: row=1, quantity * unitPrice', 'ITEM_AMOUNT_MISMATCH: row=1, quantity * unitPrice', 'INVALID_SIGN_DATE: invalid yyyy-MM-dd'])
  assert.equal(result.length, 2)
  assert.match(result[0], /Dòng 1/)
  assert.match(result[0], /dấu chấm/)
  assert.equal(result[1], 'Ngày ký chưa hợp lệ.')
  assert.doesNotMatch(result.join(' '), /ITEM_AMOUNT_MISMATCH|yyyy|visible/)
  assert.equal(assessmentMessage('secret technical text'), 'Có thông tin cần đối chiếu với chứng từ gốc.')
  assert.equal(assessmentMessage('DUPLICATE_INVOICE: x; documents=277,299'), 'Trùng người bán và số hóa đơn với chứng từ đã có: #277, #299.')
})

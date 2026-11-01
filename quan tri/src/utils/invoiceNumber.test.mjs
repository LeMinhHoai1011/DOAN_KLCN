import test from 'node:test'
import assert from 'node:assert/strict'
import { parseInvoiceNumber, invoiceNumberInput } from './invoiceNumber.ts'
import { reviewMessages, assessmentMessage } from './assessmentPresentation.ts'
test('Vietnamese decimal money and quantities do not gain three zeroes', () => {
  assert.equal(parseInvoiceNumber('80.000,000'), 80000)
  assert.equal(parseInvoiceNumber('45.000,000'), 45000)
  assert.equal(parseInvoiceNumber('842.400'), 842400)
  assert.equal(parseInvoiceNumber('3,00'), 3)
  assert.equal(parseInvoiceNumber('80,000.000'), 80000)
  assert.equal(parseInvoiceNumber(''), null)
  assert.throws(() => parseInvoiceNumber('8.00.000'))
  assert.throws(() => parseInvoiceNumber('not a number'))
  for (const number of [0, 80.125, 80000, 842400.5]) assert.equal(parseInvoiceNumber(invoiceNumberInput(number)), number)
})
test('review list excludes classification evidence and translates row mismatches', () => {
  const result = reviewMessages(['DOCUMENT_TYPE_REASON: visible VAT evidence', 'ITEM_AMOUNT_MISMATCH: row=1, quantity * unitPrice', 'ITEM_AMOUNT_MISMATCH: row=1, quantity * unitPrice', 'INVALID_SIGN_DATE: invalid yyyy-MM-dd'])
  assert.equal(result.length, 2)
  assert.match(result[0], /Dòng 1/)
  assert.match(result[0], /dấu chấm/)
  assert.equal(result[1], 'Ngày ký chưa hợp lệ.')
  assert.doesNotMatch(result.join(' '), /ITEM_AMOUNT_MISMATCH|yyyy|visible/)
  assert.equal(assessmentMessage('secret technical text'), 'Có thông tin cần đối chiếu với chứng từ gốc.')
})

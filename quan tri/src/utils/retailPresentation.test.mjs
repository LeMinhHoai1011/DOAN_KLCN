import assert from 'node:assert/strict'
import test from 'node:test'
import {
  adjustmentSummary, fromRetailDraft, isRetailDocument, retailServerErrors, toRetailDraft,
} from './retailPresentation.ts'

const complete = {
  transactionTime: '14:05', lineAmountBasis: 'AFTER_DISCOUNT', lineDiscountsChecked: true,
  lineDiscounts: [{ rowNumber: '2', amount: '5.000' }],
  billDiscount: { treatment: 'SEPARATE', amount: '10.000' },
  tax: { treatment: 'INCLUDED', amount: '' },
  fees: { treatment: 'NOT_PRESENT', amount: '999' },
  tenderedAmount: '500.000', changeAmount: '12.500,5',
}

test('recognises a retail bill by its document type code', () => {
  assert.equal(isRetailDocument({ documentTypeCode: 'RETAIL_INVOICE' }), true)
  assert.equal(isRetailDocument({ documentTypeCode: null, documentType: 'retail_invoice' }), true)
  assert.equal(isRetailDocument({ documentTypeCode: 'VAT_INVOICE', documentType: 'RETAIL_INVOICE' }), false)
  assert.equal(isRetailDocument(null), false)
})

test('a complete draft becomes the request the server expects', () => {
  const { details, errors } = fromRetailDraft(complete, 3)
  assert.deepEqual(errors, [])
  assert.deepEqual(details, {
    transactionTime: '14:05', lineAmountBasis: 'AFTER_DISCOUNT', lineDiscounts: [{ rowNumber: 2, amount: 5000 }],
    billDiscount: { treatment: 'SEPARATE', amount: 10000 }, tax: { treatment: 'INCLUDED', amount: null },
    fees: { treatment: 'NOT_PRESENT', amount: null }, tenderedAmount: 500000, changeAmount: 12500.5,
  })
})

test('round-trips stored details through the editable draft', () => {
  const { details } = fromRetailDraft(complete, 3)
  assert.deepEqual(fromRetailDraft(toRetailDraft(details), 3).details, details)
})

test('an empty draft lists every decision the accountant still has to make', () => {
  const { details, errors } = fromRetailDraft(toRetailDraft(null), 3)
  assert.equal(details, null)
  assert.ok(errors.some(error => error.includes('trước hay sau giảm giá')))
  assert.ok(errors.some(error => error.includes('giảm giá từng dòng')))
  for (const label of ['Giảm giá cả hóa đơn', 'Thuế', 'Phí']) assert.ok(errors.some(error => error.startsWith(label)), label)
})

test('rejects bad time, unknown or repeated rows, negative and unreadable amounts', () => {
  const { errors } = fromRetailDraft({ ...complete, transactionTime: '25:00',
    lineDiscounts: [{ rowNumber: '4', amount: '1' }, { rowNumber: '1', amount: '1' }, { rowNumber: '1', amount: '2' }],
    billDiscount: { treatment: 'SEPARATE', amount: '' }, tenderedAmount: '-5', changeAmount: 'abc' }, 3)
  assert.ok(errors.some(error => error.startsWith('Giờ giao dịch')))
  assert.ok(errors.some(error => error.includes('dòng 4 không có')))
  assert.ok(errors.some(error => error.includes('dòng 1 bị nhập hai lần')))
  assert.ok(errors.some(error => error.includes('cần số tiền khi cộng/trừ riêng')))
  assert.ok(errors.some(error => error.startsWith('Tiền khách đưa: không được âm')))
  assert.ok(errors.some(error => error.startsWith('Tiền thối lại: số tiền không hợp lệ')))
})

test('unchecked line discounts are sent as unknown, an empty checked list as "none"', () => {
  assert.ok(fromRetailDraft({ ...complete, lineDiscountsChecked: false }, 3).errors.length > 0)
  assert.deepEqual(fromRetailDraft({ ...complete, lineDiscounts: [] }, 3).details?.lineDiscounts, [])
})

test('keeps only the Vietnamese text of server rule messages', () => {
  assert.deepEqual(retailServerErrors('RETAIL_LINE_BASIS_UNKNOWN: Chưa rõ thành tiền dòng; RETAIL_CASH_INVALID: Tiền thừa không hợp lệ'),
    ['Chưa rõ thành tiền dòng', 'Tiền thừa không hợp lệ'])
  assert.deepEqual(retailServerErrors('Bạn không có quyền thực hiện thao tác này.'), ['Bạn không có quyền thực hiện thao tác này.'])
})

test('summarises an adjustment for display', () => {
  assert.equal(adjustmentSummary({ treatment: 'SEPARATE', amount: 10000 }).startsWith('Cộng/trừ riêng · 10.000'), true)
  assert.equal(adjustmentSummary({ treatment: 'NOT_PRESENT', amount: null }), 'Không có')
  assert.equal(adjustmentSummary(null), 'Chưa rõ')
})

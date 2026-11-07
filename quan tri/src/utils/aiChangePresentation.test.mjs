import test from 'node:test'
import assert from 'node:assert/strict'
import { aiChangeLabel, aiChangeSummary, aiChangeValue, aiFieldLabel } from './aiChangePresentation.ts'

test('labels name the row and show blank values plainly', () => {
  assert.equal(aiChangeLabel({ field: 'items.amount', row: 3 }), 'Dòng 3 · Thành tiền')
  assert.equal(aiChangeLabel({ field: 'invoiceNumber', row: null }), 'Số hóa đơn')
  assert.equal(aiFieldLabel('newField'), 'newField')
  assert.equal(aiChangeValue(null), '(trống)')
})

test('only an approved document counts as confirmed', () => {
  assert.equal(aiChangeSummary(0, 0, 0, false), 'Chưa có dữ liệu để so sánh.')
  assert.equal(aiChangeSummary(40, 37, 3, true), 'Kế toán sửa 3/40 giá trị, giữ nguyên 37.')
  assert.match(aiChangeSummary(40, 40, 0, false), /Chưa duyệt/)
})

import test from 'node:test'
import assert from 'node:assert/strict'
import { missingLabels, toEditableRow, toStoredRow } from './liveEvaluationPresentation.ts'

const checked = { state: 'ABSENT', value: null }

test('tax rates keep their percent sign in storage and lose it for editing', () => {
  assert.deepEqual(toStoredRow({ productName: ' Giấy ', taxRate: '1', unit: ' ' }), { productName: 'Giấy', taxRate: '1%' })
  assert.deepEqual(toStoredRow({ taxRate: '8%' }), { taxRate: '8%' })
  assert.deepEqual(toEditableRow({ taxRate: '8%', amount: '10' }), { taxRate: '8', amount: '10' })
  assert.deepEqual(toEditableRow({ amount: '10' }), { amount: '10' })
})

test('a finished label has nothing left to do', () => {
  assert.deepEqual(missingLabels({ documentType: 'VAT_INVOICE', fields: { totalAmount: { state: 'PRESENT', value: '10' }, sellerName: { state: 'UNREADABLE', value: null } }, category: checked, itemsState: 'ABSENT', items: [] }), [])
})

test('unchecked, empty and undecided parts are all named', () => {
  assert.deepEqual(missingLabels({ documentType: null, fields: { totalAmount: { state: 'PRESENT', value: ' ' }, sellerName: { state: 'UNCHECKED', value: null } }, category: { state: 'UNCHECKED', value: null }, itemsState: 'PRESENT', items: [] }),
    ['Loại chứng từ', 'Tổng thanh toán', 'Tên người bán', 'Nhóm chi phí', 'Bảng hàng hóa'])
})

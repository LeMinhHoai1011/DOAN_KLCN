import test from 'node:test'
import assert from 'node:assert/strict'
import { emptyItemDraft, itemDraftHints, itemDraftsChanged, itemDraftTotals, toItemDrafts, toItemPayloads, totalDiffers } from './invoiceItemEditor.ts'

const item = { id: 1, productName: 'Giấy A4', unit: 'Ram', quantity: 2, unitPrice: 50000, taxRate: 0.08, taxAmount: 8000, amount: 100000 }

test('an untouched row round-trips and is not reported as changed', () => {
  const drafts = toItemDrafts([item])
  assert.equal(drafts[0].taxRate, '8')
  assert.equal(itemDraftsChanged(drafts, [item]), false)
  const { id, ...payload } = item
  assert.deepEqual(toItemPayloads(drafts)[0], payload)
})

test('stored percentage and fraction rates show the same value and save as a fraction', () => {
  const [draft] = toItemDrafts([{ ...item, taxRate: 10 }])
  assert.equal(draft.taxRate, '10')
  assert.equal(toItemPayloads([draft])[0].taxRate, 0.1)
  assert.equal(toItemPayloads([{ ...draft, taxRate: '1' }])[0].taxRate, 0.01)
})

test('adding and removing rows counts as a change', () => {
  const drafts = toItemDrafts([item])
  assert.equal(itemDraftsChanged([...drafts, emptyItemDraft()], [item]), true)
  assert.equal(itemDraftsChanged([], [item]), true)
})

test('rejects rows the backend would refuse and names the row', () => {
  const [draft] = toItemDrafts([item])
  assert.throws(() => toItemPayloads([draft, { ...draft, productName: ' ' }]), /Dòng 2: Tên hàng hóa/)
  assert.throws(() => toItemPayloads([{ ...draft, quantity: '-1' }]), /Dòng 1 - Số lượng/)
  assert.throws(() => toItemPayloads([{ ...draft, taxRate: '101' }]), /Thuế suất/)
  assert.throws(() => toItemPayloads([{ ...draft, taxRate: '0,5' }]), /Thuế suất/)
  assert.throws(() => toItemPayloads([{ ...draft, amount: 'abc' }]), /Dòng 1 - Thành tiền/)
})

test('keeps negative money for discount rows and blank cells as unknown', () => {
  const [draft] = toItemDrafts([item])
  const [discount] = toItemPayloads([{ ...draft, quantity: '1', unitPrice: '-20.000', amount: '-20.000', taxAmount: '' }])
  assert.equal(discount.unitPrice, -20000)
  assert.equal(discount.amount, -20000)
  assert.equal(discount.taxAmount, null)
})

test('hints flag inconsistent rows without changing them', () => {
  const [draft] = toItemDrafts([item])
  assert.deepEqual(itemDraftHints(draft), [])
  assert.equal(itemDraftHints({ ...draft, amount: '90000' }).length, 2)
  assert.deepEqual(itemDraftHints({ ...draft, quantity: '' }), [])
  assert.deepEqual(itemDraftHints({ ...draft, quantity: '12,63', unitPrice: '238.182', amount: '3.008.239', taxAmount: '240.659' }), [])
  assert.equal(totalDiffers(100.99, 100), false)
  assert.equal(totalDiffers(101, 100), true)
})

test('row totals stay unknown while a cell is blank and compare to the header', () => {
  const [draft] = toItemDrafts([item])
  assert.deepEqual(itemDraftTotals([draft, draft]), { amount: 200000, taxAmount: 16000 })
  assert.equal(itemDraftTotals([draft, emptyItemDraft()]).amount, null)
  assert.equal(itemDraftTotals([]).amount, null)
  assert.equal(totalDiffers(200000, 190000), true)
  assert.equal(totalDiffers(200000, 200000), false)
  assert.equal(totalDiffers(null, 190000), false)
})

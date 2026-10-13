import test from 'node:test'
import assert from 'node:assert/strict'
import {
  hasInsufficientReconciliationData,
  reconciliationMoney,
  reconciliationStatusLabel,
} from './reconciliationPresentation.ts'

test('unknown reconciliation amounts render as em dash instead of zero', () => {
  assert.equal(reconciliationMoney(null), '—')
  assert.equal(reconciliationMoney(undefined), '—')
  assert.equal(reconciliationMoney(0), '0')
})

test('distinguishes matched mismatch and insufficient data', () => {
  assert.equal(reconciliationStatusLabel('MATCHED'), 'Khớp')
  assert.equal(reconciliationStatusLabel('MISMATCH'), 'Sai lệch')
  assert.equal(reconciliationStatusLabel('INSUFFICIENT_DATA'), 'Chưa đủ dữ liệu')
  assert.equal(hasInsufficientReconciliationData(['MATCHED', 'INSUFFICIENT_DATA']), true)
})

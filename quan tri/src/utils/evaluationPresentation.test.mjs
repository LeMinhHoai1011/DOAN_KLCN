import test from 'node:test'
import assert from 'node:assert/strict'
import { evaluationPercent, evaluationDuration, evaluationError, evaluationDateBounds } from './evaluationPresentation.ts'
test('unknown metrics never become zero or perfect accuracy', () => {
  assert.equal(evaluationPercent(null), 'Chưa xác định'); assert.equal(evaluationPercent(undefined), 'Chưa xác định')
  assert.equal(evaluationPercent(0), '0.00%'); assert.equal(evaluationPercent(1), '100.00%'); assert.equal(evaluationPercent(1.5), '150.00%')
  assert.equal(evaluationDuration(null), 'Chưa xác định'); assert.equal(evaluationDuration(1250), '1.25 giây')
})
test('API permission error and invalid benchmark differ from an empty dataset', () => {
  assert.match(evaluationError(403), /không có quyền/); assert.match(evaluationError(422), /chưa hợp lệ/)
  assert.match(evaluationError(500), /Không thể tải/)
})
test('date filter preserves inclusive local-day bounds and explicit timezone', () => {
  const bounds = evaluationDateBounds('2026-10-17', '2026-10-17')
  assert.equal(new Date(bounds.to).getTime() - new Date(bounds.from).getTime(), 86_399_999)
  assert.deepEqual(evaluationDateBounds('', ''), { from: undefined, to: undefined })
})

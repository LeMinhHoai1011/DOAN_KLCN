import test from 'node:test'
import assert from 'node:assert/strict'
import { aiGroupLabel, aiDocumentStatusLabel, aiSkipReasonLabel, countAiDocuments, filterAiDocuments } from './aiAccuracyPresentation.ts'

const rows = [
  { documentId: 3, status: 'SKIPPED', skipReason: 'NO_AI_RESULT' },
  { documentId: 2, status: 'CHANGED', skipReason: null },
  { documentId: 1, status: 'EXACT', skipReason: null },
  { documentId: 4, status: 'EXACT', skipReason: null },
]

test('filters approved documents by how the accountant treated the AI result', () => {
  assert.deepEqual(filterAiDocuments(rows, 'EXACT').map(row => row.documentId), [1, 4])
  assert.equal(filterAiDocuments(rows, 'ALL').length, 4)
  assert.deepEqual(countAiDocuments(rows), { ALL: 4, EXACT: 2, CHANGED: 1, SKIPPED: 1 })
})

test('labels groups, statuses and skip reasons in Vietnamese', () => {
  assert.equal(aiGroupLabel('ITEMS'), 'Dòng hàng hóa')
  assert.equal(aiGroupLabel('OTHER'), 'OTHER')
  assert.equal(aiDocumentStatusLabel('EXACT'), 'AI đúng hoàn toàn')
  assert.equal(aiSkipReasonLabel('NO_APPROVAL_AFTER_AI'), 'AI chạy lại sau khi duyệt')
  assert.equal(aiSkipReasonLabel(null), '')
})

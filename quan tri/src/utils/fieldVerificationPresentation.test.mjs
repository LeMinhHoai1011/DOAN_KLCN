import test from 'node:test'
import assert from 'node:assert/strict'
import { aiInputModeLabel, indexVerifications, needsAttention, toPreviewFocus, worstStatus } from './fieldVerificationPresentation.ts'

const view = (fieldKey, status, location = null) => ({ fieldKey, itemIndex: null, core: true, status, score: null, value: '1', aiValue: '1',
  valueOrigin: 'AI', evidence: location ? { outcome: 'PASS', matchScore: 0.9, method: 'OCR_WORDS', matchedText: '1', location } : null, rules: [], reasons: [] })

test('a row shows its most important problem first', () => {
  assert.equal(worstStatus([view('a', 'VERIFIED'), view('b', 'UNVERIFIED'), view('c', 'WARNING')]).status, 'WARNING')
  assert.equal(worstStatus([view('a', 'VERIFIED'), undefined]).status, 'VERIFIED')
  assert.equal(worstStatus([undefined]), undefined)
})

test('only verified fields need no attention', () => {
  assert.equal(needsAttention(view('a', 'VERIFIED')), false)
  assert.equal(needsAttention(view('a', 'UNVERIFIED')), true)
  assert.equal(needsAttention(undefined), false)
})

test('evidence location becomes an image highlight; no location means no fake highlight', () => {
  const location = { page: 2, x: 10, y: 20, width: 30, height: 8, pageWidth: 800, pageHeight: 1000 }
  const focus = toPreviewFocus(view('totalAmount', 'WARNING', location), 'Tổng tiền')
  assert.equal(focus.locations[0].page, 2)
  assert.ok(focus.id < 0, 'synthetic id never collides with a stored field id')
  assert.equal(toPreviewFocus(view('totalAmount', 'WARNING'), 'Tổng tiền'), null)
})

test('report is indexed by field key and input mode is explained', () => {
  const index = indexVerifications({ fields: [view('items[0].amount', 'VERIFIED')] })
  assert.equal(index['items[0].amount'].status, 'VERIFIED')
  assert.deepEqual(indexVerifications(null), {})
  assert.equal(aiInputModeLabel('VISION'), 'AI đọc trực tiếp từ ảnh')
  assert.equal(aiInputModeLabel(null), 'Chưa ghi nhận')
})

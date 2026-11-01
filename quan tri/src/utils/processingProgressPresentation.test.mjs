import test from 'node:test'
import assert from 'node:assert/strict'
import { formatSeconds, secondsByStep, stepLabel, stepSeconds } from './processingProgressPresentation.ts'

const at = s => new Date(Date.UTC(2026, 10, 1, 0, 0, s)).toISOString()

test('known steps get Vietnamese labels, unknown codes are shown as-is', () => {
  assert.equal(stepLabel('AI_EXTRACTION'), 'Phân tích bằng AI')
  assert.equal(stepLabel('NEW_STEP'), 'NEW_STEP')
})

test('open steps count up to now; invalid timestamps are not guessed', () => {
  assert.equal(stepSeconds({ startedAt: at(0), endedAt: at(3) }, 0), 3)
  assert.equal(stepSeconds({ startedAt: at(0), endedAt: null }, Date.parse(at(12))), 12)
  assert.equal(stepSeconds({ startedAt: 'bad', endedAt: null }, 0), null)
  assert.equal(formatSeconds(null), '—')
  assert.equal(formatSeconds(2.345), '2.3 giây')
  assert.equal(formatSeconds(42.6), '43 giây')
})

test('time is summed per step across PDF pages', () => {
  const steps = [
    { code: 'OCR', detail: 'Trang 1/2', startedAt: at(0), endedAt: at(2) },
    { code: 'AI_EXTRACTION', detail: 'Trang 1/2', startedAt: at(2), endedAt: at(12) },
    { code: 'OCR', detail: 'Trang 2/2', startedAt: at(12), endedAt: at(15) },
  ]
  assert.deepEqual(secondsByStep(steps, 0), { OCR: 5, AI_EXTRACTION: 10 })
})

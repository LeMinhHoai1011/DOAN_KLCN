import test from 'node:test'
import assert from 'node:assert/strict'
import { createActiveReviewTimer, isReviewDecision } from './activeReviewTimer.ts'

test('hidden periods are excluded from review time', () => {
  const timer = createActiveReviewTimer(0, true)
  timer.setVisible(false, 10_000)
  timer.setVisible(true, 70_000)
  assert.equal(timer.elapsed(75_000), 15_000)
})

test('a page opened in the background starts counting when shown, and reset restarts', () => {
  const timer = createActiveReviewTimer(0, false)
  assert.equal(timer.elapsed(5_000), 0)
  timer.setVisible(true, 5_000)
  assert.equal(timer.elapsed(8_000), 3_000)
  timer.reset(8_000, true)
  assert.equal(timer.elapsed(9_000), 1_000)
})

test('only reviewer decisions carry review time', () => {
  assert.equal(isReviewDecision('APPROVE'), true)
  assert.equal(isReviewDecision('REQUEST_INFO'), true)
  assert.equal(isReviewDecision('START_REVIEW'), false)
  assert.equal(isReviewDecision('SUBMIT'), false)
})

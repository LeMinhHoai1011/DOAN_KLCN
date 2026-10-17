import assert from 'node:assert/strict'
import test from 'node:test'
import { formatConfidence } from './confidence.ts'

test('formats canonical 0-1 confidence as a percentage', () => {
  assert.equal(formatConfidence(0.84), '84%')
  assert.equal(formatConfidence(0.856), '85.6%')
  assert.equal(formatConfidence(0.9), '90%')
  assert.equal(formatConfidence(1), '100%')
})

test('shows unknown for missing or out-of-scale values', () => {
  assert.equal(formatConfidence(null), 'Chưa xác định')
  assert.equal(formatConfidence(undefined), 'Chưa xác định')
  assert.equal(formatConfidence(84), 'Chưa xác định')
})

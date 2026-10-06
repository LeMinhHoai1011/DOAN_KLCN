import assert from 'node:assert/strict'
import test from 'node:test'
import { formatConfidence } from './confidence.ts'

test('formats canonical 0-1 confidence as a percentage', () => {
  assert.equal(formatConfidence(0.84), '84%')
  assert.equal(formatConfidence(0.856), '85.6%')
  assert.equal(formatConfidence(0.9), '90%')
  assert.equal(formatConfidence(1), '100%')
})

test('returns an em dash for missing or out-of-scale values', () => {
  assert.equal(formatConfidence(null), '—')
  assert.equal(formatConfidence(undefined), '—')
  assert.equal(formatConfidence(84), '—')
})

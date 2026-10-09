import test from 'node:test'
import assert from 'node:assert/strict'
import { classificationReadOutcome, listReadOutcome } from './readState.ts'

test('category list distinguishes populated and empty HTTP 200 responses', () => {
  assert.equal(listReadOutcome(200, 2), 'success')
  assert.equal(listReadOutcome(200, 0), 'empty')
})

test('classification treats only HTTP 404 as a valid missing result', () => {
  assert.equal(classificationReadOutcome(200), 'success')
  assert.equal(classificationReadOutcome(404), 'empty')
  for (const status of [401, 403, 500]) assert.equal(classificationReadOutcome(status), 'error')
})

test('category authorization and server failures remain errors', () => {
  for (const status of [401, 403, 404, 500]) assert.equal(listReadOutcome(status, 0), 'error')
})

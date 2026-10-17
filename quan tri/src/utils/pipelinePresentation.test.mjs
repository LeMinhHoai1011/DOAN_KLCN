import test from 'node:test';
import assert from 'node:assert/strict';
import { canRetryProcessing, documentPipelinePaths, pipelineStageLabel } from './pipelinePresentation.ts';

const temporary = {
  retryAllowed: true, maxAttempts: 3, attemptCount: 1, succeeded: 0, failed: 1, failuresByCode: { AI_TIMEOUT: 1 },
  attempts: [{ jobId: 1, attemptNumber: 1, status: 'FAILED', failure: { retryable: true } }],
};
test('all pipeline paths use the real API prefix', () => {
  assert.equal(documentPipelinePaths(12).assessment, '/api/v1/documents/12/assessment');
  assert.equal(documentPipelinePaths(12).history, '/api/v1/documents/12/processing-history');
  assert.equal(documentPipelinePaths(12).retry, '/api/v1/documents/12/retry');
  assert.throws(() => documentPipelinePaths(NaN));
});
test('only failed temporary attempts with server approval can show retry', () => {
  assert.equal(canRetryProcessing(temporary, 'FAILED', 'PENDING'), true);
  assert.equal(canRetryProcessing(temporary, 'PROCESSING', 'PENDING'), false);
  assert.equal(canRetryProcessing(temporary, 'FAILED', 'APPROVED'), false);
  assert.equal(canRetryProcessing({ ...temporary, retryAllowed: false }, 'FAILED', 'PENDING'), false);
  assert.equal(canRetryProcessing(null, 'FAILED', 'PENDING'), false);
});
test('a completed or exhausted chain does not show retry', () => {
  assert.equal(canRetryProcessing({ ...temporary, attempts: [{ ...temporary.attempts[0], attemptNumber: 3 }] }, 'FAILED', 'PENDING'), false);
  assert.equal(canRetryProcessing({ ...temporary, attempts: [{ ...temporary.attempts[0], status: 'SUCCESS' }] }, 'FAILED', 'PENDING'), false);
  assert.equal(pipelineStageLabel('STORAGE'), 'Truy cập kho tệp');
});

import assert from 'node:assert/strict'
import test from 'node:test'
import { bestLocation, fieldLabel, parseOcrLayout, scaleBoundingBox } from './ocrPreviewUtils.ts'
import { PreviewLoadError, previewHttpError, validatePreviewBlob } from '../../services/previewBlob.ts'

test('scales a backend bbox to the rendered page dimensions', () => {
  assert.deepEqual(
    scaleBoundingBox({ x: 100, y: 200, width: 300, height: 100 }, 1000, 2000, 500, 1000),
    { left: 50, top: 100, width: 150, height: 50 },
  )
})

test('bbox remains attached when zoom changes from 100 to 150 percent', () => {
  const box = { x: 100, y: 200, width: 300, height: 100 }
  const normal = scaleBoundingBox(box, 1000, 2000, 500, 1000)
  const zoomed = scaleBoundingBox(box, 1000, 2000, 750, 1500)
  assert.deepEqual(zoomed, Object.fromEntries(Object.entries(normal).map(([key, value]) => [key, value * 1.5])))
})

test('selects the highest confidence location including a field on page two', () => {
  const field = { id: 1, fieldName: 'totalAmount', fieldValue: '70.000', source: 'AI', confidence: .9,
    locations: [
      { page: 1, x: 1, y: 2, width: 3, height: 4, pageWidth: 100, pageHeight: 200, matchConfidence: .8 },
      { page: 2, x: 5, y: 6, width: 7, height: 8, pageWidth: 100, pageHeight: 200, matchConfidence: .96 },
    ] }
  assert.equal(bestLocation(field)?.page, 2)
  assert.equal(bestLocation({ ...field, locations: [] }), null)
})

test('parses v2 word line and block collections', () => {
  const pages = parseOcrLayout(JSON.stringify({ version: 2, pages: [{ page: 1, words: [{}], lines: [{}], blocks: [{}] }] }))
  assert.equal(pages[0].words?.length, 1)
  assert.equal(pages[0].lines?.length, 1)
  assert.equal(pages[0].blocks?.length, 1)
})

test('uses a Vietnamese label instead of raw sellerTaxCode', () => {
  assert.equal(fieldLabel('sellerTaxCode'), 'Mã số thuế người bán')
  assert.notEqual(fieldLabel('sellerTaxCode'), 'sellerTaxCode')
})

test('accepts JPEG PNG and PDF signatures and corrects a generic Blob type', async () => {
  const jpeg = await validatePreviewBlob(new Blob([Uint8Array.from([0xff, 0xd8, 0xff, 0x01])], { type: 'application/octet-stream' }))
  const png = await validatePreviewBlob(new Blob([Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x0d])]))
  const pdf = await validatePreviewBlob(new Blob(['%PDF-1.7'], { type: 'application/octet-stream' }))
  assert.equal(jpeg.contentType, 'image/jpeg')
  assert.equal(jpeg.blob.type, 'image/jpeg')
  assert.equal(png.contentType, 'image/png')
  assert.equal(pdf.contentType, 'application/pdf')
})

test('rejects empty, JSON and unsupported preview payloads before creating an object URL', async () => {
  await assert.rejects(validatePreviewBlob(new Blob([])), error => error instanceof PreviewLoadError && error.code === 'PREVIEW_EMPTY_BLOB')
  await assert.rejects(validatePreviewBlob(new Blob(['{}'], { type: 'application/json' })), error => error instanceof PreviewLoadError && error.code === 'PREVIEW_INVALID_CONTENT_TYPE')
  await assert.rejects(validatePreviewBlob(new Blob(['not-an-image'], { type: 'application/octet-stream' })), error => error instanceof PreviewLoadError && error.code === 'PREVIEW_INVALID_CONTENT_TYPE')
})

test('classifies auth, not-found and server/storage HTTP failures', () => {
  assert.equal(previewHttpError(401).code, 'PREVIEW_AUTH_ERROR')
  assert.equal(previewHttpError(403).code, 'PREVIEW_AUTH_ERROR')
  assert.equal(previewHttpError(404).code, 'PREVIEW_NOT_FOUND')
  assert.equal(previewHttpError(500).code, 'PREVIEW_SERVER_ERROR')
})

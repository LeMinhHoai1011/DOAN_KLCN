import assert from 'node:assert/strict'
import test from 'node:test'
import { scaleBoundingBox } from './ocrPreviewUtils.ts'
import {
  displayToPageRect, isUsableRegion, joinWordsByLine, normalizeDragRect, parseRegionDate, regionValueForField, wordsInRect,
} from './regionSelect.ts'

const words = [
  { id: 'w1', text: 'Mã', x: 100, y: 200, width: 40, height: 20 },
  { id: 'w3', text: 'CQT:', x: 200, y: 202, width: 50, height: 20 },
  { id: 'w2', text: 'của', x: 145, y: 201, width: 45, height: 20 },
  { id: 'w4', text: '00A1B2C3D4E5F6A7B8', x: 260, y: 200, width: 200, height: 20 },
  { id: 'w5', text: 'Ngày', x: 100, y: 240, width: 50, height: 20 },
  { id: 'w6', text: '05/03/2025', x: 160, y: 240, width: 120, height: 20 },
  { id: 'w7', text: 'Ngoài', x: 700, y: 900, width: 80, height: 20 },
]

test('drag in any direction gives the same rectangle, clamped to the page', () => {
  assert.deepEqual(normalizeDragRect({ x: 300, y: 120 }, { x: 100, y: 20 }, 500, 700), { left: 100, top: 20, width: 200, height: 100 })
  assert.deepEqual(normalizeDragRect({ x: -20, y: 650 }, { x: 600, y: 800 }, 500, 700), { left: 0, top: 650, width: 500, height: 50 })
})

test('display rectangle maps back to original page pixels at any zoom', () => {
  // Page 1000x1400 rendered at 50% and at 75% (zoom 150%): both must land on the same page box.
  const half = displayToPageRect({ left: 50, top: 100, width: 150, height: 15 }, 1, 1000, 1400, 500, 700)
  const zoomed = displayToPageRect({ left: 75, top: 150, width: 225, height: 22.5 }, 1, 1000, 1400, 750, 1050)
  assert.deepEqual(half, { page: 1, x: 100, y: 200, width: 300, height: 30 })
  assert.deepEqual(zoomed, half)
})

test('display-to-page is the inverse of scaleBoundingBox', () => {
  const box = { x: 123, y: 456, width: 210, height: 34 }
  const shown = scaleBoundingBox(box, 1240, 1754, 913, 1291.5)
  const back = displayToPageRect(shown, 2, 1240, 1754, 913, 1291.5)
  for (const key of ['x', 'y', 'width', 'height']) assert.ok(Math.abs(back[key] - box[key]) < 0.02, key)
  assert.equal(back.page, 2)
})

test('mapped rectangle never leaves the page and tiny drags are rejected', () => {
  const edge = displayToPageRect({ left: 490, top: 690, width: 10, height: 10 }, 1, 1000, 1400, 500, 700)
  assert.ok(edge.x + edge.width <= 1000 && edge.y + edge.height <= 1400)
  assert.equal(isUsableRegion(displayToPageRect({ left: 10, top: 10, width: 1, height: 30 }, 1, 1000, 1400, 500, 700)), false)
  assert.equal(isUsableRegion(edge), true)
})

test('keeps only words mostly inside the region, in reading order', () => {
  const inside = wordsInRect(words, { page: 1, x: 90, y: 190, width: 400, height: 80 }, 1000, 1400)
  assert.deepEqual(inside.map(word => word.id).sort(), ['w1', 'w2', 'w3', 'w4', 'w5', 'w6'])
  assert.deepEqual(joinWordsByLine(inside), {
    lines: ['Mã của CQT: 00A1B2C3D4E5F6A7B8', 'Ngày 05/03/2025'],
    text: 'Mã của CQT: 00A1B2C3D4E5F6A7B8\nNgày 05/03/2025',
  })
  // 30% of "CQT:" is inside → excluded.
  const partial = wordsInRect(words, { page: 1, x: 235, y: 195, width: 240, height: 30 }, 1000, 1400)
  assert.deepEqual(partial.map(word => word.id), ['w4'])
})

test('normalized (0..1) layout coordinates are filtered in page pixels', () => {
  const normalized = [{ text: 'A', x: 0.1, y: 0.1, width: 0.05, height: 0.02 }]
  assert.equal(wordsInRect(normalized, { page: 1, x: 90, y: 130, width: 80, height: 40 }, 1000, 1400).length, 1)
})

test('suggests clean values per field from the region text', () => {
  assert.equal(regionValueForField('taxAuthorityCode', 'Mã của CQT: 00A1B2C3D4E5F6A7B8'), '00A1B2C3D4E5F6A7B8')
  assert.equal(regionValueForField('signDate', 'Ký ngày: 05/03/2025'), '2025-03-05')
  assert.equal(regionValueForField('signDate', 'Ngày 5 tháng 3 năm 2025'), '2025-03-05')
  assert.equal(regionValueForField('sellerPhone', 'Điện thoại: 028 3823 4567'), '028 3823 4567')
  assert.equal(regionValueForField('sellerTaxCode', 'Mã số thuế: 0 3 1 2 3 4 5 6 7 8'), '0312345678')
  assert.equal(regionValueForField('totalAmount', 'Tổng cộng tiền thanh toán: 10.800.000'), '10.800.000')
  assert.equal(regionValueForField('sellerAddress', 'Địa chỉ: 12 Lê Lợi,\nQuận 1'), '12 Lê Lợi, Quận 1')
  assert.equal(regionValueForField('sellerName', ''), '')
})

test('date parser rejects impossible dates', () => {
  assert.equal(parseRegionDate('31/13/2025'), null)
  assert.equal(parseRegionDate('2025-03-05'), '2025-03-05')
})

// --- field check flow: which location is highlighted and which name is shown ---
import { bestLocation, fieldDisplay, isValidLocation } from './ocrPreviewUtils.ts'

const loc = (extra = {}) => ({ page: 1, x: 10, y: 20, width: 100, height: 20, pageWidth: 1000, pageHeight: 1400, matchConfidence: 0.8, ...extra })

test('rejects locations that are empty, outside their page or on a page that does not exist', () => {
  assert.equal(isValidLocation(loc()), true)
  assert.equal(isValidLocation(loc({ width: 0 })), false)
  assert.equal(isValidLocation(loc({ x: 950 })), false)
  assert.equal(isValidLocation(loc({ y: -5 })), false)
  assert.equal(isValidLocation(loc({ pageWidth: 0 })), false)
  assert.equal(isValidLocation(loc({ page: 3 }), 2), false)
  assert.equal(isValidLocation(loc({ page: 2 }), 2), true)
  assert.equal(isValidLocation(loc({ x: Number.NaN })), false)
  assert.equal(isValidLocation(null), false)
})

test('a hand-drawn location wins over AI matching and invalid locations are ignored', () => {
  const field = { id: 1, fieldName: 'taxCode', locations: [
    loc({ matchConfidence: 0.99 }), loc({ page: 2, matchConfidence: 1, source: 'MANUAL_REGION' }), loc({ page: 9, matchConfidence: 1 })] }
  assert.equal(bestLocation(field, 2)?.source, 'MANUAL_REGION')
  assert.equal(bestLocation({ ...field, locations: [loc({ width: -1 })] }), null)
  assert.equal(bestLocation({ ...field, locations: [loc({ page: 9 })] }, 2), null)
  assert.equal(bestLocation({ ...field, locations: undefined }), null)
})

test('shows the backend display name and never guesses an unresolved one', () => {
  assert.deepEqual(fieldDisplay({ fieldName: 'sTiNViTBNgChAmountInWords', displayName: 'Số tiền viết bằng chữ (Amount in words)', displayNameSource: 'AI_ORIGINAL' }),
    { label: 'Số tiền viết bằng chữ (Amount in words)', unresolved: false })
  assert.deepEqual(fieldDisplay({ fieldName: 'dN', displayName: null, displayNameSource: 'UNRESOLVED' }), { label: 'dN', unresolved: true })
  assert.deepEqual(fieldDisplay({ fieldName: 'sellerTaxCode' }), { label: 'Mã số thuế người bán', unresolved: false })
  assert.equal(fieldDisplay({ fieldName: 'deliveryCode' }).unresolved, false)
})

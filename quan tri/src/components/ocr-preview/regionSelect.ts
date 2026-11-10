import type { OcrLayoutElement } from './ocrPreviewUtils'

/** Rectangle on screen, relative to the rendered page's top-left corner. */
export type DisplayRect = { left: number; top: number; width: number; height: number }
/** Rectangle in pixels of the original OCR page (same space as ocr_results.layout_json). */
export type PageRect = { page: number; x: number; y: number; width: number; height: number }
export type Point = { x: number; y: number }

/** Matches the backend's RegionSelectionService.MIN_SIDE_PX. */
export const MIN_REGION_SIDE = 4
/** A word belongs to the region when at least this share of its own box lies inside. */
export const WORD_OVERLAP = 0.5

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value))
const round2 = (value: number) => Math.round(value * 100) / 100

/** Drag from any corner to any corner; the result is clamped to the rendered page. */
export const normalizeDragRect = (start: Point, end: Point, renderedWidth: number, renderedHeight: number): DisplayRect => {
  const x1 = clamp(Math.min(start.x, end.x), 0, renderedWidth), x2 = clamp(Math.max(start.x, end.x), 0, renderedWidth)
  const y1 = clamp(Math.min(start.y, end.y), 0, renderedHeight), y2 = clamp(Math.max(start.y, end.y), 0, renderedHeight)
  return { left: x1, top: y1, width: x2 - x1, height: y2 - y1 }
}

/** Inverse of scaleBoundingBox: screen pixels (any zoom / fit mode) back to original page pixels. */
export const displayToPageRect = (rect: DisplayRect, page: number, pageWidth: number, pageHeight: number,
  renderedWidth: number, renderedHeight: number): PageRect => {
  const scaleX = Math.max(1, pageWidth) / Math.max(1, renderedWidth)
  const scaleY = Math.max(1, pageHeight) / Math.max(1, renderedHeight)
  const x = clamp(rect.left * scaleX, 0, pageWidth), y = clamp(rect.top * scaleY, 0, pageHeight)
  const right = clamp((rect.left + rect.width) * scaleX, 0, pageWidth)
  const bottom = clamp((rect.top + rect.height) * scaleY, 0, pageHeight)
  // Floor the origin and ceil the far edge so rounding never pushes the box past the page.
  const left = Math.floor(round2(x) * 100) / 100, top = Math.floor(round2(y) * 100) / 100
  return { page, x: left, y: top, width: round2(Math.min(pageWidth, right) - left), height: round2(Math.min(pageHeight, bottom) - top) }
}

export const isUsableRegion = (rect: PageRect) => rect.width >= MIN_REGION_SIDE && rect.height >= MIN_REGION_SIDE

const toPixels = (word: OcrLayoutElement, pageWidth: number, pageHeight: number) => {
  const normalized = word.x <= 1 && word.y <= 1 && word.width <= 1 && word.height <= 1
  return normalized
    ? { ...word, x: word.x * pageWidth, y: word.y * pageHeight, width: word.width * pageWidth, height: word.height * pageHeight }
    : word
}

export const overlapShare = (word: Pick<OcrLayoutElement, 'x' | 'y' | 'width' | 'height'>, rect: PageRect) => {
  const left = Math.max(word.x, rect.x), top = Math.max(word.y, rect.y)
  const right = Math.min(word.x + word.width, rect.x + rect.width), bottom = Math.min(word.y + word.height, rect.y + rect.height)
  const own = word.width * word.height
  return own <= 0 ? 0 : Math.max(0, right - left) * Math.max(0, bottom - top) / own
}

/** OCR words (in page pixels) that are mostly inside the rectangle. Same rule as the backend. */
export const wordsInRect = (words: OcrLayoutElement[] | undefined, rect: PageRect, pageWidth: number, pageHeight: number) =>
  (words ?? []).map(word => toPixels(word, pageWidth, pageHeight))
    .filter(word => word.text?.trim() && word.width > 0 && word.height > 0 && overlapShare(word, rect) >= WORD_OVERLAP)

/** Reading order: words whose vertical centres are within half a word height share a line. */
export const joinWordsByLine = (words: OcrLayoutElement[]) => {
  const sorted = [...words].sort((a, b) => (a.y + a.height / 2) - (b.y + b.height / 2) || a.x - b.x)
  const lines: OcrLayoutElement[][] = []
  let centre = 0, height = 0
  for (const word of sorted) {
    const wordCentre = word.y + word.height / 2
    const current = lines[lines.length - 1]
    if (current && Math.abs(wordCentre - centre) <= Math.max(1, Math.min(height, word.height) / 2)) {
      current.push(word); centre = (centre * (current.length - 1) + wordCentre) / current.length; height = Math.max(height, word.height)
    } else { lines.push([word]); centre = wordCentre; height = word.height }
  }
  const texts = lines.map(line => line.sort((a, b) => a.x - b.x).map(word => word.text.trim()).join(' '))
  return { lines: texts, text: texts.join('\n') }
}

const DATE_FIELDS = new Set(['signDate', 'invoiceDate'])
const AMOUNT_FIELDS = new Set(['subtotal', 'vatAmount', 'totalAmount', 'taxAmount'])
const pad = (value: string) => value.padStart(2, '0')

/** dd/mm/yyyy, dd-mm-yyyy, dd.mm.yyyy, "ngày 05 tháng 03 năm 2025" or yyyy-mm-dd → yyyy-mm-dd. */
export const parseRegionDate = (text: string) => {
  const iso = text.match(/(\d{4})-(\d{1,2})-(\d{1,2})/)
  const words = text.match(/ng[àa]y\s*(\d{1,2})\s*th[áa]ng\s*(\d{1,2})\s*n[ăa]m\s*(\d{4})/i)
  const numeric = text.match(/(\d{1,2})\s*[/.-]\s*(\d{1,2})\s*[/.-]\s*(\d{4})/)
  const [year, month, day] = iso ? [iso[1], iso[2], iso[3]] : words ? [words[3], words[2], words[1]]
    : numeric ? [numeric[3], numeric[2], numeric[1]] : []
  if (!year || !month || !day) return null
  const m = Number(month), d = Number(day)
  if (m < 1 || m > 12 || d < 1 || d > 31) return null
  return `${year}-${pad(month)}-${pad(day)}`
}

const stripLabel = (text: string) => text.replace(/^[^:\n]{1,40}[:：]\s*/, '')

/**
 * Suggested value for a field from the text inside the region. The accountant always reviews it
 * before applying, so this only removes the printed label and normalizes obvious formats.
 */
export const regionValueForField = (fieldName: string, text: string) => {
  const flat = text.replace(/\s*\n\s*/g, ' ').replace(/\s+/g, ' ').trim()
  if (!flat) return ''
  if (DATE_FIELDS.has(fieldName)) return parseRegionDate(flat) ?? stripLabel(flat)
  if (fieldName === 'taxAuthorityCode') {
    const tokens = stripLabel(flat).match(/[A-Z0-9][A-Z0-9-]{9,}/gi) ?? []
    return tokens.sort((a, b) => b.length - a.length)[0] ?? stripLabel(flat)
  }
  if (/TaxCode$/.test(fieldName)) {
    const code = flat.replace(/(\d)\s+(?=\d)/g, '$1').match(/\d{10}(?:-\d{3})?|\d{12,14}/)
    return code?.[0] ?? stripLabel(flat)
  }
  if (/Phone$/.test(fieldName)) return stripLabel(flat).match(/\+?\d[\d .()-]{6,}\d/)?.[0]?.trim() ?? stripLabel(flat)
  if (AMOUNT_FIELDS.has(fieldName)) {
    const amounts = stripLabel(flat).match(/\d[\d.,]*(?:\s\d{3})*/g) ?? []
    return (amounts[amounts.length - 1] ?? stripLabel(flat)).replace(/\s/g, '')
  }
  return stripLabel(flat)
}

export const formatConfidence = (value: number | null | undefined): string => {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 1) return 'Chưa xác định'

  const percent = Math.round(value * 1000) / 10
  return `${Number.isInteger(percent) ? percent.toFixed(0) : percent.toFixed(1)}%`
}

/** A digital PDF is read from its text layer, so OCR never runs and has no score to show. */
export const textSourceLabel = (sourceType: string | null | undefined, ocrConfidence: number | null | undefined): string =>
  sourceType === 'DIGITAL_PDF_TEXT' ? 'Đọc lớp chữ PDF, không cần OCR' : formatConfidence(ocrConfidence)

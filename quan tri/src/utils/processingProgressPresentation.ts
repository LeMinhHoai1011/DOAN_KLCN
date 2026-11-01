import type { ProcessingStep } from '../services/documentService'

const STEP_LABELS: Record<string, string> = {
  VALIDATION: 'Kiểm tra',
  STORAGE: 'Đọc file',
  TEXT_EXTRACTION: 'Đọc lớp chữ PDF',
  OCR: 'Nhận dạng chữ (OCR)',
  AI_EXTRACTION: 'Phân tích bằng AI',
  PERSISTENCE: 'Lưu kết quả',
}

export const stepLabel = (code: string) => STEP_LABELS[code] ?? code

/** Elapsed seconds of a step; an open step counts up to `now`. Invalid timestamps give null, never a guess. */
export const stepSeconds = (step: Pick<ProcessingStep, 'startedAt' | 'endedAt'>, now: number) => {
  const start = Date.parse(step.startedAt)
  const end = step.endedAt ? Date.parse(step.endedAt) : now
  if (!Number.isFinite(start) || !Number.isFinite(end)) return null
  return Math.max(0, (end - start) / 1000)
}

export const formatSeconds = (seconds: number | null) => seconds == null ? '—' : seconds < 10 ? `${seconds.toFixed(1)} giây` : `${Math.round(seconds)} giây`

/** Total seconds spent per step code, so the report can show where the time went (OCR vs AI). */
export const secondsByStep = (steps: ProcessingStep[], now: number) => {
  const totals: Record<string, number> = {}
  for (const step of steps) { const s = stepSeconds(step, now); if (s != null) totals[step.code] = (totals[step.code] ?? 0) + s }
  return totals
}

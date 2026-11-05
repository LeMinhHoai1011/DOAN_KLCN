import type { ExtractedFieldResponse } from '../services/documentService'
import type { FieldVerificationReport, FieldVerificationView, VerificationStatus } from '../services/fieldVerificationService'

export const verificationStatusLabel: Record<VerificationStatus, string> = {
  VERIFIED: 'Đã kiểm chứng', WARNING: 'Cần đối chiếu', UNVERIFIED: 'Chưa kiểm chứng', MISSING: 'Thiếu',
}

export const aiInputModeLabel = (mode: string | null) => ({
  TEXT: 'AI đọc văn bản OCR', VISION: 'AI đọc trực tiếp từ ảnh', MIXED: 'Trang đọc văn bản, trang đọc ảnh', UNKNOWN: 'Chưa ghi nhận',
} as Record<string, string>)[mode ?? 'UNKNOWN'] ?? 'Chưa ghi nhận'

export const ruleOutcomeLabel = { PASS: 'Đạt', FAIL: 'Không đạt', NOT_APPLICABLE: 'Không áp dụng' } as const

/** Field key → verification; item cells keep their "items[i].x" key. */
export const indexVerifications = (report: FieldVerificationReport | null) =>
  Object.fromEntries((report?.fields ?? []).map(view => [view.fieldKey, view])) as Record<string, FieldVerificationView>

/** Worst status first, so a row shows its most important problem. */
const severity: Record<VerificationStatus, number> = { WARNING: 0, MISSING: 1, UNVERIFIED: 2, VERIFIED: 3 }
export const worstStatus = (views: Array<FieldVerificationView | undefined>) =>
  views.filter((view): view is FieldVerificationView => Boolean(view)).sort((a, b) => severity[a.status] - severity[b.status])[0]

export const needsAttention = (view: FieldVerificationView | undefined) => Boolean(view && view.status !== 'VERIFIED')

/** Adapts evidence to the preview's field focus so the matched words are highlighted on the original image. */
export const toPreviewFocus = (view: FieldVerificationView, label: string): ExtractedFieldResponse | null => {
  const location = view.evidence?.location
  if (!location) return null
  return {
    id: -1 - Math.abs(hash(view.fieldKey)), fieldName: label, fieldValue: view.value, source: view.valueOrigin, confidence: null,
    originalAiValue: view.aiValue, correctedValue: null, manuallyCorrected: view.valueOrigin === 'MANUAL', correctedById: null,
    correctedAt: null, locations: [{ ...location, matchConfidence: view.evidence?.matchScore ?? 1 }],
  }
}

const hash = (value: string) => [...value].reduce((total, char) => (total * 31 + char.charCodeAt(0)) | 0, 7)

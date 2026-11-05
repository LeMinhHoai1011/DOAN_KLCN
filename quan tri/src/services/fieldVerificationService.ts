import api from './api'

export type VerificationStatus = 'VERIFIED' | 'WARNING' | 'UNVERIFIED' | 'MISSING'
export interface VerificationLocation { page: number; x: number; y: number; width: number; height: number; pageWidth: number; pageHeight: number }
export interface FieldVerificationView {
  fieldKey: string; itemIndex: number | null; core: boolean; status: VerificationStatus; score: number | null
  value: string | null; aiValue: string | null; valueOrigin: 'AI' | 'MANUAL'
  evidence: { outcome: string; matchScore: number | null; method: string; matchedText: string | null; location: VerificationLocation | null } | null
  rules: Array<{ ruleId: string; outcome: 'PASS' | 'FAIL' | 'NOT_APPLICABLE'; message: string }>
  reasons: string[]
}
export interface FieldVerificationReport {
  documentId: number; ruleVersion: string | null; aiInputMode: 'TEXT' | 'VISION' | 'MIXED' | 'UNKNOWN' | null
  triggerSource: string | null; verifiedAt: string | null; stale: boolean
  counts: Record<VerificationStatus, number>; scoreNote: string; statusNote: string; fields: FieldVerificationView[]
}

const base = (id: number) => `/api/v1/documents/${id}/field-verifications`
export const fieldVerificationService = {
  get: async (documentId: number) => (await api.get<FieldVerificationReport>(base(documentId))).data,
  recompute: async (documentId: number) => (await api.post<FieldVerificationReport>(`${base(documentId)}/recompute`)).data,
}

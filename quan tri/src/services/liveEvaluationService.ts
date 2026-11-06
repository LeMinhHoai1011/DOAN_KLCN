import api from './api'
import type { FieldScore, ReconciliationScore } from './evaluationService'

export type LabelState = 'PRESENT' | 'ABSENT' | 'UNREADABLE' | 'UNCHECKED'
export type SampleStatus = 'SELECTED' | 'DRAFT' | 'CONFIRMED'
export interface Label { state: LabelState; value: string | null }
export interface LiveSample {
  documentId: number; companyId: number; status: SampleStatus; revision: number; selection: 'MANUAL' | 'RANDOM'
  documentType: string | null; fields: Record<string, Label>; category: Label; itemsState: LabelState
  items: Record<string, string>[]; ocrText: string | null; ocrAssisted: boolean; reconciliation: Record<string, string> | null
  confirmedBy: string | null; confirmedAt: string | null; revisedAfterReveal: boolean
}
export interface SampleSummary {
  documentId: number; fileName: string; documentType: string | null; status: SampleStatus; selection: 'MANUAL' | 'RANDOM'
  stale: boolean; revisedAfterReveal: boolean; selectedAt: string; confirmedAt: string | null
}
export interface Comparison { field: string; truth: string | null; predicted: string | null; outcome: 'CORRECT' | 'INCORRECT' | 'MISSING' | 'SPURIOUS' | 'EXCLUDED' | 'NOT_APPLICABLE' }
export interface Reveal { model: string | null; documentType: string | null; category: string | null; fields: Comparison[]; items: Record<string, string>[] }
export interface Workspace {
  sample: LiveSample; fileName: string; fileType: string; stale: boolean; fieldCatalog: string[]
  reconciliationDraft: Record<string, string> | null; reveal: Reveal | null
}
export interface DraftRequest {
  revision: number; documentType: string | null; fields: Record<string, Label>; category: Label
  itemsState: LabelState; items: Record<string, string>[]; ocrText: string | null
}
export interface LiveScore {
  samples: number; cer: number | null; wer: number | null; ocrSamples: number; averageProcessingMs: number | null
  fields: Record<string, FieldScore>; itemCells: FieldScore; extraItemRows: number; fieldTotals: FieldScore
  fieldMacroPrecision: number | null; fieldMacroRecall: number | null; fieldMacroF1: number | null
  exactDocuments: number; exactEligible: number; exactDocumentRate: number | null; reconciliation: ReconciliationScore | null
  classificationAccuracy: number | null; classificationSamples: number
  costCategoryMetrics?: Record<string, { samples: number; accuracy: number | null; macroPrecision: number | null; macroRecall: number | null; macroF1: number | null }>
}
export interface LiveReport {
  selected: number; labelling: number; confirmed: number; stale: number; scored: number; randomSelected: number; manualSelected: number
  unreadableFields: number; ocrAssisted: number; revisedAfterReveal: number; averageAiMs: number | null
  models: { model: string; samples: number; score: LiveScore }[]; notes: string[]
}

const base = '/api/v1/evaluation-labels'
export const liveEvaluationService = {
  list: async (companyId?: number) => (await api.get<SampleSummary[]>(base, { params: { companyId } })).data,
  report: async (companyId?: number) => (await api.get<LiveReport>(`${base}/report`, { params: { companyId } })).data,
  select: async (documentId: number) => (await api.post<SampleSummary>(`${base}/${documentId}`)).data,
  selectRandom: async (request: { dateFrom?: string; dateTo?: string; companyId?: number; perType: number }) =>
    (await api.post<{ added: number; candidates: number }>(`${base}/random`, request)).data,
  workspace: async (documentId: number) => (await api.get<Workspace>(`${base}/${documentId}`)).data,
  save: async (documentId: number, request: DraftRequest) => (await api.put<Workspace>(`${base}/${documentId}`, request)).data,
  ocrDraft: async (documentId: number) => (await api.post<{ text: string }>(`${base}/${documentId}/ocr-draft`)).data.text,
  confirm: async (documentId: number, revision: number, reconciliation: Record<string, string> | null) =>
    (await api.post<Workspace>(`${base}/${documentId}/confirm`, { revision, reconciliation })).data,
  reopen: async (documentId: number, revision: number) =>
    (await api.post<Workspace>(`${base}/${documentId}/reopen`, undefined, { params: { revision } })).data,
}

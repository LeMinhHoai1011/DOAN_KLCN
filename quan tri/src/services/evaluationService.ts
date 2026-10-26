import api from './api'

export interface FieldScore { expected: number; correct: number; missing: number; incorrect: number; accuracy: number | null; missingRate: number | null; incorrectRate: number | null }
export interface Score {
  costCategoryMetrics?: Record<string, {
    samples: number; correct: number; missing: number; accuracy: number | null
    classes: Record<string, { support: number; predicted: number; truePositive: number; precision: number; recall: number; f1: number }>
    macroPrecision: number | null; macroRecall: number | null; macroF1: number | null
    confusionMatrix: Record<string, Record<string, number>>
  }>
  samples: number; succeeded: number; failed: number; successRate: number | null; failureRate: number | null
  averageProcessingMs: number | null; timedSamples: number; visionFallbackRate: number | null; fallbackObserved: number
  retryRate: number | null; attemptsObserved: number; cer: number | null; wer: number | null; ocrSamples: number
  referenceCharacters: number; characterEdits: number; referenceWords: number; wordEdits: number
  fields: Record<string, FieldScore>; ocrFields: Record<string, FieldScore>; itemCells: FieldScore; extraItemRows: number; extraFields: number
  classificationAccuracy: number | null; classificationSamples: number; macroPrecision: number | null; macroRecall: number | null; macroF1: number | null
  classes: Record<string, { support: number; predicted: number; truePositive: number; precision: number; recall: number; f1: number }>
  confusionMatrix: Record<string, Record<string, number>>; failuresByCode: Record<string, number>
}
export interface EvaluationRun {
  runId: string; datasetId: string; datasetVersion: string; groundTruthSha256: string; bundleSha256: string
  strategy: string; model: string; modelDigest: string; configSha256: string; environment: string; codeRevision: string
  startedAt: string; datasetSamples: number; evaluatedSamples: number; complete: boolean; coverageTags: string[]
  metrics: Score; byDocumentType: Record<string, Score>; byProcessingMode: Record<string, Score>
}
export interface EvaluationReport { status: 'AVAILABLE' | 'NOT_CONFIGURED' | 'NO_TEST_DATA'; runs: EvaluationRun[]; comparable: boolean; comparisonReason: string; metricVersion: string; notes: string[] }
export interface EvaluationFilter { model?: string; strategy?: string; from?: string; to?: string }
export const evaluationService = {
  report: async (params: EvaluationFilter) => (await api.get<EvaluationReport>('/api/v1/evaluation/statistics', { params })).data,
  export: async (params: EvaluationFilter) => (await api.get<Blob>('/api/v1/evaluation/export', { params, responseType: 'blob' })).data,
}

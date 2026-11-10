import type { AiDocumentRow, AiDocumentStatus } from '../services/evaluationService'

export const aiGroupLabel = (group: string) => ({
  ALL: 'Tất cả trường',
  HEADER: 'Thông tin hóa đơn',
  ITEMS: 'Dòng hàng hóa',
  CLASSIFICATION: 'Phân loại & tài khoản',
} as Record<string, string>)[group] ?? group

export const aiDocumentStatusLabel = (status: AiDocumentStatus) => ({
  EXACT: 'AI đúng hoàn toàn',
  CHANGED: 'Kế toán đã sửa',
  SKIPPED: 'Không tính',
} as Record<AiDocumentStatus, string>)[status]

export const aiSkipReasonLabel = (reason: string | null) => reason == null ? '' : ({
  NO_AI_RESULT: 'Duyệt khi chưa có kết quả AI',
  NO_APPROVAL_AFTER_AI: 'AI chạy lại sau khi duyệt',
  UNREADABLE_SNAPSHOT: 'Không đọc được lịch sử',
} as Record<string, string>)[reason] ?? reason

export type AiDocumentFilter = 'ALL' | AiDocumentStatus

export const filterAiDocuments = (rows: AiDocumentRow[], filter: AiDocumentFilter) =>
  filter === 'ALL' ? rows : rows.filter(row => row.status === filter)

export const countAiDocuments = (rows: AiDocumentRow[]) => ({
  ALL: rows.length,
  EXACT: rows.filter(row => row.status === 'EXACT').length,
  CHANGED: rows.filter(row => row.status === 'CHANGED').length,
  SKIPPED: rows.filter(row => row.status === 'SKIPPED').length,
})

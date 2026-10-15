import type { ReconciliationStatus } from '../services/reconciliationService'

export const reconciliationMoney = (value: number | null | undefined) =>
  value == null ? '—' : Number(value).toLocaleString('vi-VN')

export const reconciliationStatusLabel = (status: ReconciliationStatus) => ({
  MATCHED: 'Khớp',
  MISMATCH: 'Sai lệch',
  INSUFFICIENT_DATA: 'Chưa đủ dữ liệu',
  NEED_REVIEW: 'Cần kiểm tra',
})[status]

export const reconciliationStatusClass = (status: ReconciliationStatus) => {
  if (status === 'MATCHED') return 'si-chip border-parsed/30 bg-parsed-surface text-parsed'
  if (status === 'MISMATCH') return 'si-chip border-failed/30 bg-failed-surface text-failed'
  return 'si-chip border-review-surface bg-review-surface text-review'
}

export const hasInsufficientReconciliationData = (statuses: ReconciliationStatus[]) =>
  statuses.includes('INSUFFICIENT_DATA')

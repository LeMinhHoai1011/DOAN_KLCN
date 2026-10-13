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
  if (status === 'MATCHED') return 'rounded-full bg-emerald-100 px-2 py-1 text-xs text-emerald-700'
  if (status === 'MISMATCH') return 'rounded-full bg-red-100 px-2 py-1 text-xs text-red-700'
  return 'rounded-full bg-amber-100 px-2 py-1 text-xs text-amber-700'
}

export const hasInsufficientReconciliationData = (statuses: ReconciliationStatus[]) =>
  statuses.includes('INSUFFICIENT_DATA')

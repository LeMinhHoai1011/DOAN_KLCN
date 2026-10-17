import { instantDateBounds } from './dateTime.ts'
export const evaluationPercent = (value: number | null | undefined) => value == null || !Number.isFinite(value) ? 'Chưa xác định' : `${(value * 100).toFixed(2)}%`
export const evaluationDuration = (value: number | null | undefined) => value == null || !Number.isFinite(value) ? 'Chưa xác định' : `${(value / 1000).toFixed(2)} giây`
export const evaluationError = (status?: number) => status === 403 ? 'Bạn không có quyền xem dữ liệu đánh giá OCR/AI.' : status === 422 ? 'Bộ dữ liệu kiểm thử chưa hợp lệ hoặc không thể đọc. Cần kiểm tra cấu hình và bằng chứng thực nghiệm.' : 'Không thể tải dữ liệu đánh giá OCR/AI. Vui lòng thử lại.'
export const evaluationDateBounds = instantDateBounds

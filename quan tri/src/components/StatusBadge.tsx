import clsx from 'clsx';

interface StatusBadgeProps {
  status: string;
  reviewStatus?: string | null;
}

export interface StatusPresentation { label: string; tone: 'success' | 'warning' | 'info' | 'error' | 'neutral'; rawStatus: string; rawReviewStatus?: string | null; }

export const getStatusPresentation = (status: string, reviewStatus?: string | null): StatusPresentation => {
  const labels: Record<string, Omit<StatusPresentation, 'rawStatus' | 'rawReviewStatus'>> = {
    UPLOADED: { label: 'Đã tải lên', tone: 'neutral' }, PROCESSING: { label: 'Đang xử lý', tone: 'info' }, PROCESSED: { label: 'Đã xử lý', tone: 'success' }, NEED_REVIEW: { label: 'Cần kiểm tra', tone: 'warning' }, COMPLETED: { label: 'Đã xử lý', tone: 'success' }, FAILED: { label: 'Lỗi xử lý', tone: 'error' }, APPROVED: { label: 'Đã duyệt', tone: 'success' }, REJECTED: { label: 'Từ chối', tone: 'error' }, CORRECTED: { label: 'Đã điều chỉnh', tone: 'warning' }, PENDING: { label: 'Chờ kiểm tra', tone: 'neutral' },
  };
  const value = labels[status] || { label: status || 'Không xác định', tone: 'neutral' as const };
  return { ...value, rawStatus: status, rawReviewStatus: reviewStatus };
};

/**
 * Status pill with a state dot (OCR pipeline palette from the design system).
 * Sized for projection: 32px tall with 15px bold label text.
 * The optional review status is exposed through the title tooltip when it differs.
 */
const labelsReview = (value: string) => ({ PENDING: "Chờ duyệt", APPROVED: "Đã duyệt", REJECTED: "Đã từ chối", CORRECTED: "Yêu cầu bổ sung" }[value] || value);

const StatusBadge = ({ status, reviewStatus }: StatusBadgeProps) => {
  const presentation = getStatusPresentation(status, reviewStatus);
  const surfaces: Record<string, string> = {
    success: 'border-parsed/25 bg-parsed-surface text-parsed',
    warning: 'border-review/30 bg-review-surface text-review',
    info: 'border-extracting/25 bg-extracting-surface text-extracting',
    error: 'border-failed/25 bg-failed-surface text-failed',
    neutral: 'border-hairline bg-subtle text-ink-soft',
  };

  const dots: Record<string, string> = {
    success: 'bg-parsed',
    warning: 'bg-review',
    info: 'bg-extracting animate-pulse',
    error: 'bg-failed',
    neutral: 'bg-draft',
  };

  const surface = surfaces[presentation.tone] || surfaces.neutral;

  return (
    <span
      title={`Trạng thái xử lý: ${presentation.rawStatus}${presentation.rawReviewStatus ? ` · Kiểm tra: ${labelsReview(presentation.rawReviewStatus)}` : ''}`}
      className={clsx('inline-flex h-8 items-center gap-2 rounded-full border px-3 text-2xs font-bold whitespace-nowrap', surface)}
    >
      <span className={clsx('h-2.5 w-2.5 shrink-0 rounded-full', dots[presentation.tone] || dots.neutral)}></span>
      {presentation.label}
    </span>
  );
};

export default StatusBadge;

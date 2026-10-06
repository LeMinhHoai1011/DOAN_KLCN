import React from 'react';
import clsx from 'clsx';

interface StatusBadgeProps {
  status: string;
  reviewStatus?: string | null;
}

export interface StatusPresentation { label: string; tone: 'success' | 'warning' | 'info' | 'error' | 'neutral'; rawStatus: string; rawReviewStatus?: string | null; }

export const getStatusPresentation = (status: string, reviewStatus?: string | null): StatusPresentation => {
  const labels: Record<string, Omit<StatusPresentation, 'rawStatus' | 'rawReviewStatus'>> = {
    UPLOADED: { label: 'Đã tải lên', tone: 'neutral' }, PROCESSING: { label: 'Đang xử lý', tone: 'info' }, PROCESSED: { label: 'Đã xử lý', tone: 'success' }, NEED_REVIEW: { label: 'Cần kiểm tra', tone: 'warning' }, COMPLETED: { label: 'Hoàn tất', tone: 'success' }, FAILED: { label: 'Lỗi xử lý', tone: 'error' }, APPROVED: { label: 'Đã duyệt', tone: 'success' }, REJECTED: { label: 'Từ chối', tone: 'error' }, CORRECTED: { label: 'Đã điều chỉnh', tone: 'warning' }, PENDING: { label: 'Chờ kiểm tra', tone: 'neutral' },
  };
  const value = labels[status] || { label: status || 'Không xác định', tone: 'neutral' as const };
  return { ...value, rawStatus: status, rawReviewStatus: reviewStatus };
};

const StatusBadge: React.FC<StatusBadgeProps> = ({ status, reviewStatus }) => {
  const presentation = getStatusPresentation(status, reviewStatus);
  const styles: Record<string, string> = {
    success: 'bg-emerald-100 text-emerald-700 border-emerald-200', warning: 'bg-amber-100 text-amber-700 border-amber-200', info: 'bg-blue-100 text-blue-700 border-blue-200', error: 'bg-red-100 text-red-700 border-red-200', neutral: 'bg-slate-100 text-slate-700 border-slate-200',
  };

  const dots: Record<string, string> = {
    success: 'bg-emerald-500', warning: 'bg-amber-500', info: 'bg-blue-500', error: 'bg-red-500', neutral: 'bg-slate-500',
  };

  const defaultStyle = 'bg-slate-100 text-slate-700 border-slate-200';
  const defaultDot = 'bg-slate-500';

  return (
    <span title={`Processing: ${presentation.rawStatus}${presentation.rawReviewStatus ? ` · Review: ${presentation.rawReviewStatus}` : ''}`} className={clsx("inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border", styles[presentation.tone] || defaultStyle)}>
      <span className={clsx("w-1.5 h-1.5 rounded-full", dots[presentation.tone] || defaultDot)}></span>
      {presentation.label}
    </span>
  );
};

export default StatusBadge;

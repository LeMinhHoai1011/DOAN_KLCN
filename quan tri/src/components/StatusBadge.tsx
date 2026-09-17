import React from 'react';
import clsx from 'clsx';

interface StatusBadgeProps {
  status: string;
}

const StatusBadge: React.FC<StatusBadgeProps> = ({ status }) => {
  const styles: Record<string, string> = {
    'Đã xử lý': 'bg-emerald-100 text-emerald-700 border-emerald-200',
    'Cần kiểm tra': 'bg-amber-100 text-amber-700 border-amber-200',
    'Đang xử lý': 'bg-blue-100 text-blue-700 border-blue-200',
    'Lỗi': 'bg-red-100 text-red-700 border-red-200',
  };

  const dots: Record<string, string> = {
    'Đã xử lý': 'bg-emerald-500',
    'Cần kiểm tra': 'bg-amber-500',
    'Đang xử lý': 'bg-blue-500',
    'Lỗi': 'bg-red-500',
  };

  const defaultStyle = 'bg-slate-100 text-slate-700 border-slate-200';
  const defaultDot = 'bg-slate-500';

  return (
    <span className={clsx("inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border", styles[status] || defaultStyle)}>
      <span className={clsx("w-1.5 h-1.5 rounded-full", dots[status] || defaultDot)}></span>
      {status}
    </span>
  );
};

export default StatusBadge;

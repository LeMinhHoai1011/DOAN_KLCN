import type { ReactNode } from 'react';
import clsx from 'clsx';

export type ToastTone = 'success' | 'error' | 'info' | 'warning';
interface ToastProps { tone?: ToastTone; children: ReactNode; }
const Toast = ({ tone = 'info', children }: ToastProps) => {
  const tones: Record<ToastTone, string> = { success: 'border-parsed-surface bg-parsed-surface/70 text-parsed', error: 'border-failed-surface bg-failed-surface/70 text-failed', info: 'border-extracting-surface bg-extracting-surface/70 text-extracting', warning: 'border-review-surface bg-review-surface/70 text-review' };
  return <div className={clsx('rounded-card shadow-elev2 border px-4 py-3 text-[18px] font-medium', tones[tone])} role="status">{children}</div>;
};
export default Toast;

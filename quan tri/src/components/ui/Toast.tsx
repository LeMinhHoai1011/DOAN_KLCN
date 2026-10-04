import type { ReactNode } from 'react';
import clsx from 'clsx';

export type ToastTone = 'success' | 'error' | 'info' | 'warning';
interface ToastProps { tone?: ToastTone; children: ReactNode; }
const Toast = ({ tone = 'info', children }: ToastProps) => {
  const tones: Record<ToastTone, string> = { success: 'border-emerald-200 bg-emerald-50 text-emerald-800', error: 'border-red-200 bg-red-50 text-red-800', info: 'border-blue-200 bg-blue-50 text-blue-800', warning: 'border-amber-200 bg-amber-50 text-amber-800' };
  return <div className={clsx('rounded-xl border px-4 py-3 text-sm font-medium shadow-lg', tones[tone])} role="status">{children}</div>;
};
export default Toast;

import type { ReactNode } from 'react';
import { Inbox } from 'lucide-react';

interface EmptyStateProps { title?: string; description?: string; action?: ReactNode; }
const EmptyState = ({ title = 'Chưa có dữ liệu', description, action }: EmptyStateProps) => (
  <div className="flex min-h-40 flex-col items-center justify-center px-6 py-10 text-center">
    <Inbox className="mb-3 text-slate-300" size={32} aria-hidden="true" />
    <h3 className="text-sm font-semibold text-slate-700">{title}</h3>
    {description && <p className="mt-1 max-w-md text-sm text-slate-500">{description}</p>}
    {action && <div className="mt-4">{action}</div>}
  </div>
);
export default EmptyState;

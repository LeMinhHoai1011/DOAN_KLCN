import type { ReactNode } from 'react';
import { Inbox } from 'lucide-react';

interface EmptyStateProps { title?: string; description?: string; action?: ReactNode; }
const EmptyState = ({ title = 'Chưa có dữ liệu', description, action }: EmptyStateProps) => (
  <div className="flex min-h-56 flex-col items-center justify-center px-6 py-14 text-center">
    <span className="bg-subtle text-ink-soft mb-4 flex h-16 w-16 items-center justify-center rounded-full" aria-hidden="true">
      <Inbox size={32} />
    </span>
    <h3 className="font-sans text-[24px] leading-[32px] font-bold text-ink">{title}</h3>
    {description && <p className="mt-2 max-w-2xl text-[19px] leading-[30px] text-ink-soft">{description}</p>}
    {action && <div className="mt-5">{action}</div>}
  </div>
);
export default EmptyState;

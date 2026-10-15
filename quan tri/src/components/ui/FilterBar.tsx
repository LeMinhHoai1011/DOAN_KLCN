import type { ReactNode } from 'react';
import { Filter } from 'lucide-react';

interface FilterBarProps { children: ReactNode; actions?: ReactNode; }
const FilterBar = ({ children, actions }: FilterBarProps) => (
  <div className="rounded-card border-hairline bg-surface flex flex-wrap items-end gap-4 border p-4">
    <span className="bg-subtle text-ink-soft mb-0.5 flex h-12 w-12 shrink-0 items-center justify-center self-end rounded-control" aria-hidden="true">
      <Filter size={24} />
    </span>
    <div className="flex flex-1 flex-wrap items-end gap-4">{children}</div>
    {actions && <div className="flex items-center gap-3">{actions}</div>}
  </div>
);
export default FilterBar;

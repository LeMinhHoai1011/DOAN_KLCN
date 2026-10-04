import type { ReactNode } from 'react';
import { Filter } from 'lucide-react';

interface FilterBarProps { children: ReactNode; actions?: ReactNode; }
const FilterBar = ({ children, actions }: FilterBarProps) => (
  <div className="flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 bg-slate-50/70 p-3">
    <Filter className="mb-2 text-slate-400" size={18} aria-hidden="true" />
    <div className="flex flex-1 flex-wrap items-end gap-3">{children}</div>
    {actions && <div className="flex items-center gap-2">{actions}</div>}
  </div>
);
export default FilterBar;

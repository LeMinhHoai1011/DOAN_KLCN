import type { ReactNode } from 'react';
import ContentCard from './ContentCard';
import EmptyState from './EmptyState';
import LoadingState from './LoadingState';

interface DataTableProps { headers: ReactNode; children: ReactNode; colSpan: number; loading?: boolean; empty?: boolean; emptyTitle?: string; }
const DataTable = ({ headers, children, colSpan, loading = false, empty = false, emptyTitle }: DataTableProps) => (
  <ContentCard>
    <div className="overflow-x-auto">
      <table className="si-table">
        <thead><tr>{headers}</tr></thead>
        <tbody>
          {loading ? <tr><td colSpan={colSpan}><LoadingState /></td></tr> : empty ? <tr><td colSpan={colSpan}><EmptyState title={emptyTitle} /></td></tr> : children}
        </tbody>
      </table>
    </div>
  </ContentCard>
);
export default DataTable;

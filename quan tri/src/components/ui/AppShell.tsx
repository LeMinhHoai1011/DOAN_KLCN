import type { ReactNode } from 'react';
import Topbar from '../Topbar';

interface AppShellProps {
  sidebar: ReactNode;
  children: ReactNode;
}

/** Shared visual frame for each role; it intentionally owns no navigation or auth logic. */
const AppShell = ({ sidebar, children }: AppShellProps) => (
  <div className="min-h-screen bg-slate-50 text-slate-900">
    {sidebar}
    <div className="flex min-h-screen flex-col lg:ml-64">
      <Topbar />
      <main className="flex-1 overflow-x-hidden px-4 py-5 sm:px-6 lg:px-8 lg:py-7">{children}</main>
    </div>
  </div>
);

export default AppShell;

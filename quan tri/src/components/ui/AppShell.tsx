import type { ReactNode } from 'react';
import Topbar from '../Topbar';

interface AppShellProps {
  sidebar: ReactNode;
  children: ReactNode;
}

/**
 * Shared visual frame for each role; it intentionally owns no navigation or auth logic.
 *
 * Layout framework (from the SmartInvoice design system):
 * - fixed 280px left navigation rail (white surface, near-black copy)
 * - 72px sticky global search / status bar
 * - fluid content canvas on the white `bg-canvas`
 */
const AppShell = ({ sidebar, children }: AppShellProps) => (
  <div className="min-h-screen bg-canvas text-ink">
    {sidebar}
    <div className="flex min-h-screen flex-col lg:ml-rail">
      <Topbar />
      <main className="mx-auto w-full max-w-[1500px] flex-1 overflow-x-hidden px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
        {children}
      </main>
    </div>
  </div>
);

export default AppShell;

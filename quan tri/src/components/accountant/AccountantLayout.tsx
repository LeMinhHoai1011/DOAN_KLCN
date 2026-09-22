import { Outlet } from 'react-router-dom';
import AccountantSidebar from './AccountantSidebar';
import Topbar from '../Topbar';

const AccountantLayout = () => {
  return (
    <div className="flex min-h-screen bg-slate-50">
      <AccountantSidebar />
      <div className="flex-1 flex flex-col ml-64 transition-all duration-300">
        <Topbar />
        <main className="flex-1 p-6 overflow-x-hidden">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default AccountantLayout;

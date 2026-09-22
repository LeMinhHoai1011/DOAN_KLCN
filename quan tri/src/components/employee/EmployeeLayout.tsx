import { Outlet } from 'react-router-dom';
import EmployeeSidebar from './EmployeeSidebar';
import Topbar from '../Topbar';

const EmployeeLayout = () => {
  return (
    <div className="flex min-h-screen bg-slate-50">
      <EmployeeSidebar />
      <div className="flex-1 flex flex-col ml-64 transition-all duration-300">
        <Topbar />
        <main className="flex-1 p-6 overflow-x-hidden">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default EmployeeLayout;

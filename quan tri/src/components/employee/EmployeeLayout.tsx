import { Outlet } from 'react-router-dom';
import EmployeeSidebar from './EmployeeSidebar';
import AppShell from '../ui/AppShell';

const EmployeeLayout = () => {
  return (
    <AppShell sidebar={<EmployeeSidebar />}><Outlet /></AppShell>
  );
};

export default EmployeeLayout;

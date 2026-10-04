import { Outlet } from 'react-router-dom';
import AdminSidebar from './AdminSidebar';
import AppShell from '../ui/AppShell';

const AdminLayout = () => {
  return (
    <AppShell sidebar={<AdminSidebar />}><Outlet /></AppShell>
  );
};

export default AdminLayout;

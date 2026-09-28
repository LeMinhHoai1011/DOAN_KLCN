import { Outlet } from 'react-router-dom';
import AccountantSidebar from './AccountantSidebar';
import AppShell from '../ui/AppShell';

const AccountantLayout = () => {
  return (
    <AppShell sidebar={<AccountantSidebar />}><Outlet /></AppShell>
  );
};

export default AccountantLayout;

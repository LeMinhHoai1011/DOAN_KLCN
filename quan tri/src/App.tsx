import { useEffect, useState } from 'react';
import { BrowserRouter, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import AccountantLayout from './components/accountant/AccountantLayout';
import AdminLayout from './components/admin/AdminLayout';
import EmployeeLayout from './components/employee/EmployeeLayout';
import Login from './pages/Login';
import Register from './pages/Register';
import AccountantDashboard from './pages/accountant/AccountantDashboard';
import AccountantDocuments from './pages/accountant/AccountantDocuments';
import AccountantDocumentDetail from './pages/accountant/AccountantDocumentDetail';
import AccountantUpload from './pages/accountant/AccountantUpload';
import FinancialTransactions from './pages/accountant/FinancialTransactions';
import AiTest from './pages/accountant/AiTest';
import ClassificationWorkspace from './pages/accountant/ClassificationWorkspace';
import ReconciliationWorkspace from './pages/accountant/ReconciliationWorkspace';
import Storage from './pages/accountant/Storage';
import Reports from './pages/accountant/Reports';
import Settings from './pages/Settings';
import RoleManagement from './pages/admin/RoleManagement';
import AdminDashboard from './pages/admin/AdminDashboard';
import UserManagement from './pages/admin/UserManagement';
import SystemStatistics from './pages/admin/SystemStatistics';
import CatalogManagement from './pages/admin/CatalogManagement';
import ChangePassword from './pages/ChangePassword';
import EmployeeDashboard from './pages/employee/EmployeeDashboard';
import { getDashboardPath, getEffectiveRole, getToken, getCurrentUser, getMe, logout, saveUser } from './services/authService';

function ProtectedRoute() {
  const location = useLocation();
  if (!getToken()) return <Navigate to="/login" replace state={{ from: location }} />;
  return <Outlet />;
}

function RoleGuard({ allowedRoles }: { allowedRoles: string[] }) {
  const user = getCurrentUser();
  const location = useLocation();
  const role = getEffectiveRole(user);
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />;
  if (allowedRoles.length > 0 && (!role || !allowedRoles.includes(role))) {
    return <Navigate to={getDashboardPath(user) || '/login'} replace />;
  }
  return <Outlet />;
}

function App() {
  const [user, setUser] = useState(getCurrentUser());

  useEffect(() => {
    const syncUser = () => setUser(getCurrentUser());
    window.addEventListener('auth-changed', syncUser);
    return () => window.removeEventListener('auth-changed', syncUser);
  }, []);

  useEffect(() => {
    if (!getToken()) return;
    void getMe()
      .then((currentUser) => {
        saveUser(currentUser);
        setUser(currentUser);
      })
      .catch(() => logout());
  }, []);

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/" element={<Navigate to={getDashboardPath(user) || '/login'} replace />} />
          <Route element={<RoleGuard allowedRoles={['ACCOUNTANT']} />}>
            <Route path="/accountant" element={<AccountantLayout />}>
              <Route index element={<Navigate to="/accountant/dashboard" replace />} />
              <Route path="dashboard" element={<AccountantDashboard />} />
              <Route path="documents" element={<AccountantDocuments />} />
              <Route path="documents/:id" element={<AccountantDocumentDetail />} />
              <Route path="upload" element={<AccountantUpload />} />
              <Route path="financial-transactions" element={<FinancialTransactions />} />
              <Route path="ocr-ai" element={<AiTest />} />
              <Route path="storage" element={<Storage />} />
              <Route path="classification" element={<ClassificationWorkspace />} />
              <Route path="reconciliation" element={<ReconciliationWorkspace />} />
              <Route path="reports" element={<Reports />} />
              <Route path="settings" element={<Settings />} />
              <Route path="password" element={<ChangePassword />} />
            </Route>
          </Route>
          <Route element={<RoleGuard allowedRoles={['ADMIN']} />}>
            <Route path="/admin" element={<AdminLayout />}>
              <Route index element={<Navigate to="/admin/dashboard" replace />} />
              <Route path="dashboard" element={<AdminDashboard />} />
              <Route path="users" element={<UserManagement />} />
              <Route path="roles" element={<RoleManagement />} />
              <Route path="documents" element={<AccountantDocuments />} />
              <Route path="documents/:id" element={<AccountantDocumentDetail />} />
              <Route path="upload" element={<AccountantUpload />} />
              <Route path="statistics" element={<SystemStatistics />} />
              <Route path="categories" element={<CatalogManagement />} />
              <Route path="settings" element={<Settings admin />} />
              <Route path="password" element={<ChangePassword />} />
            </Route>
          </Route>
          <Route element={<RoleGuard allowedRoles={['EMPLOYEE', 'USER']} />}>
            <Route path="/employee" element={<EmployeeLayout />}>
              <Route index element={<Navigate to="/employee/dashboard" replace />} />
              <Route path="dashboard" element={<EmployeeDashboard />} />
              <Route path="documents" element={<AccountantDocuments />} />
              <Route path="documents/:id" element={<AccountantDocumentDetail />} />
              <Route element={<RoleGuard allowedRoles={['EMPLOYEE']} />}>
                <Route path="upload" element={<AccountantUpload />} />
              </Route>
              <Route path="settings" element={<Settings />} />
              <Route path="password" element={<ChangePassword />} />
            </Route>
          </Route>
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;

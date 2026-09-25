import { useEffect, useState } from 'react';
import { BrowserRouter, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';

// Layouts theo role - mỗi role có layout/sidebar riêng
import AccountantLayout from './components/accountant/AccountantLayout';
import AdminLayout from './components/admin/AdminLayout';
import EmployeeLayout from './components/employee/EmployeeLayout';

// Shared pages (không có layout)
import Login from './pages/Login';
import Register from './pages/Register';

// Kế toán pages (folder accountant/)
import AccountantDashboard from './pages/accountant/AccountantDashboard';
import AccountantDocuments from './pages/accountant/AccountantDocuments';
import AccountantDocumentDetail from './pages/accountant/AccountantDocumentDetail';
import AccountantUpload from './pages/accountant/AccountantUpload';

// Admin pages (folder admin/)
import RoleManagement from './pages/admin/RoleManagement';
import AdminDashboard from './pages/admin/AdminDashboard';
import UserManagement from './pages/admin/UserManagement';
import SystemStatistics from './pages/admin/SystemStatistics';


// Employee pages (folder employee/)
import EmployeeDashboard from './pages/employee/EmployeeDashboard';

import { getEffectiveRole, getToken, getCurrentUser } from './services/authService';

// ─── Guards ───────────────────────────────────────────────────────────────────

function ProtectedRoute() {
  const location = useLocation();
  if (!getToken()) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Outlet />;
}

function RoleGuard({ allowedRoles }: { allowedRoles: string[] }) {
  const user = getCurrentUser();
  const location = useLocation();
  const role = getEffectiveRole(user);

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (allowedRoles.length > 0 && (!role || !allowedRoles.includes(role))) {
    // Redirect về đúng trang của role thực tế
    if (role === 'ADMIN') return <Navigate to="/admin/dashboard" replace />;
    if (role === 'ACCOUNTANT') return <Navigate to="/accountant/dashboard" replace />;
    return <Navigate to="/employee/dashboard" replace />;
  }

  return <Outlet />;
}

// Trang placeholder cho tính năng chưa có API
const UnavailableFeature = ({ title }: { title: string }) => (
  <div className="p-6">
    <div className="bg-white rounded-2xl border border-slate-200 p-12 text-center shadow-sm">
      <h2 className="text-xl font-semibold text-slate-800 mb-2">{title}</h2>
      <p className="text-slate-500">Backend hiện tại chưa hỗ trợ API cho tính năng này.</p>
    </div>
  </div>
);

// ─── App ──────────────────────────────────────────────────────────────────────

function App() {
  const [user, setUser] = useState(getCurrentUser());

  useEffect(() => {
    const syncUser = () => setUser(getCurrentUser());
    window.addEventListener('auth-changed', syncUser);
    return () => window.removeEventListener('auth-changed', syncUser);
  }, []);

  const role = getEffectiveRole(user);

  return (
    <BrowserRouter>
      <Routes>
        {/* Public routes */}
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        {/* Protected routes (phải đăng nhập) */}
        <Route element={<ProtectedRoute />}>

          {/* Redirect từ "/" về đúng dashboard theo role */}
          <Route path="/" element={
            <Navigate to={
              role === 'ADMIN' ? '/admin/dashboard'
              : role === 'ACCOUNTANT' ? '/accountant/dashboard'
              : '/employee/dashboard'
            } replace />
          } />

          {/* ── KẾ TOÁN (ACCOUNTANT) ── */}
          <Route element={<RoleGuard allowedRoles={['ACCOUNTANT']} />}>
            <Route path="/accountant" element={<AccountantLayout />}>
              <Route index element={<Navigate to="/accountant/dashboard" replace />} />
              <Route path="dashboard" element={<AccountantDashboard />} />
              <Route path="documents" element={<AccountantDocuments />} />
              <Route path="documents/:id" element={<AccountantDocumentDetail />} />
              <Route path="upload" element={<AccountantUpload />} />
              <Route path="ocr-ai" element={<UnavailableFeature title="OCR & AI Tracking" />} />
              <Route path="storage" element={<UnavailableFeature title="Kho lưu trữ" />} />
              <Route path="classification" element={<UnavailableFeature title="Phân loại" />} />
              <Route path="reports" element={<UnavailableFeature title="Báo cáo" />} />
              <Route path="settings" element={<UnavailableFeature title="Cài đặt" />} />
            </Route>
          </Route>

            {/* ── ADMIN ── */}
          <Route element={<RoleGuard allowedRoles={['ADMIN']} />}>
            <Route path="/admin" element={<AdminLayout />}>
              <Route index element={<Navigate to="/admin/dashboard" replace />} />
              <Route path="dashboard" element={<AdminDashboard />} />
              <Route path="users" element={<UserManagement />} />
              <Route path="roles" element={<RoleManagement />} />
              <Route path="documents" element={<AccountantDocuments />} />
              <Route path="documents/:id" element={<AccountantDocumentDetail />} />
              <Route path="statistics" element={<SystemStatistics />} />
              <Route path="settings" element={<UnavailableFeature title="Cài đặt" />} />
            </Route>
          </Route>

          {/* ── NHÂN VIÊN (EMPLOYEE / USER) ── */}
          <Route element={<RoleGuard allowedRoles={['EMPLOYEE']} />}>
            <Route path="/employee" element={<EmployeeLayout />}>
              <Route index element={<Navigate to="/employee/dashboard" replace />} />
              <Route path="dashboard" element={<EmployeeDashboard />} />
              <Route path="documents" element={<AccountantDocuments />} />
              <Route path="documents/:id" element={<AccountantDocumentDetail />} />
              <Route path="upload" element={<AccountantUpload />} />
            </Route>
          </Route>

        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;

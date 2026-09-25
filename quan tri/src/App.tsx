import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import Documents from './pages/Documents';
import DocumentDetail from './pages/DocumentDetail';
import Upload from './pages/Upload';
import Login from './pages/Login';
import Register from './pages/Register';
<<<<<<< Updated upstream
import UserManagement from './pages/UserManagement';
=======

// Kế toán pages (folder accountant/)
import AccountantDashboard from './pages/accountant/AccountantDashboard';
import AccountantDocuments from './pages/accountant/AccountantDocuments';
import AccountantDocumentDetail from './pages/accountant/AccountantDocumentDetail';
import AccountantUpload from './pages/accountant/AccountantUpload';

// Admin pages (folder admin/)
import AdminDashboard from './pages/admin/AdminDashboard';
import RoleManagement from './pages/admin/RoleManagement';
import SystemStatistics from './pages/admin/SystemStatistics';
import UserManagement from './pages/admin/UserManagement';

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
>>>>>>> Stashed changes

function App() {
  return (
    <BrowserRouter>
      <Routes>

        {/* Trang đăng nhập */}
        <Route path="/login" element={<Login />} />

        {/* Trang đăng ký */}
        <Route path="/register" element={<Register />} />

        {/* Khu vực quản trị */}
        <Route path="/" element={<Layout />}>
          <Route index element={<Dashboard />} />

          <Route path="documents" element={<Documents />} />
          <Route path="documents/:id" element={<DocumentDetail />} />
          <Route path="upload" element={<Upload />} />

          <Route
            path="ocr-ai"
            element={<div className="p-6">OCR & AI Tracking placeholder</div>}
          />

<<<<<<< Updated upstream
          <Route
            path="storage"
            element={<div className="p-6">Storage placeholder</div>}
          />
=======
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
>>>>>>> Stashed changes

          <Route
            path="classification"
            element={<div className="p-6">Classification placeholder</div>}
          />

          <Route
            path="reports"
            element={<div className="p-6">Reports placeholder</div>}
          />

          <Route
            path="settings"
            element={<div className="p-6">Settings placeholder</div>}
          />

          {/* Quản trị người dùng */}
          <Route
            path="settings/users"
            element={<UserManagement />}
          />

        </Route>

      </Routes>
    </BrowserRouter>
  );
}

export default App;
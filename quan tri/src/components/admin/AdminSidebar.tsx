import { NavLink, useNavigate } from 'react-router-dom';
import { 
  LayoutDashboard, 
  Users,
  Shield,
  Files,
  Settings,
  LogOut,
  UploadCloud,
  BarChart3,
  BrainCircuit
} from 'lucide-react';
import clsx from 'clsx';
import { getCurrentUser, logout } from '../../services/authService';

const menuItems = [
  { name: 'Dashboard Quản trị', path: '/admin', icon: LayoutDashboard },
  { name: 'Quản lý người dùng', path: '/admin/users', icon: Users },
  { name: 'Quản lý vai trò', path: '/admin/roles', icon: Shield },
  { name: 'Quản lý chứng từ', path: '/admin/documents', icon: Files },
  { name: 'Tải lên chứng từ', path: '/admin/upload', icon: UploadCloud },
  { name: 'Thống kê hệ thống', path: '/admin/statistics', icon: BarChart3 },
];

const AdminSidebar = () => {
  const navigate = useNavigate();
  const user = getCurrentUser();
  const displayName = user?.fullName || user?.username || 'Người dùng';
  const initials = displayName
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <aside className="w-64 bg-[#0B1731] text-slate-300 h-screen flex flex-col fixed left-0 top-0 shadow-xl z-20">
      <div className="h-16 flex items-center px-6 border-b border-slate-700/50">
        <div className="flex items-center gap-2 text-white font-bold text-lg">
          <BrainCircuit className="text-blue-500" size={24} />
          <span>SmartInvoice</span>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto py-6 px-3 flex flex-col gap-1">
        <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2 px-3">Menu chính</div>
        {menuItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) => clsx(
                "flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all duration-200",
                isActive 
                  ? "bg-blue-600/10 text-blue-400 font-medium" 
                  : "hover:bg-slate-800/50 hover:text-white"
              )}
            >
              <Icon size={20} className="text-slate-400" />
              <span>{item.name}</span>
            </NavLink>
          );
        })}

        <div className="mt-8 text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2 px-3">Hệ thống</div>
        <NavLink
          to="/settings"
          className={({ isActive }) => clsx(
            "flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all duration-200",
            isActive 
              ? "bg-blue-600/10 text-blue-400 font-medium" 
              : "hover:bg-slate-800/50 hover:text-white"
          )}
        >
          <Settings size={20} className="text-slate-400" />
          <span>Cài đặt</span>
        </NavLink>
      </div>
      
      <div className="p-4 border-t border-slate-700/50">
        <div className="bg-slate-800/50 rounded-lg p-3 flex items-center gap-3">
          <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-white font-bold text-sm">
            {initials}
          </div>
          <div className="flex-1 overflow-hidden">
            <div className="text-sm font-medium text-white truncate">{displayName}</div>
            <div className="text-xs text-slate-400 truncate">{user?.email || user?.username || ''}</div>
          </div>
          <button
            type="button"
            onClick={handleLogout}
            title="Đăng xuất"
            aria-label="Đăng xuất"
            className="p-1.5 text-slate-400 hover:text-white hover:bg-slate-700 rounded transition-colors"
          >
            <LogOut size={16} />
          </button>
        </div>
      </div>
    </aside>
  );
};

export default AdminSidebar;

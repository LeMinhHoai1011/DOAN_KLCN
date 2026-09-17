import { NavLink } from 'react-router-dom';
import { 
  LayoutDashboard, 
  Files, 
  UploadCloud, 
  BrainCircuit, 
  Database, 
  Tags, 
  BarChart3, 
  Settings,
  User
} from 'lucide-react';
import clsx from 'clsx';

const menuItems = [
  { name: 'Tổng quan', path: '/', icon: LayoutDashboard },
  { name: 'Chứng từ', path: '/documents', icon: Files },
  { name: 'Upload chứng từ', path: '/upload', icon: UploadCloud },
  { name: 'OCR & AI', path: '/ocr-ai', icon: BrainCircuit },
  { name: 'Kho lưu trữ', path: '/storage', icon: Database },
  { name: 'Phân loại', path: '/classification', icon: Tags },
  { name: 'Báo cáo', path: '/reports', icon: BarChart3 },
];

const Sidebar = () => {

  // Lấy thông tin người dùng đang đăng nhập
  const userData = localStorage.getItem("user");
  const user = userData ? JSON.parse(userData) : null;

  // Lấy chữ cái viết tắt từ họ tên
  const getInitials = (fullName: string) => {
    const words = fullName.trim().split(" ");

    if (words.length === 1) {
      return words[0].substring(0, 2).toUpperCase();
    }

    return (
      words[0].charAt(0) +
      words[words.length - 1].charAt(0)
    ).toUpperCase();
  };

  // Hiển thị tên vai trò
  const getRoleName = (role: string) => {
    switch (role) {
      case "ADMIN":
        return "Quản trị viên";

      case "USER":
        return "Người dùng";

      default:
        return role;
    }
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
        
        <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2 px-3">
          Menu chính
        </div>

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

        <div className="mt-8 text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2 px-3">
          Hệ thống
        </div>

        {/* Chỉ ADMIN mới thấy Quản trị người dùng */}
        {user?.role === "ADMIN" && (
          <>
            <NavLink
              to="/settings/users"
              className={({ isActive }) => clsx(
                "flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all duration-200",
                isActive 
                  ? "bg-blue-600/10 text-blue-400 font-medium" 
                  : "hover:bg-slate-800/50 hover:text-white"
              )}
            >
              <User size={20} className="text-slate-400" />
              <span>Quản trị người dùng</span>
            </NavLink>

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
          </>
        )}

      </div>
      
      {/* Thông tin người dùng */}
      <div className="p-4 border-t border-slate-700/50">
        <div className="bg-slate-800/50 rounded-lg p-3 flex items-center gap-3">
          
          <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-white font-bold text-sm">
            {user ? getInitials(user.fullName) : "U"}
          </div>

          <div className="flex-1 overflow-hidden">
            <div className="text-sm font-medium text-white truncate">
              {user ? getRoleName(user.role) : "Người dùng"}
            </div>

            <div className="text-xs text-slate-400 truncate">
              {user ? user.email : ""}
            </div>
          </div>

        </div>
      </div>

    </aside>
  );
};

export default Sidebar;
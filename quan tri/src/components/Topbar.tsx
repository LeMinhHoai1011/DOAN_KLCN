import { Bell, Search } from 'lucide-react';
import { getCurrentUser } from '../services/authService';

const Topbar = () => {
  const user = getCurrentUser();
  const displayName = user?.fullName || user?.username || 'Người dùng';
  const initials = displayName.split(' ').map((part) => part[0]).join('').slice(0, 2).toUpperCase();
  return (
    <header className="sticky top-0 z-10 flex h-16 items-center justify-between border-b border-slate-200 bg-white/95 px-4 shadow-sm backdrop-blur sm:px-6">
      <div className="flex-1 max-w-xl">
        <div className="relative group">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-blue-500 transition-colors" size={20} />
          <input
            type="text" 
            placeholder="Tìm kiếm chưa được kết nối"
            aria-label="Tìm kiếm (chưa được kết nối)"
            disabled
            className="w-full cursor-not-allowed rounded-xl border border-transparent bg-slate-100 py-2 pl-10 pr-4 text-sm text-slate-400 outline-none"
          />
        </div>
      </div>

      <div className="flex items-center gap-4 ml-4">
        <button type="button" disabled title="Thông báo chưa được kết nối" className="relative cursor-not-allowed rounded-full p-2 text-slate-400" aria-label="Thông báo chưa được kết nối">
          <Bell size={20} />
          <span className="absolute right-1.5 top-1.5 h-2.5 w-2.5 rounded-full border-2 border-white bg-slate-300"></span>
        </button>
        <div className="hidden items-center gap-2 border-l border-slate-200 pl-4 sm:flex">
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-100 text-xs font-bold text-blue-700">{initials}</span>
          <span className="max-w-32 truncate text-sm font-medium text-slate-700">{displayName}</span>
        </div>
      </div>
    </header>
  );
};

export default Topbar;

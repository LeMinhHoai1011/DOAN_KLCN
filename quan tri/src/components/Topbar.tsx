import { Search, Bell } from 'lucide-react';

const Topbar = () => {
  return (
    <header className="h-16 bg-white border-b border-slate-200 flex items-center justify-between px-6 sticky top-0 z-10 shadow-sm">
      <div className="flex-1 max-w-xl">
        <div className="relative group">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-blue-500 transition-colors" size={20} />
          <input 
            type="text" 
            placeholder="Tìm kiếm chứng từ, số hóa đơn, nhà cung cấp..." 
            className="w-full bg-slate-100 hover:bg-slate-50 focus:bg-white border border-transparent focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10 rounded-xl py-2 pl-10 pr-4 outline-none transition-all duration-200 text-sm"
          />
        </div>
      </div>

      <div className="flex items-center gap-4 ml-4">
        <button className="relative p-2 rounded-full hover:bg-slate-100 text-slate-600 transition-colors">
          <Bell size={20} />
          <span className="absolute top-1.5 right-1.5 w-2.5 h-2.5 bg-red-500 rounded-full border-2 border-white"></span>
        </button>
      </div>
    </header>
  );
};

export default Topbar;

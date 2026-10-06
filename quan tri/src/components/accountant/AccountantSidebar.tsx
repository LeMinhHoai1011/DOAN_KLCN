import { NavLink, useNavigate } from 'react-router-dom'
import { BarChart3, BrainCircuit, Database, Files, KeyRound, LayoutDashboard, LogOut, Scale, Tags, UploadCloud, WalletCards } from 'lucide-react'
import clsx from 'clsx'
import { getCurrentUser, logout } from '../../services/authService'

const menuItems = [
  { name: 'Tổng quan', path: '/accountant/dashboard', icon: LayoutDashboard },
  { name: 'Chứng từ', path: '/accountant/documents', icon: Files },
  { name: 'Upload chứng từ', path: '/accountant/upload', icon: UploadCloud },
  { name: 'Thu / Chi', path: '/accountant/financial-transactions', icon: WalletCards },
  { name: 'Kho lưu trữ', path: '/accountant/storage', icon: Database },
  { name: 'Phân loại', path: '/accountant/classification', icon: Tags },
  { name: 'Đối soát', path: '/accountant/reconciliation', icon: Scale },
  { name: 'Báo cáo', path: '/accountant/reports', icon: BarChart3 },
]

const AccountantSidebar = () => {
  const navigate = useNavigate()
  const user = getCurrentUser()
  const displayName = user?.fullName || user?.username || 'Người dùng'
  const initials = displayName.split(' ').filter(Boolean).map(part => part[0]).join('').slice(0, 2).toUpperCase()
  const handleLogout = () => { logout(); navigate('/login', { replace: true }) }
  const linkClass = ({ isActive }: { isActive: boolean }) => clsx('flex items-center gap-3 rounded-lg px-3 py-2.5 transition-colors', isActive ? 'bg-blue-600/10 font-medium text-blue-400' : 'hover:bg-slate-800/50 hover:text-white')
  return <aside className="fixed left-0 top-0 z-20 hidden h-screen w-64 flex-col bg-[#0B1731] text-slate-300 shadow-xl lg:flex">
    <div className="flex h-16 items-center gap-2 border-b border-slate-700/50 px-6 text-lg font-bold text-white"><BrainCircuit className="text-blue-500" size={24} />SmartInvoice</div>
    <nav className="flex flex-1 flex-col gap-1 overflow-y-auto px-3 py-6"><div className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Menu chính</div>
      {menuItems.map(item => <NavLink key={item.path} to={item.path} className={linkClass}><item.icon size={20} className="text-slate-400" /><span>{item.name}</span></NavLink>)}
      <div className="mb-2 mt-8 px-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Hệ thống</div>
      <NavLink to="/accountant/password" className={linkClass}><KeyRound size={20} className="text-slate-400" /><span>Đổi mật khẩu</span></NavLink>
    </nav>
    <div className="border-t border-slate-700/50 p-4"><div className="flex items-center gap-3 rounded-lg bg-slate-800/50 p-3"><div className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-600 text-sm font-bold text-white">{initials}</div><div className="min-w-0 flex-1"><div className="truncate text-sm font-medium text-white">{displayName}</div><div className="truncate text-xs text-slate-400">{user?.email || user?.username || ''}</div></div><button type="button" onClick={handleLogout} title="Đăng xuất" aria-label="Đăng xuất" className="rounded p-1.5 text-slate-400 hover:bg-slate-700 hover:text-white"><LogOut size={16} /></button></div></div>
  </aside>
}
export default AccountantSidebar

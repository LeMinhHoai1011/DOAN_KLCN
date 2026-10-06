import { getCurrentUser } from '../services/authService'

const Topbar = () => {
  const user = getCurrentUser()
  const displayName = user?.fullName || user?.username || 'Người dùng'
  const initials = displayName.split(' ').filter(Boolean).map(part => part[0]).join('').slice(0, 2).toUpperCase()
  return <header className="sticky top-0 z-10 flex h-16 items-center justify-end border-b border-slate-200 bg-white/95 px-4 shadow-sm backdrop-blur sm:px-6">
    <div className="flex items-center gap-2">
      <span className="flex h-9 w-9 items-center justify-center rounded-full bg-blue-100 text-xs font-bold text-blue-700">{initials}</span>
      <div className="hidden sm:block"><div className="max-w-48 truncate text-sm font-medium text-slate-700">{displayName}</div><div className="text-xs text-slate-500">{user?.role || user?.roles?.[0] || ''}</div></div>
    </div>
  </header>
}
export default Topbar

import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Bell, Search } from 'lucide-react'
import { getCurrentUser, getEffectiveRole } from '../services/authService'
import dashboardService from '../services/dashboardService'

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Quản trị viên',
  ACCOUNTANT: 'Kế toán viên',
  EMPLOYEE: 'Nhân viên',
  USER: 'Người dùng',
}

/**
 * 72px sticky global search bar, dựng theo Stitch (dashboard_ke_toan/dashboard_quan_tri):
 * ô tìm kiếm + phím tắt ⌘K, chip trạng thái hệ thống, chuông chứng từ cần kiểm tra và khối tài khoản.
 */
const Topbar = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const user = getCurrentUser()
  const role = getEffectiveRole(user)
  const basePath = role === 'ADMIN' ? '/admin' : role === 'EMPLOYEE' || role === 'USER' ? '/employee' : '/accountant'
  const displayName = user?.fullName || user?.username || 'Người dùng'
  const initials = displayName.split(' ').filter(Boolean).map((part) => part[0]).join('').slice(0, 2).toUpperCase()
  const [searchTerm, setSearchTerm] = useState('')
  const [pendingReview, setPendingReview] = useState<number | null>(null)
  const [systemOnline, setSystemOnline] = useState<boolean | null>(null)
  const searchInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    let active = true
    void dashboardService.getDashboardStatistics()
      .then((statistics) => { if (active) { setPendingReview(statistics.totalReviewRequired); setSystemOnline(true) } })
      .catch(() => { if (active) { setPendingReview(null); setSystemOnline(false) } })
    return () => { active = false }
  }, [location.pathname])


  useEffect(() => {
    const handleShortcut = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        searchInput.current?.focus()
      }
    }
    window.addEventListener('keydown', handleShortcut)
    return () => window.removeEventListener('keydown', handleShortcut)
  }, [])

  const handleSearch = (event: FormEvent) => {
    event.preventDefault()
    navigate(`${basePath}/documents`, { state: { documentSearch: searchTerm.trim() } })
  }

  return (
    <header className="sticky top-0 z-30 flex h-topbar items-center gap-4 border-b border-hairline bg-surface/95 px-4 backdrop-blur-sm sm:px-6 lg:px-8">
      <form onSubmit={handleSearch} role="search" className="flex w-full max-w-lg min-w-0 items-center gap-2.5 rounded-control border border-hairline bg-surface px-4 py-2 transition-all focus-within:border-brand-600 focus-within:bg-surface focus-within:ring-2 focus-within:ring-brand-600/20">
        <Search size={24} className="shrink-0 text-ink-soft" />
        <input
          ref={searchInput}
          value={searchTerm}
          onChange={(event) => setSearchTerm(event.target.value)}
          type="search"
          aria-label="Tìm kiếm chứng từ"
          placeholder="Tìm chứng từ, số hóa đơn, MST..."
          className="w-full min-w-0 border-none bg-transparent text-[19px] text-ink outline-none placeholder:text-ink-muted"
        />
        <kbd className="hidden rounded border border-hairline bg-subtle px-2 py-1 font-mono text-[17px] font-semibold text-ink-soft sm:inline-block">⌘K</kbd>
      </form>

      <div className="ml-auto flex items-center gap-4">
        <span className="hidden items-center gap-2 rounded-full border border-hairline bg-subtle px-3.5 py-1.5 text-[18px] font-semibold text-ink-soft xl:inline-flex">
          <span className={`h-2.5 w-2.5 rounded-full ${systemOnline === false ? 'bg-failed' : systemOnline === true ? 'bg-parsed' : 'bg-review'}`} />
          <span className="text-ink">{systemOnline === false ? 'Mất kết nối hệ thống' : systemOnline === true ? 'Hệ thống ổn định' : 'Đang kiểm tra hệ thống'}</span>
        </span>
        <span className="hidden h-6 w-px bg-hairline-strong xl:block" />
        <button
          type="button"
          onClick={() => navigate(`${basePath}/documents`, { state: { processingStatus: 'NEED_REVIEW' } })}
          title={pendingReview === null ? 'Chứng từ cần kiểm tra' : `${pendingReview} chứng từ cần kiểm tra`}
          aria-label="Chứng từ cần kiểm tra"
          className="text-ink-soft hover:bg-subtle hover:text-ink relative flex h-12 w-12 items-center justify-center rounded-md transition-colors"
        >
          <Bell size={26} />
          {pendingReview !== null && pendingReview > 0 && (
            <span className="bg-brand-600 absolute top-0.5 right-0.5 min-w-6 rounded-full px-1 text-center font-mono text-[17px] leading-6 font-bold text-white">
              {pendingReview > 99 ? '99+' : pendingReview}
            </span>
          )}
        </button>
        <span className="hidden h-6 w-px bg-hairline-strong sm:block" />
        <div className="flex items-center gap-3">
          <span className="bg-brand-100 text-brand-800 flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-[18px] font-bold">{initials}</span>
          <div className="hidden min-w-0 sm:block">
            <div className="max-w-56 truncate text-[18px] leading-[24px] font-semibold text-ink">{displayName}</div>
            <div className="max-w-56 truncate text-2xs text-ink-soft">{ROLE_LABELS[role ?? ''] || user?.role || user?.roles?.[0] || ''}</div>
          </div>
        </div>
      </div>
    </header>
  )
}

export default Topbar

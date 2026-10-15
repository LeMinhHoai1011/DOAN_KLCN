import type { ElementType } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import clsx from 'clsx'
import { LogOut } from 'lucide-react'
import { getCurrentUser, logout } from '../../services/authService'

export interface NavRailItem {
  name: string
  path: string
  icon: ElementType
  /** Match the route exactly (used for index-style links). */
  end?: boolean
}

export interface NavRailSection {
  label: string
  items: NavRailItem[]
}

interface NavRailProps {
  /** Role chip rendered next to the wordmark, e.g. "Kế toán". */
  roleLabel: string
  /** Grouped navigation, e.g. `[{ label: 'Nghiệp vụ', items: [...] }]`. */
  sections: NavRailSection[]
  /** Secondary links pinned above the account block, e.g. Đổi mật khẩu. */
  footerItems?: NavRailItem[]
}

const railLinkClass = ({ isActive }: { isActive: boolean }) => clsx(
  'flex items-center gap-3 rounded-md px-3 py-2.5 text-[18px] font-semibold transition-colors',
  isActive
    ? 'bg-brand-600 shadow-elev1 text-white'
    : 'text-ink-soft hover:bg-subtle hover:text-ink',
)

const railFooterLinkClass = ({ isActive }: { isActive: boolean }) => clsx(
  'flex items-center gap-3 rounded-md px-3 py-2 text-[17px] font-semibold transition-colors',
  isActive
    ? 'bg-subtle text-ink'
    : 'text-ink-soft hover:bg-subtle hover:text-ink',
)

/**
 * Fixed 280px navigation rail (SmartInvoice design system).
 * Owns navigation, the role chip and the account block (identity + logout) only —
 * routing guards and data loading stay in the role layouts / pages.
 *
 * Projection pass: the rail is a light surface with near-black copy instead of
 * the former navy panel, so the whole shell survives a bright projector.
 */
const NavRail = ({ roleLabel, sections, footerItems = [] }: NavRailProps) => {
  const navigate = useNavigate()
  const user = getCurrentUser()
  const displayName = user?.fullName || user?.username || 'Người dùng'
  const initials = displayName.split(' ').filter(Boolean).map((part) => part[0]).join('').slice(0, 2).toUpperCase()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <aside className="fixed top-0 left-0 z-50 hidden h-screen w-rail flex-col justify-between border-r border-hairline bg-surface py-5 text-ink select-none lg:flex">
      <div className="flex min-h-0 flex-col">
        <div className="flex h-14 shrink-0 items-center gap-3 px-5">
          <span className="bg-brand-600 shadow-elev1 flex h-9 w-9 items-center justify-center rounded-md text-[20px] font-bold text-white">S</span>
          <span className="font-sans text-[22px] font-bold tracking-tight text-ink">SmartInvoice</span>
          <span className="ml-auto rounded border border-hairline bg-subtle px-2 py-0.5 text-2xs font-semibold text-ink-soft">{roleLabel}</span>
        </div>

        <nav className="flex min-h-0 flex-1 flex-col overflow-y-auto px-3 pb-3">
          {sections.map((section) => (
            <div key={section.label} className="flex flex-col gap-1">
              <span className="mt-6 mb-2 px-3 text-2xs font-bold tracking-wider text-ink-muted uppercase">{section.label}</span>
              {section.items.map((item) => {
                const Icon = item.icon
                return (
                  <NavLink key={item.path} to={item.path} end={item.end} className={railLinkClass}>
                    <Icon size={22} className="shrink-0" />
                    <span className="truncate">{item.name}</span>
                  </NavLink>
                )
              })}
            </div>
          ))}
        </nav>
      </div>

      <div className="flex shrink-0 flex-col px-3">
        <div className="mx-2 my-3 h-px bg-hairline" />
        {footerItems.map((item) => {
          const Icon = item.icon
          return (
            <NavLink key={item.path} to={item.path} end={item.end} className={railFooterLinkClass}>
              <Icon size={20} className="shrink-0" />
              <span className="truncate">{item.name}</span>
            </NavLink>
          )
        })}
        <div className="mt-3 flex items-center justify-between gap-2 rounded-md border border-hairline bg-subtle p-2.5">
          <div className="flex min-w-0 items-center gap-3">
            <span className="bg-brand-100 text-brand-800 flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-[17px] font-bold">{initials}</span>
            <div className="flex min-w-0 flex-col">
              <span className="truncate text-[17px] leading-[24px] font-semibold text-ink">{displayName}</span>
              <span className="truncate text-2xs text-ink-muted">{user?.email || user?.username || roleLabel}</span>
            </div>
          </div>
          <button type="button" onClick={handleLogout} title="Đăng xuất" aria-label="Đăng xuất" className="text-ink-soft hover:text-failed flex items-center justify-center rounded p-1.5 transition-colors">
            <LogOut size={20} />
          </button>
        </div>
      </div>
    </aside>
  )
}

export default NavRail

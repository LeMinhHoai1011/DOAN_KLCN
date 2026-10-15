import type { ElementType, ReactNode } from 'react'

export interface AuthPoint {
  icon: ElementType
  title: string
  description: string
}

interface AuthShellProps {
  /** Card heading, e.g. "Đăng nhập hệ thống". */
  title: string
  /** One-line supporting copy under the heading. */
  subtitle: string
  /** Form / body of the card. */
  children: ReactNode
  /** Small print under the card, e.g. the link to the sibling screen. */
  footer?: ReactNode
  /** Capability list rendered on the dark authority panel (desktop only). */
  points?: AuthPoint[]
}

/**
 * Split-screen authentication shell (SmartInvoice design system).
 * Left: light "structural authority" panel with the wordmark, matching the
 * white `NavRail`. Right: the form card on the white canvas.
 * Both `/login` and `/register` render through this shell so the two entry
 * screens stay pixel-identical in chrome.
 */
const AuthShell = ({ title, subtitle, children, footer, points = [] }: AuthShellProps) => (
  <div className="min-h-screen lg:grid lg:grid-cols-[minmax(0,1fr)_520px]">
    <aside className="border-hairline bg-subtle relative hidden flex-col justify-between border-r px-12 py-12 text-ink-soft lg:flex">
      <div className="flex items-center gap-3">
        <span className="bg-brand-600 shadow-elev1 flex h-10 w-10 items-center justify-center rounded-md text-[20px] font-bold text-white">S</span>
        <span className="font-sans text-[22px] font-bold tracking-tight text-ink">SmartInvoice</span>
      </div>

      <div className="max-w-xl">
        <h1 className="font-sans text-[40px] leading-[1.15] font-bold tracking-tight text-ink">
          Số hóa chứng từ,
          <br />
          kiểm soát ngay từ đầu vào.
        </h1>
        <p className="mt-5 text-[20px] leading-[32px] text-ink-soft">
          Nền tảng quản trị hóa đơn và chứng từ nội bộ: trích xuất tự động, phân loại theo nghiệp vụ và kiểm soát phê duyệt tập trung.
        </p>

        {points.length > 0 && (
          <ul className="mt-10 flex flex-col gap-6">
            {points.map((point) => {
              const Icon = point.icon
              return (
                <li key={point.title} className="flex gap-4">
                  <span className="border-hairline bg-surface text-brand-700 flex h-11 w-11 shrink-0 items-center justify-center rounded-md border">
                    <Icon size={24} />
                  </span>
                  <div className="flex flex-col">
                    <span className="text-[20px] font-semibold text-ink">{point.title}</span>
                    <span className="mt-1 text-[18px] leading-[28px] text-ink-soft">{point.description}</span>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </div>

      <p className="text-2xs text-ink-muted">© {new Date().getFullYear()} SmartInvoice · Hệ thống quản lý chứng từ nội bộ</p>
    </aside>

    <main className="bg-canvas flex min-h-screen flex-col items-center justify-center px-5 py-10">
      <div className="w-full max-w-[440px]">
        <div className="mb-6 flex items-center gap-3 lg:hidden">
          <span className="bg-brand-600 flex h-10 w-10 items-center justify-center rounded-md text-[20px] font-bold text-white">S</span>
          <span className="font-sans text-[22px] font-bold tracking-tight text-ink">SmartInvoice</span>
        </div>

        <div className="si-panel p-8">
          <h1 className="font-sans text-[28px] leading-[36px] font-bold tracking-tight text-ink">{title}</h1>
          <p className="mt-2 text-[18px] leading-[28px] text-ink-soft">{subtitle}</p>

          <div className="mt-7">{children}</div>
        </div>

        {footer && <div className="mt-6 text-center text-[18px] text-ink-soft">{footer}</div>}
      </div>
    </main>
  </div>
)

export default AuthShell

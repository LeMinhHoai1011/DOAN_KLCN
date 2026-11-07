import { BarChart3, ClipboardCheck, Files, KeyRound, LayoutDashboard, UploadCloud, WalletCards } from 'lucide-react'
import NavRail from '../ui/NavRail'

// Menu bám đúng điều hướng của bộ thiết kế Stitch (dashboard_ke_toan/code.html):
// Tổng quan · Chứng từ · Tải lên chứng từ · Xử lý & kiểm tra · Thu / Chi · Báo cáo (+ Đổi mật khẩu).
const AccountantSidebar = () => (
  <NavRail
    roleLabel="Kế toán"
    sections={[
      {
        label: 'Nghiệp vụ',
        items: [
          { name: 'Tổng quan', path: '/accountant/dashboard', icon: LayoutDashboard },
          { name: 'Chứng từ', path: '/accountant/documents', icon: Files },
          { name: 'Tải lên chứng từ', path: '/accountant/upload', icon: UploadCloud },
          { name: 'Xử lý & kiểm tra', path: '/accountant/review', icon: ClipboardCheck },
          { name: 'Thu / Chi', path: '/accountant/financial-transactions', icon: WalletCards },
          { name: 'Báo cáo', path: '/accountant/reports', icon: BarChart3 },
        ],
      },
    ]}
    footerItems={[{ name: 'Đổi mật khẩu', path: '/accountant/password', icon: KeyRound }]}
  />
)

export default AccountantSidebar


import { Files, KeyRound, LayoutDashboard, UploadCloud } from 'lucide-react'
import NavRail, { type NavRailItem } from '../ui/NavRail'
import { getCurrentUser, getEffectiveRole } from '../../services/authService'

const EmployeeSidebar = () => {
  const role = getEffectiveRole(getCurrentUser())
  const menuItems: NavRailItem[] = [
    { name: 'Tổng quan', path: '/employee/dashboard', icon: LayoutDashboard },
    { name: 'Chứng từ của tôi', path: '/employee/documents', icon: Files },
    { name: 'Tải lên chứng từ', path: '/employee/upload', icon: UploadCloud },
  ]

  return (
    <NavRail
      roleLabel="Nhân viên"
      sections={[
        {
          label: 'Nghiệp vụ',
          // Chỉ nhân viên (EMPLOYEE) được phép tải chứng từ lên.
          items: menuItems.filter((item) => item.path !== '/employee/upload' || role === 'EMPLOYEE'),
        },
      ]}
      footerItems={[{ name: 'Đổi mật khẩu', path: '/employee/password', icon: KeyRound }]}
    />
  )
}

export default EmployeeSidebar

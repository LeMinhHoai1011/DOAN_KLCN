import { BarChart3, KeyRound, LayoutDashboard, Shield, Tags, Users } from 'lucide-react'
import NavRail from '../ui/NavRail'

// Menu bám đúng điều hướng của bộ thiết kế Stitch (dashboard_quan_tri/code.html): một nhóm
// "Phân hệ Quản trị" gồm Dashboard quản trị · Người dùng · Vai trò & quyền · Danh mục loại chứng từ · Thống kê.
const AdminSidebar = () => (
  <NavRail
    roleLabel="Quản trị"
    sections={[
      {
        label: 'Phân hệ Quản trị',
        items: [
          { name: 'Dashboard quản trị', path: '/admin/dashboard', icon: LayoutDashboard, end: true },
          { name: 'Quản lý người dùng', path: '/admin/users', icon: Users, end: true },
          { name: 'Quản lý vai trò', path: '/admin/roles', icon: Shield, end: true },
          { name: 'Danh mục loại chứng từ', path: '/admin/categories', icon: Tags, end: true },
          { name: 'Thống kê hệ thống', path: '/admin/statistics', icon: BarChart3, end: true },
        ],
      },
    ]}
    footerItems={[{ name: 'Đổi mật khẩu', path: '/admin/password', icon: KeyRound, end: true }]}
  />
)

export default AdminSidebar


import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Search, UserPlus, X } from 'lucide-react'
import api from '../../services/api'
import { getErrorMessage } from '../../services/authService'
import type { User } from '../../services/authService'
import roleService from '../../services/roleService'
import type { Role } from '../../services/roleService'
import ContentCard from '../../components/ui/ContentCard'
import EmptyState from '../../components/ui/EmptyState'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'

type ManagedUser = User & { status: 'ACTIVE' | 'INACTIVE' }

const UserManagement = () => {
  const [users, setUsers] = useState<ManagedUser[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [isCreating, setIsCreating] = useState(false)
  const [form, setForm] = useState({ username: '', password: '', fullName: '', email: '', role: 'EMPLOYEE' })
  const [query, setQuery] = useState('')

  const loadData = async () => {
    try {
      const [usersRes, rolesRes] = await Promise.all([
        api.get<ManagedUser[]>('/api/v1/users'),
        roleService.getRoles(),
      ])
      setUsers(usersRes.data)
      setRoles(rolesRes.data)
    } catch {
      setError('Không thể tải danh sách người dùng hoặc vai trò')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadData()
  }, [])

  useEffect(() => {
    if (!isCreating) return
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsCreating(false)
    }
    window.addEventListener('keydown', handleEscape)
    return () => window.removeEventListener('keydown', handleEscape)
  }, [isCreating])

  const createUser = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    try {
      await api.post('/api/v1/users', form)
      setForm({ username: '', password: '', fullName: '', email: '', role: 'EMPLOYEE' })
      setIsCreating(false)
      await loadData()
    } catch (error: unknown) {
      setError(getErrorMessage(error, 'Không thể tạo tài khoản. Kiểm tra lại thông tin đã nhập.'))
    }
  }

  const updateStatus = async (user: ManagedUser, newStatus: string) => {
    try {
      await api.put(`/api/v1/users/${user.id}`, {
        status: newStatus,
        companyId: user.companyId ?? null,
      })
      await loadData()
    } catch (error: unknown) {
      setError(getErrorMessage(error, 'Không thể cập nhật trạng thái tài khoản.'))
    }
  }

  const assignRole = async (user: ManagedUser, roleCode: string) => {
    try {
      const selectedRole = roles.find(r => r.code === roleCode)
      if (selectedRole) {
        await api.put(`/api/v1/users/${user.id}/roles`, {
          roleIds: [selectedRole.id]
        })
      }
      await loadData()
    } catch {
      setError('Không thể cập nhật quyền')
    }
  }

  const visibleUsers = users.filter((user) => {
    const value = query.trim().toLocaleLowerCase()
    return !value || user.fullName.toLocaleLowerCase().includes(value) || user.username.toLocaleLowerCase().includes(value) || user.email.toLocaleLowerCase().includes(value)
  })

  const activeCount = users.filter(user => user.status === 'ACTIVE').length

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        eyebrow="Quản trị / Người dùng"
        title="Quản lý người dùng"
        description="Tạo tài khoản, gán vai trò và khóa/mở khóa bằng API hiện có."
        actions={
          <button type="button" onClick={() => setIsCreating(true)} className="si-btn-primary">
            <UserPlus size={20} />Thêm người dùng
          </button>
        }
      />

      {error && <ErrorState message={error} onRetry={() => void loadData()} />}

      {isCreating && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 p-4" onMouseDown={() => setIsCreating(false)}>
          <form
            onSubmit={createUser}
            role="dialog"
            aria-modal="true"
            aria-labelledby="create-user-title"
            onMouseDown={(event) => event.stopPropagation()}
            className="si-panel w-full max-w-2xl p-6 shadow-elev3"
          >
            <div className="mb-5 flex items-start justify-between gap-4">
              <div>
                <h2 id="create-user-title" className="si-section-title">Thêm người dùng mới</h2>
                <p className="si-section-subtitle mt-1">Nhập thông tin tài khoản và vai trò ban đầu.</p>
              </div>
              <button type="button" onClick={() => setIsCreating(false)} aria-label="Đóng hộp thoại" className="text-ink-muted hover:bg-subtle hover:text-ink rounded p-1.5 transition-colors">
                <X size={22} />
              </button>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <label className="grid gap-1">
                <span className="si-field-label">Tên đăng nhập</span>
                <input required placeholder="username" value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} className="si-input" />
              </label>
              <label className="grid gap-1">
                <span className="si-field-label">Mật khẩu</span>
                <input required type="password" placeholder="••••••••" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} className="si-input" />
              </label>
              <label className="grid gap-1">
                <span className="si-field-label">Họ và tên</span>
                <input required placeholder="Nguyễn Văn A" value={form.fullName} onChange={(event) => setForm({ ...form, fullName: event.target.value })} className="si-input" />
              </label>
              <label className="grid gap-1">
                <span className="si-field-label">Email</span>
                <input required type="email" placeholder="email@congty.vn" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} className="si-input" />
              </label>
              <label className="grid gap-1 md:col-span-2">
                <span className="si-field-label">Vai trò ban đầu</span>
                <select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value })} className="si-select">
                  {roles.filter(r => r.active).map(r => <option key={r.id} value={r.code}>{r.name} ({r.code})</option>)}
                </select>
              </label>
            </div>
            <div className="border-hairline mt-6 flex justify-end gap-3 border-t pt-4">
              <button type="button" onClick={() => setIsCreating(false)} className="si-btn-secondary">Hủy</button>
              <button type="submit" className="si-btn-primary">Tạo tài khoản</button>
            </div>
          </form>
        </div>
      )}

      <ContentCard>
        <div className="border-hairline flex flex-wrap items-center gap-3 border-b p-4">
          <div className="relative flex-1 sm:max-w-md">
            <Search size={20} className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-ink-muted" />
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm theo tên, username hoặc email..." className="si-input !pl-9" />
          </div>
          <div className="flex items-center gap-2">
            <span className="si-chip border-hairline bg-subtle text-ink-muted">{users.length} tài khoản</span>
            <span className="si-chip border-parsed/30 bg-parsed-surface text-parsed">{activeCount} hoạt động</span>
            {users.length - activeCount > 0 && (
              <span className="si-chip border-failed/30 bg-failed-surface text-failed">{users.length - activeCount} đã khóa</span>
            )}
          </div>
        </div>
        {isLoading ? (
          <LoadingState label="Đang tải người dùng..." />
        ) : visibleUsers.length === 0 ? (
          <EmptyState
            title={users.length === 0 ? 'Chưa có người dùng' : 'Không tìm thấy người dùng'}
            description="Thử thay đổi từ khóa tìm kiếm hoặc tạo tài khoản mới."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="si-table">
              <thead>
                <tr>
                  <th>Người dùng</th>
                  <th>Email</th>
                  <th>Vai trò</th>
                  <th>Trạng thái</th>
                  <th className="text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {visibleUsers.map((user) => (
                  <tr key={user.id}>
                    <td>
                      <div className="font-medium text-ink">{user.fullName}</div>
                      <div className="si-num text-[17px] text-ink-muted">@{user.username}</div>
                    </td>
                    <td className="text-ink-soft">{user.email}</td>
                    <td>
                      <select value={user.role || ''} onChange={(event) => void assignRole(user, event.target.value)} className="si-select !py-1 !text-[17px]">
                        <option value="" disabled>Chọn vai trò...</option>
                        {roles.map(r => <option key={r.id} value={r.code}>{r.name}</option>)}
                      </select>
                    </td>
                    <td>
                      <span className={user.status === 'ACTIVE' ? 'si-chip border-parsed/30 bg-parsed-surface text-parsed' : 'si-chip border-failed/30 bg-failed-surface text-failed'}>
                        {user.status === 'ACTIVE' ? 'Đang hoạt động' : 'Đã khóa'}
                      </span>
                    </td>
                    <td className="text-right">
                      <button type="button" onClick={() => void updateStatus(user, user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE')} className="si-btn-secondary !px-2.5 !py-1 !text-[17px]">
                        {user.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </ContentCard>
    </div>
  )
}

export default UserManagement

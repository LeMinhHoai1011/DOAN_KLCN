import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { UserPlus, X } from 'lucide-react'
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
const assignableLegacyRoles = new Set(['ADMIN', 'ACCOUNTANT', 'EMPLOYEE', 'USER'])

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

  return (
    <div className="space-y-6">
      <PageHeader title="Quản lý người dùng" description="Tạo tài khoản, gán vai trò và khóa/mở khóa bằng API hiện có." actions={<button type="button" onClick={() => setIsCreating(true)} className="flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-medium text-white shadow-sm hover:bg-blue-700"><UserPlus size={18} /> Thêm người dùng</button>} />

      {error && <ErrorState message={error} onRetry={() => void loadData()} />}

      {isCreating && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4" onMouseDown={() => setIsCreating(false)}>
          <form onSubmit={createUser} role="dialog" aria-modal="true" aria-labelledby="create-user-title" onMouseDown={(event) => event.stopPropagation()} className="w-full max-w-2xl rounded-lg bg-white p-6 shadow-xl">
            <div className="mb-5 flex items-start justify-between gap-4">
              <div>
                <h2 id="create-user-title" className="text-lg font-semibold text-slate-800">Tao nguoi dung moi</h2>
                <p className="mt-1 text-sm text-slate-500">Nhap thong tin tai khoan va vai tro ban dau.</p>
              </div>
              <button type="button" onClick={() => setIsCreating(false)} aria-label="Dong" title="Dong" className="rounded p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800">
                <X size={20} />
              </button>
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              {(['username', 'password', 'fullName', 'email'] as const).map((field) => (
                <input key={field} required type={field === 'password' ? 'password' : field === 'email' ? 'email' : 'text'} placeholder={field} value={form[field]} onChange={(event) => setForm({ ...form, [field]: event.target.value })} className="rounded-lg border border-slate-200 px-3 py-2" />
              ))}
              <select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value })} className="rounded-lg border border-slate-200 px-3 py-2">
                {roles.filter(r => assignableLegacyRoles.has(r.code)).map(r => <option key={r.id} value={r.code}>{r.name} ({r.code})</option>)}
              </select>
            </div>
            <div className="mt-6 flex justify-end gap-3">
              <button type="button" onClick={() => setIsCreating(false)} className="rounded-lg border border-slate-300 px-4 py-2 text-slate-700 hover:bg-slate-50">Huy</button>
              <button type="submit" className="rounded-lg bg-emerald-600 px-4 py-2 text-white hover:bg-emerald-700">Tao tai khoan</button>
            </div>
          </form>
        </div>
      )}

      <ContentCard>
        <div className="border-b border-slate-200 bg-slate-50 p-4"><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm theo tên, username hoặc email..." className="w-full max-w-md rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" /></div>
        {isLoading ? <LoadingState label="Đang tải người dùng..." /> : visibleUsers.length === 0 ? <EmptyState title={users.length === 0 ? 'Chưa có người dùng' : 'Không tìm thấy người dùng'} description="Thử thay đổi từ khóa tìm kiếm hoặc tạo tài khoản mới." /> : (
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500"><tr><th className="p-4">Nguoi dung</th><th className="p-4">Email</th><th className="p-4">Role</th><th className="p-4">Trang thai</th><th className="p-4">Thao tac</th></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {visibleUsers.map((user) => <tr key={user.id} className="hover:bg-slate-50">
                <td className="p-4"><div className="font-medium text-slate-800">{user.fullName}</div><div className="text-slate-500">@{user.username}</div></td>
                <td className="p-4">{user.email}</td>
                <td className="p-4">
                  <select value={user.role || ''} onChange={(event) => void assignRole(user, event.target.value)} className="rounded border border-slate-200 px-2 py-1">
                    <option value="" disabled>Chon role...</option>
                    {roles.map(r => <option key={r.id} value={r.code}>{r.name}</option>)}
                  </select>
                </td>
                <td className="p-4"><span className={user.status === 'ACTIVE' ? 'text-emerald-600' : 'text-red-600'}>{user.status === 'ACTIVE' ? 'Dang hoat dong' : 'Da khoa'}</span></td>
                <td className="p-4"><button type="button" onClick={() => void updateStatus(user, user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE')} className="text-blue-600 hover:underline">{user.status === 'ACTIVE' ? 'Khoa' : 'Mo khoa'}</button></td>
              </tr>)}
            </tbody>
          </table>
        )}
      </ContentCard>
    </div>
  )
}

export default UserManagement

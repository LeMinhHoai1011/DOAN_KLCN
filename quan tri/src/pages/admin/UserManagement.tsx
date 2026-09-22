import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { UserPlus, Users } from 'lucide-react'
import api from '../../services/api'
import type { User } from '../../services/authService'

type ManagedUser = User & { status: 'ACTIVE' | 'INACTIVE' }

const UserManagement = () => {
  const [users, setUsers] = useState<ManagedUser[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [isCreating, setIsCreating] = useState(false)
  const [form, setForm] = useState({ username: '', password: '', fullName: '', email: '', role: 'EMPLOYEE' })

  const loadUsers = async () => {
    try {
      const { data } = await api.get<ManagedUser[]>('/api/v1/users')
      setUsers(data)
    } catch {
      setError('Không thể tải danh sách người dùng')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadUsers()
  }, [])

  const createUser = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    try {
      await api.post('/api/v1/users', form)
      setForm({ username: '', password: '', fullName: '', email: '', role: 'EMPLOYEE' })
      setIsCreating(false)
      await loadUsers()
    } catch {
      setError('Không thể tạo người dùng. Kiểm tra username hoặc email đã tồn tại.')
    }
  }

  const updateUser = async (user: ManagedUser, changes: Partial<Pick<ManagedUser, 'role' | 'status'>>) => {
    try {
      await api.put(`/api/v1/users/${user.id}`, {
        role: changes.role || user.role,
        status: changes.status || user.status,
      })
      await loadUsers()
    } catch {
      setError('Không thể cập nhật quyền hoặc trạng thái tài khoản')
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Quản lý người dùng</h1>
          <p className="text-slate-500 mt-1">Tạo tài khoản, phân quyền và khóa/mở khóa người dùng</p>
        </div>
        <button type="button" onClick={() => setIsCreating((value) => !value)} className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
          <UserPlus size={18} /> Thêm người dùng
        </button>
      </div>

      {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-red-700">{error}</div>}

      {isCreating && (
        <form onSubmit={createUser} className="grid gap-3 rounded-xl border border-slate-200 bg-white p-5 shadow-sm md:grid-cols-2">
          {(['username', 'password', 'fullName', 'email'] as const).map((field) => (
            <input key={field} required type={field === 'password' ? 'password' : field === 'email' ? 'email' : 'text'} placeholder={field} value={form[field]} onChange={(event) => setForm({ ...form, [field]: event.target.value })} className="rounded-lg border border-slate-200 px-3 py-2" />
          ))}
          <select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value })} className="rounded-lg border border-slate-200 px-3 py-2">
            <option value="ACCOUNTANT">ACCOUNTANT</option>
            <option value="EMPLOYEE">EMPLOYEE</option>
            <option value="ADMIN">ADMIN</option>
          </select>
          <button type="submit" className="rounded-lg bg-emerald-600 px-4 py-2 text-white hover:bg-emerald-700">Tạo tài khoản</button>
        </form>
      )}

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
        {isLoading ? <div className="p-10 text-center text-slate-500">Đang tải...</div> : users.length === 0 ? <div className="p-10 text-center text-slate-500"><Users className="mx-auto mb-2" />Chưa có người dùng</div> : (
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500"><tr><th className="p-4">Người dùng</th><th className="p-4">Email</th><th className="p-4">Role</th><th className="p-4">Trạng thái</th><th className="p-4">Thao tác</th></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {users.map((user) => <tr key={user.id}>
                <td className="p-4"><div className="font-medium text-slate-800">{user.fullName}</div><div className="text-slate-500">@{user.username}</div></td>
                <td className="p-4">{user.email}</td>
                <td className="p-4"><select value={user.role || 'EMPLOYEE'} onChange={(event) => void updateUser(user, { role: event.target.value })} className="rounded border border-slate-200 px-2 py-1"><option>ADMIN</option><option>ACCOUNTANT</option><option>EMPLOYEE</option><option>USER</option></select></td>
                <td className="p-4"><span className={user.status === 'ACTIVE' ? 'text-emerald-600' : 'text-red-600'}>{user.status === 'ACTIVE' ? 'Đang hoạt động' : 'Đã khóa'}</span></td>
                <td className="p-4"><button type="button" onClick={() => void updateUser(user, { status: user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' })} className="text-blue-600 hover:underline">{user.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}</button></td>
              </tr>)}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

export default UserManagement

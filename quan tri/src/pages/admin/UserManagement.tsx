import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { UserPlus, Users } from 'lucide-react'
import api from '../../services/api'
import type { User } from '../../services/authService'
import roleService from '../../services/roleService'
import type { Role } from '../../services/roleService'

type ManagedUser = User & { status: 'ACTIVE' | 'INACTIVE' }
type Company = { id: number; companyName: string; taxCode: string | null }
const assignableLegacyRoles = new Set(['ADMIN', 'ACCOUNTANT', 'EMPLOYEE', 'USER'])

const UserManagement = () => {
  const [users, setUsers] = useState<ManagedUser[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [companies, setCompanies] = useState<Company[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [isCreating, setIsCreating] = useState(false)
  const [form, setForm] = useState({ username: '', password: '', fullName: '', email: '', role: 'EMPLOYEE', companyId: '' })

  const loadData = async () => {
    try {
      const [usersRes, rolesRes, companiesRes] = await Promise.all([
        api.get<ManagedUser[]>('/api/v1/users'),
        roleService.getRoles(),
        api.get<Company[]>('/api/v1/companies'),
      ])
      setUsers(usersRes.data)
      setRoles(rolesRes.data)
      setCompanies(companiesRes.data)
    } catch {
      setError('Khong the tai danh sach nguoi dung hoac vai tro')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadData()
  }, [])

  const createUser = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    try {
      await api.post('/api/v1/users', { ...form, companyId: form.companyId ? Number(form.companyId) : null })
      setForm({ username: '', password: '', fullName: '', email: '', role: 'EMPLOYEE', companyId: '' })
      setIsCreating(false)
      await loadData()
    } catch {
      setError('Khong the tao nguoi dung. Kiem tra username hoac email da ton tai.')
    }
  }

  const updateStatus = async (user: ManagedUser, newStatus: string) => {
    try {
      await api.put(`/api/v1/users/${user.id}`, {
        role: user.role, // legacy
        status: newStatus,
        companyId: user.companyId ?? null,
      })
      await loadData()
    } catch {
      setError('Khong the cap nhat trang thai')
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
      setError('Khong the cap nhat quyen')
    }
  }

  const assignCompany = async (user: ManagedUser, companyId: string) => {
    try {
      await api.put(`/api/v1/users/${user.id}`, {
        role: user.role,
        status: user.status,
        companyId: companyId ? Number(companyId) : null,
      })
      await loadData()
    } catch {
      setError('Khong the cap nhat cong ty')
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Quan ly nguoi dung</h1>
          <p className="text-slate-500 mt-1">Tao tai khoan, phan quyen va khoa/mo khoa</p>
        </div>
        <button type="button" onClick={() => setIsCreating((value) => !value)} className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
          <UserPlus size={18} /> Them nguoi dung
        </button>
      </div>

      {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-red-700">{error}</div>}

      {isCreating && (
        <form onSubmit={createUser} className="grid gap-3 rounded-xl border border-slate-200 bg-white p-5 shadow-sm md:grid-cols-2">
          {(['username', 'password', 'fullName', 'email'] as const).map((field) => (
            <input key={field} required type={field === 'password' ? 'password' : field === 'email' ? 'email' : 'text'} placeholder={field} value={form[field]} onChange={(event) => setForm({ ...form, [field]: event.target.value })} className="rounded-lg border border-slate-200 px-3 py-2" />
          ))}
          <select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value })} className="rounded-lg border border-slate-200 px-3 py-2">
            {roles.filter(r => assignableLegacyRoles.has(r.code)).map(r => <option key={r.id} value={r.code}>{r.name} ({r.code})</option>)}
          </select>
          <select value={form.companyId} onChange={(event) => setForm({ ...form, companyId: event.target.value })} className="rounded-lg border border-slate-200 px-3 py-2">
            <option value="">Chưa gán công ty</option>
            {companies.map(company => <option key={company.id} value={company.id}>{company.companyName}{company.taxCode ? ` (${company.taxCode})` : ''}</option>)}
          </select>
          <button type="submit" className="rounded-lg bg-emerald-600 px-4 py-2 text-white hover:bg-emerald-700">Tao tai khoan</button>
        </form>
      )}

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
        {isLoading ? <div className="p-10 text-center text-slate-500">Dang tai...</div> : users.length === 0 ? <div className="p-10 text-center text-slate-500"><Users className="mx-auto mb-2" />Chua co nguoi dung</div> : (
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-slate-500"><tr><th className="p-4">Nguoi dung</th><th className="p-4">Email</th><th className="p-4">Role</th><th className="p-4">Cong ty</th><th className="p-4">Trang thai</th><th className="p-4">Thao tac</th></tr></thead>
            <tbody className="divide-y divide-slate-100">
              {users.map((user) => <tr key={user.id}>
                <td className="p-4"><div className="font-medium text-slate-800">{user.fullName}</div><div className="text-slate-500">@{user.username}</div></td>
                <td className="p-4">{user.email}</td>
                <td className="p-4">
                  <select value={user.role || ''} onChange={(event) => void assignRole(user, event.target.value)} className="rounded border border-slate-200 px-2 py-1">
                    <option value="" disabled>Chon role...</option>
                    {roles.map(r => <option key={r.id} value={r.code}>{r.name}</option>)}
                  </select>
                </td>
                <td className="p-4">
                  <select value={user.companyId ?? ''} onChange={(event) => void assignCompany(user, event.target.value)} className="rounded border border-slate-200 px-2 py-1">
                    <option value="">Chưa gán</option>
                    {companies.map(company => <option key={company.id} value={company.id}>{company.companyName}</option>)}
                  </select>
                </td>
                <td className="p-4"><span className={user.status === 'ACTIVE' ? 'text-emerald-600' : 'text-red-600'}>{user.status === 'ACTIVE' ? 'Dang hoat dong' : 'Da khoa'}</span></td>
                <td className="p-4"><button type="button" onClick={() => void updateStatus(user, user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE')} className="text-blue-600 hover:underline">{user.status === 'ACTIVE' ? 'Khoa' : 'Mo khoa'}</button></td>
              </tr>)}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

export default UserManagement

import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import api from '../../services/api'
import { getErrorMessage } from '../../services/authService'
import type { User } from '../../services/authService'
import roleService from '../../services/roleService'
import type { Role } from '../../services/roleService'
import { isAssignableRole, isCreatableAccountRole, sameSet } from '../../utils/rbacDraft'
import ContentCard from '../../components/ui/ContentCard'
import EmptyState from '../../components/ui/EmptyState'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'

type ManagedUser = User & { status: 'ACTIVE' | 'INACTIVE' }
type UserAccess = {
  assignedRoles: { id: number; code: string; name: string; active: boolean }[]
  permissions: string[]
  adminBypass: boolean
}
const emptyForm = { username: '', password: '', fullName: '', email: '', role: 'EMPLOYEE' }

export default function UserManagement() {
  const [users, setUsers] = useState<ManagedUser[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [access, setAccess] = useState<Record<number, UserAccess>>({})
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [isCreating, setIsCreating] = useState(false)
  const [form, setForm] = useState(emptyForm)
  const [query, setQuery] = useState('')
  const [editingUser, setEditingUser] = useState<ManagedUser | null>(null)
  const [roleDraft, setRoleDraft] = useState<number[]>([])
  const [busy, setBusy] = useState(false)
  const pending = useRef(false)
  const originalIds = editingUser ? (access[editingUser.id]?.assignedRoles ?? []).map(r => r.id) : []
  const rolesDirty = !!editingUser && roleDraft.length === 1 && !sameSet(originalIds, roleDraft)

  const loadData = async () => {
    const [usersRes, rolesRes] = await Promise.all([api.get<ManagedUser[]>('/api/v1/users'), roleService.getRoles()])
    const entries = await Promise.all(usersRes.data.map(async user => {
      const response = await api.get<UserAccess>(`/api/v1/users/${user.id}/access`)
      return [user.id, response.data] as const
    }))
    setUsers(usersRes.data); setRoles(rolesRes.data); setAccess(Object.fromEntries(entries))
  }
  const reload = async () => {
    if (pending.current || (rolesDirty && !window.confirm('Bỏ thay đổi vai trò chưa lưu và tải lại dữ liệu?'))) return
    pending.current = true; setBusy(true); setError(''); setNotice('')
    try { await loadData(); setEditingUser(null) }
    catch (e) { setError(getErrorMessage(e, 'Không thể tải danh sách tài khoản và quyền.')) }
    finally { pending.current = false; setBusy(false); setIsLoading(false) }
  }
  useEffect(() => { void reload() }, [])
  useEffect(() => {
    const guard = (event: BeforeUnloadEvent) => {
      if (rolesDirty || busy || (isCreating && JSON.stringify(form) !== JSON.stringify(emptyForm))) { event.preventDefault(); event.returnValue = '' }
    }
    window.addEventListener('beforeunload', guard)
    return () => window.removeEventListener('beforeunload', guard)
  }, [rolesDirty, busy, isCreating, form])

  const createUser = async (event: FormEvent) => {
    event.preventDefault()
    if (pending.current || !roles.some(r => r.code === form.role && isCreatableAccountRole(r))) return
    pending.current = true; setBusy(true); setError(''); setNotice('')
    try {
      await api.post('/api/v1/users', form)
      setForm({ ...emptyForm }); setIsCreating(false)
      setNotice('Đã tạo tài khoản.')
      try { await loadData() } catch (e) { setError(getErrorMessage(e, 'Tài khoản đã tạo nhưng chưa tải lại được danh sách. Hãy tải lại dữ liệu.')) }
    } catch (e) { setError(getErrorMessage(e, 'Không thể tạo tài khoản.')) }
    finally { pending.current = false; setBusy(false) }
  }
  const updateStatus = async (user: ManagedUser) => {
    if (pending.current) return
    if (access[user.id]?.adminBypass && !window.confirm(`Xác nhận ${user.status === 'ACTIVE' ? 'khóa' : 'mở khóa'} tài khoản quản trị @${user.username}?`)) return
    pending.current = true; setBusy(true); setError(''); setNotice('')
    try {
      await api.put(`/api/v1/users/${user.id}`, { status: user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE', companyId: user.companyId ?? null })
      setNotice('Đã cập nhật trạng thái tài khoản.')
      try { await loadData() } catch (e) { setError(getErrorMessage(e, 'Đã cập nhật nhưng chưa tải lại được danh sách.')) }
    } catch (e) { setError(getErrorMessage(e, 'Không thể cập nhật trạng thái tài khoản.')) }
    finally { pending.current = false; setBusy(false) }
  }
  const assignRoles = async () => {
    if (!editingUser || pending.current || !rolesDirty || roleDraft.length !== 1
      || !roles.some(role => role.id === roleDraft[0] && isAssignableRole(role))) return
    const wasAdmin = access[editingUser.id]?.adminBypass
    if (wasAdmin && !window.confirm(`Xác nhận thay quyền ADMIN của @${editingUser.username} bằng vai trò đã chọn?`)) return
    pending.current = true; setBusy(true); setError(''); setNotice('')
    try {
      await api.put(`/api/v1/users/${editingUser.id}/roles`, { roleIds: roleDraft, expectedRoleIds: originalIds })
      setEditingUser(null); setNotice('Đã cập nhật vai trò tài khoản.')
      try { await loadData() } catch (e) { setError(getErrorMessage(e, 'Vai trò đã lưu nhưng chưa tải lại được danh sách.')) }
    } catch (e) { setError(getErrorMessage(e, 'Không thể cập nhật vai trò. Các lựa chọn chưa lưu được giữ lại.')) }
    finally { pending.current = false; setBusy(false) }
  }
  const visible = users.filter(u => `${u.fullName ?? ''} ${u.username} ${u.email ?? ''}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()))
  const historical = editingUser && access[editingUser.id]?.assignedRoles.some(r => r.code === 'USER')

  return <div className="flex flex-col gap-5" aria-busy={busy}>
    <PageHeader eyebrow="Quản trị / Người dùng" title="Quản lý người dùng"
      description="Tạo tài khoản, chọn vai trò Kế toán hoặc Nhân viên và xem quyền hiệu lực."
      actions={<button className="si-btn-primary" disabled={busy || isLoading || !!editingUser || isCreating} onClick={() => { setNotice(''); setIsCreating(true) }}>Thêm người dùng</button>} />
    {error && <ErrorState message={error} onRetry={() => void reload()} />}
    {notice && <p role="status" className="rounded-control bg-parsed-surface p-3 text-parsed">{notice}</p>}
    <button className="si-btn-secondary self-start" disabled={busy || isCreating} onClick={() => void reload()}>Tải lại dữ liệu</button>
    {isCreating && <ContentCard><form onSubmit={createUser} className="p-5">
      <h2 className="si-section-title mb-4">Tạo tài khoản</h2>
      <fieldset disabled={busy} className="grid gap-4 md:grid-cols-2">
        <label className="grid gap-1">Tên đăng nhập<input required minLength={3} maxLength={100} className="si-input" value={form.username} onChange={e => setForm({ ...form, username: e.target.value })} /></label>
        <label className="grid gap-1">Mật khẩu<input required type="password" minLength={6} maxLength={100} autoComplete="new-password" className="si-input" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} /></label>
        <label className="grid gap-1">Họ và tên<input required maxLength={150} className="si-input" value={form.fullName} onChange={e => setForm({ ...form, fullName: e.target.value })} /></label>
        <label className="grid gap-1">Email<input required type="email" maxLength={150} className="si-input" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></label>
        <label className="grid gap-1 md:col-span-2">Vai trò ban đầu<select className="si-select" value={form.role} onChange={e => setForm({ ...form, role: e.target.value })}>
          <option value="" disabled>Chọn vai trò…</option>{roles.filter(isCreatableAccountRole).map(r => <option key={r.id} value={r.code}>{r.name} ({r.code})</option>)}
        </select></label>
      </fieldset>
      <p className="my-3 text-sm text-ink-muted">Chỉ chọn một vai trò: Kế toán hoặc Nhân viên.</p>
      <div className="flex justify-end gap-2"><button type="button" disabled={busy} className="si-btn-secondary" onClick={() => { setIsCreating(false); setForm({ ...emptyForm }) }}>Hủy</button>
        <button disabled={busy || !roles.some(r => r.code === form.role && isCreatableAccountRole(r))} className="si-btn-primary">{busy ? 'Đang tạo…' : 'Tạo tài khoản'}</button></div>
    </form></ContentCard>}
    {editingUser && <ContentCard><div className="grid gap-4 p-5">
      <h2 className="si-section-title">Vai trò của @{editingUser.username}</h2>
      {historical ? <p>USER lịch sử được bảo vệ; không được thay đổi danh sách vai trò.</p> : <fieldset disabled={busy} className="grid gap-3 md:grid-cols-2">
        <legend className="mb-3 text-sm text-ink-muted">Chọn một vai trò: Kế toán hoặc Nhân viên. Khi lưu, vai trò đã chọn sẽ thay thế các vai trò hiện tại.</legend>
        {roles.filter(isAssignableRole).map(role => <label key={role.id} className="flex items-center gap-2">
          <input type="radio" name="account-role" checked={roleDraft[0] === role.id} onChange={() => { setRoleDraft([role.id]); setNotice('') }} />{role.name} ({role.code})
        </label>)}
      </fieldset>}
      {access[editingUser.id]?.assignedRoles.some(r => !r.active) && <p className="text-sm text-ink-muted">Vai trò đã vô hiệu hóa sẽ bị loại khỏi danh sách khi lưu.</p>}
      <div className="flex justify-end gap-2"><button className="si-btn-secondary" disabled={busy} onClick={() => { setEditingUser(null); setError('') }}>Hủy</button>
        <button className="si-btn-primary" disabled={busy || !rolesDirty || roleDraft.length !== 1 || !!historical} onClick={() => void assignRoles()}>{busy ? 'Đang lưu…' : 'Lưu vai trò'}</button></div>
    </div></ContentCard>}
    <ContentCard><div className="flex flex-wrap items-center gap-3 p-4">
      <input className="si-input sm:max-w-md" placeholder="Tìm tên, username hoặc email…" value={query} onChange={e => setQuery(e.target.value)} />
      <span>{users.length} tài khoản · {users.filter(u => u.status === 'ACTIVE').length} hoạt động</span>
    </div>
      {isLoading ? <LoadingState label="Đang tải tài khoản…" /> : visible.length === 0 ? <EmptyState title="Không tìm thấy tài khoản" description="Thử thay đổi từ khóa hoặc tạo tài khoản mới." /> :
        <div className="overflow-x-auto"><table className="si-table"><thead><tr><th>Người dùng</th><th>Email</th><th>Vai trò & quyền</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
          <tbody>{visible.map(user => <tr key={user.id}>
            <td><span className="block font-medium">{user.fullName}</span><span className="text-sm text-ink-muted">@{user.username}</span></td><td>{user.email}</td>
            <td><div className="flex flex-wrap gap-1">{access[user.id]?.assignedRoles.map(role => <span key={role.id} className="si-chip border-hairline bg-subtle text-ink-muted">{role.name} ({role.code}){!role.active && ' · Tạm dừng'}</span>)}</div>
              {access[user.id]?.assignedRoles.length === 0 && <span>Chưa có vai trò</span>}
              <details className="mt-2 text-sm"><summary className="cursor-pointer">Quyền hiệu lực (chỉ đọc)</summary>
                {user.status !== 'ACTIVE' && <p>Tài khoản đã khóa, không được xác thực.</p>}
                {access[user.id]?.adminBypass && <p>ADMIN được truy cập tại các API có ngoại lệ quản trị.</p>}
                <p className="my-2 text-ink-muted">Hợp permission hoạt động từ các vai trò hoạt động; API vẫn kiểm tra vai trò và phạm vi dữ liệu.</p>
                <ul className="grid gap-1">{access[user.id]?.permissions.slice().sort().map(code => <li key={code}>{code}</li>)}</ul>
                {access[user.id]?.permissions.length === 0 && <p>Không có permission hoạt động.</p>}
              </details></td>
            <td>{user.status === 'ACTIVE' ? 'Đang hoạt động' : 'Đã khóa'}</td>
            <td><div className="grid gap-2">
              <button className="si-btn-secondary" disabled={busy || isCreating} onClick={() => {
                if (rolesDirty && !window.confirm('Bỏ thay đổi vai trò chưa lưu?')) return
                const current = access[user.id]; if (!current) return
                setEditingUser(user)
                setRoleDraft(current.assignedRoles.length === 1 && isAssignableRole(current.assignedRoles[0]) ? [current.assignedRoles[0].id] : [])
                setError(''); setNotice('')
              }}>Cấu hình vai trò</button>
              <button className="si-btn-secondary" disabled={busy || !!editingUser || isCreating} onClick={() => void updateStatus(user)}>{user.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}</button>
            </div></td>
          </tr>)}</tbody></table></div>}
    </ContentCard>
  </div>
}

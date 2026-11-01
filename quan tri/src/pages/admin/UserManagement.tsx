import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Users, ShieldCheck, LockKeyhole, UserRound, MoreVertical, Eye, Shield, RefreshCw, Plus } from 'lucide-react'
import api from '../../services/api'
import { getErrorMessage } from '../../services/authService'
import type { User } from '../../services/authService'
import roleService from '../../services/roleService'
import type { Role } from '../../services/roleService'
import { isAssignableRole, isCreatableAccountRole, sameSet } from '../../utils/rbacDraft'
import ContentCard from '../../components/ui/ContentCard'
import FormModal from '../../components/ui/FormModal'
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
  const [statusFilter, setStatusFilter] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [detailUser, setDetailUser] = useState<ManagedUser | null>(null)
  const [menuUser, setMenuUser] = useState<number | null>(null)
  const [menuPosition, setMenuPosition] = useState({ top: 0, left: 0 })
  const menuRef = useRef<HTMLDivElement>(null)
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
  useEffect(() => { setPage(1) }, [query, statusFilter, roleFilter, pageSize])
  useEffect(() => {
    if (menuUser === null) return
    const dismiss = (event: MouseEvent) => { if (!menuRef.current?.contains(event.target as Node)) setMenuUser(null) }
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') setMenuUser(null) }
    document.addEventListener('mousedown', dismiss)
    document.addEventListener('keydown', escape)
    return () => { document.removeEventListener('mousedown', dismiss); document.removeEventListener('keydown', escape) }
  }, [menuUser])
  useEffect(() => {
    const guard = (event: BeforeUnloadEvent) => {
      if (rolesDirty || busy || (isCreating && JSON.stringify(form) !== JSON.stringify(emptyForm))) { event.preventDefault(); event.returnValue = '' }
    }
    window.addEventListener('beforeunload', guard)
    return () => window.removeEventListener('beforeunload', guard)
  }, [rolesDirty, busy, isCreating, form])

  const closeCreate = () => {
    if (pending.current) return
    setIsCreating(false); setForm({ ...emptyForm }); setError('')
  }

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
  const visible = users.filter(u => `${u.fullName ?? ''} ${u.username} ${u.email ?? ''}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())
    && (!statusFilter || u.status === statusFilter)
    && (!roleFilter || access[u.id]?.assignedRoles.some(role => role.code === roleFilter)))
  const pageCount = Math.max(1, Math.ceil(visible.length / pageSize))
  const currentPage = Math.min(page, pageCount)
  const pageUsers = visible.slice((currentPage - 1) * pageSize, currentPage * pageSize)
  const openAssignment = (user: ManagedUser) => {
    if (pending.current || (rolesDirty && !window.confirm('Bỏ thay đổi vai trò chưa lưu?'))) return
    const current = access[user.id]; if (!current) return
    setMenuUser(null); setDetailUser(null); setEditingUser(user)
    setRoleDraft(current.assignedRoles.length === 1 && isAssignableRole(current.assignedRoles[0]) ? [current.assignedRoles[0].id] : [])
    setError(''); setNotice('')
  }
  const historical = editingUser && access[editingUser.id]?.assignedRoles.some(r => r.code === 'USER')

  return <div className="flex flex-col gap-5" aria-busy={busy}>
    <PageHeader eyebrow="Quản trị / Người dùng" title="Quản lý người dùng"
      description="Tạo tài khoản, chọn vai trò Kế toán hoặc Nhân viên và xem quyền hiệu lực."
      actions={<button className="si-btn-primary" disabled={busy || isLoading || !!editingUser || isCreating} onClick={() => { setNotice(''); setError(''); setIsCreating(true) }}><Plus size={20} />Thêm người dùng</button>} />
    {error && !isCreating && !editingUser && <ErrorState message={error} onRetry={() => void reload()} />}
    {notice && <p role="status" className="rounded-control bg-parsed-surface p-3 text-parsed">{notice}</p>}
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {[
        { label: 'Tổng tài khoản', value: users.length, icon: Users },
        { label: 'Đang hoạt động', value: users.filter(u => u.status === 'ACTIVE').length, icon: ShieldCheck },
        { label: 'Đã khóa', value: users.filter(u => u.status === 'INACTIVE').length, icon: LockKeyhole },
        { label: 'Chưa gán vai trò', value: users.filter(u => access[u.id]?.assignedRoles.length === 0).length, icon: UserRound },
      ].map(({ label, value, icon: Icon }) => <ContentCard key={label}><div className="flex items-center gap-4 p-4">
        <span className="rounded-control bg-subtle p-3 text-brand-600"><Icon size={24} /></span>
        <div><p className="text-sm text-ink-muted">{label}</p><p className="text-xl font-semibold">{isLoading ? '—' : value}</p></div>
      </div></ContentCard>)}
    </div>
    {isCreating && <FormModal title="Tạo tài khoản" busy={busy} onClose={closeCreate}><form onSubmit={createUser}>
      <div className="p-5">
      {error && <p role="alert" className="mb-4 text-red-700">{error}</p>}
      <fieldset disabled={busy} className="grid gap-4 ">
        <label className="grid gap-1">Tên đăng nhập<input required minLength={3} maxLength={100} className="si-input" value={form.username} onChange={e => setForm({ ...form, username: e.target.value })} /></label>
        <label className="grid gap-1">Mật khẩu<input required type="password" minLength={6} maxLength={100} autoComplete="new-password" className="si-input" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} /></label>
        <label className="grid gap-1">Họ và tên<input required maxLength={150} className="si-input" value={form.fullName} onChange={e => setForm({ ...form, fullName: e.target.value })} /></label>
        <label className="grid gap-1">Email<input required type="email" maxLength={150} className="si-input" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></label>
        <label className="grid gap-1">Vai trò ban đầu<select className="si-select" value={form.role} onChange={e => setForm({ ...form, role: e.target.value })}>
          <option value="" disabled>Chọn vai trò…</option>{roles.filter(isCreatableAccountRole).map(r => <option key={r.id} value={r.code}>{r.name} ({r.code})</option>)}
        </select></label>
      </fieldset>
      <p className="my-3 text-sm text-ink-muted">Chỉ chọn một vai trò: Kế toán hoặc Nhân viên.</p>
      </div>
      <div className="border-hairline flex justify-end gap-2 border-t px-5 py-3.5"><button type="button" disabled={busy} className="si-btn-secondary" onClick={closeCreate}>Hủy</button>
        <button disabled={busy || !roles.some(r => r.code === form.role && isCreatableAccountRole(r))} className="si-btn-primary">{busy ? 'Đang tạo…' : 'Tạo tài khoản'}</button></div>
    </form></FormModal>}
    {editingUser && <FormModal title={`Vai trò của @${editingUser.username}`} busy={busy} onClose={() => { if (!busy) { setEditingUser(null); setError('') } }}><div className="grid gap-4 p-5">
      {error && <p role="alert" className="text-failed">{error}</p>}
      {historical ? <p>USER lịch sử được bảo vệ; không được thay đổi danh sách vai trò.</p> : <fieldset disabled={busy} className="grid gap-3 md:grid-cols-2">
        <legend className="mb-3 text-sm text-ink-muted">Chọn một vai trò: Kế toán hoặc Nhân viên. Khi lưu, vai trò đã chọn sẽ thay thế các vai trò hiện tại.</legend>
        {roles.filter(isAssignableRole).map(role => <label key={role.id} className="flex items-center gap-2">
          <input type="radio" name="account-role" checked={roleDraft[0] === role.id} onChange={() => { setRoleDraft([role.id]); setNotice('') }} />{role.name} ({role.code})
        </label>)}
      </fieldset>}
      {access[editingUser.id]?.assignedRoles.some(r => !r.active) && <p className="text-sm text-ink-muted">Vai trò đã vô hiệu hóa sẽ bị loại khỏi danh sách khi lưu.</p>}
      <div className="flex justify-end gap-2"><button className="si-btn-secondary" disabled={busy} onClick={() => { setEditingUser(null); setError('') }}>Hủy</button>
        <button className="si-btn-primary" disabled={busy || !rolesDirty || roleDraft.length !== 1 || !!historical} onClick={() => void assignRoles()}>{busy ? 'Đang lưu…' : 'Lưu vai trò'}</button></div>
    </div></FormModal>}
    {detailUser && <FormModal title="Chi tiết tài khoản" busy={false} onClose={() => setDetailUser(null)}><div className="grid gap-4 p-5">
      <dl className="grid gap-3 break-words">
        <div><dt className="si-field-label">Họ và tên</dt><dd>{detailUser.fullName || '—'}</dd></div>
        <div><dt className="si-field-label">Tên đăng nhập</dt><dd>@{detailUser.username}</dd></div>
        <div><dt className="si-field-label">Email</dt><dd>{detailUser.email || '—'}</dd></div>
        <div><dt className="si-field-label">Trạng thái</dt><dd>{detailUser.status === 'ACTIVE' ? 'Đang hoạt động' : 'Đã khóa'}</dd></div>
        <div><dt className="si-field-label">Vai trò</dt><dd>{access[detailUser.id]?.assignedRoles.map(role => `${role.name} (${role.code})${role.active ? '' : ' · Tạm dừng'}`).join(', ') || 'Chưa có vai trò'}</dd></div>
      </dl>
      <div><h3 className="si-field-label">Quyền hiệu lực (chỉ đọc)</h3>
        {detailUser.status !== 'ACTIVE' && <p className="text-ink-muted">Tài khoản đã khóa, không được xác thực.</p>}
        {access[detailUser.id]?.adminBypass && <p className="text-ink-muted">Quản trị viên có quyền truy cập theo vai trò ADMIN.</p>}
        <ul className="mt-2 grid gap-1 break-words text-sm">{access[detailUser.id]?.permissions.slice().sort().map(code => <li key={code}>{code}</li>)}</ul>
        {access[detailUser.id]?.permissions.length === 0 && <p>Không có quyền hoạt động.</p>}
      </div>
      <div className="flex justify-end"><button className="si-btn-secondary" onClick={() => setDetailUser(null)}>Đóng</button></div>
    </div></FormModal>}
    <ContentCard><div className="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-[minmax(240px,1fr)_minmax(180px,auto)_minmax(180px,auto)_auto]">
      <input aria-label="Tìm người dùng" className="si-input" placeholder="Tìm tên, username hoặc email…" value={query} onChange={e => setQuery(e.target.value)} />
      <select aria-label="Lọc trạng thái" className="si-select" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}><option value="">Tất cả trạng thái</option><option value="ACTIVE">Đang hoạt động</option><option value="INACTIVE">Đã khóa</option></select>
      <select aria-label="Lọc vai trò" className="si-select" value={roleFilter} onChange={e => setRoleFilter(e.target.value)}><option value="">Tất cả vai trò</option>{roles.map(role => <option key={role.id} value={role.code}>{role.name}</option>)}</select>
      <div className="flex gap-2"><button className="si-btn-secondary" onClick={() => { setQuery(''); setStatusFilter(''); setRoleFilter('') }}>Xóa lọc</button><button aria-label="Tải lại dữ liệu" title="Tải lại dữ liệu" className="si-btn-secondary" disabled={busy || isCreating} onClick={() => void reload()}><RefreshCw size={18} /></button></div>
    </div>
      {isLoading ? <LoadingState label="Đang tải tài khoản…" /> : visible.length === 0 ? <EmptyState title="Không tìm thấy tài khoản" description="Thử thay đổi từ khóa hoặc tạo tài khoản mới." /> :
        <div className="overflow-x-auto"><table className="si-table"><thead><tr><th>Người dùng</th><th>Email</th><th>Vai trò & quyền</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
          <tbody>{pageUsers.map(user => <tr key={user.id}>
            <td><span className="block font-medium">{user.fullName}</span><span className="text-sm text-ink-muted">@{user.username}</span></td><td>{user.email}</td>
            <td><div className="flex flex-wrap gap-1">{access[user.id]?.assignedRoles.map(role => <span key={role.id} className={`si-chip border-hairline ${role.code === 'ADMIN' ? 'bg-brand-50 text-brand-700' : 'bg-subtle text-ink-muted'}`}>{role.name} ({role.code}){!role.active && ' · Tạm dừng'}</span>)}</div>
              {access[user.id]?.assignedRoles.length === 0 && <span>Chưa có vai trò</span>}
              </td>
            <td><span className="inline-flex items-center gap-2 whitespace-nowrap"><span className={`h-2 w-2 rounded-full ${user.status === 'ACTIVE' ? 'bg-parsed' : 'bg-failed'}`} />{user.status === 'ACTIVE' ? 'Đang hoạt động' : 'Đã khóa'}</span></td>
            <td><div className="flex items-center gap-2">
              <button className="si-btn-secondary" disabled={busy} onClick={() => setDetailUser(user)}>Chi tiết</button>
              <div ref={menuUser === user.id ? menuRef : undefined}>
                <button aria-label={`Thao tác với @${user.username}`} aria-expanded={menuUser === user.id} className="si-btn-secondary" disabled={busy} onClick={event => {
                  const bounds = event.currentTarget.getBoundingClientRect()
                  setMenuPosition({ top: Math.max(8, Math.min(bounds.bottom + 4, window.innerHeight - 220)), left: Math.max(8, bounds.right - 280) })
                  setMenuUser(menuUser === user.id ? null : user.id)
                }}><MoreVertical size={20} /></button>
                {menuUser === user.id && <div style={menuPosition} className="fixed z-20 grid w-[280px] max-w-[calc(100vw-1rem)] gap-1 rounded-card border border-hairline bg-surface p-2 shadow-elev1">
                  <button className="flex items-center gap-2 rounded-control p-2 text-left hover:bg-subtle" onClick={() => { setMenuUser(null); setDetailUser(user) }}><Eye size={18} />Xem chi tiết</button>
                  {!access[user.id]?.assignedRoles.some(role => role.code === 'USER') && <button className="flex items-center gap-2 rounded-control p-2 text-left hover:bg-subtle" onClick={() => openAssignment(user)}><Shield size={18} />Gán vai trò</button>}
                  <button className="flex items-center gap-2 rounded-control p-2 text-left hover:bg-subtle" onClick={() => { setMenuUser(null); void updateStatus(user) }}><LockKeyhole size={18} />{user.status === 'ACTIVE' ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}</button>
                </div>}
              </div>
            </div></td>
          </tr>)}</tbody></table></div>}
      <div className="border-hairline flex flex-wrap items-center justify-between gap-3 border-t p-4 text-sm">
        <span>Hiển thị {visible.length ? (currentPage - 1) * pageSize + 1 : 0}–{Math.min(currentPage * pageSize, visible.length)} của {visible.length} tài khoản</span>
        <div className="flex flex-wrap items-center gap-2"><button className="si-btn-secondary" disabled={currentPage === 1} onClick={() => setPage(currentPage - 1)}>Trước</button><span>Trang {currentPage}/{pageCount}</span><button className="si-btn-secondary" disabled={currentPage === pageCount} onClick={() => setPage(currentPage + 1)}>Sau</button>
          <select aria-label="Số tài khoản mỗi trang" className="si-select w-auto" value={pageSize} onChange={e => setPageSize(Number(e.target.value))}>{[10, 20, 50].map(size => <option key={size} value={size}>{size}/trang</option>)}</select>
        </div>
      </div>
    </ContentCard>
  </div>
}

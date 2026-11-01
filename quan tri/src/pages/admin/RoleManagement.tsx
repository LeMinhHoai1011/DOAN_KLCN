import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Copy, Plus, RefreshCw, Save, ShieldCheck } from 'lucide-react'
import api from '../../services/api'
import type { User } from '../../services/authService'
import roleService from '../../services/roleService'
import type { Permission, PermissionGroup, Role, RoleCreateRequest } from '../../services/roleService'
import { getErrorMessage } from '../../services/authService'
import { isSystemRole, sameSet, toggleValues } from '../../utils/rbacDraft'
import PermissionChecklist from '../../components/admin/PermissionChecklist'
import ContentCard from '../../components/ui/ContentCard'
import FormModal from '../../components/ui/FormModal'
import EmptyState from '../../components/ui/EmptyState'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'

const emptyForm = { code: '', name: '', description: '', active: true }
const metadata = (role: Role): RoleCreateRequest => ({ code: role.code, name: role.name, description: role.description ?? '', active: role.active })

export default function RoleManagement() {
  const [roles, setRoles] = useState<Role[]>([])
  const [permissions, setPermissions] = useState<Permission[]>([])
  const [groups, setGroups] = useState<PermissionGroup[]>([])
  const [selected, setSelected] = useState<Role | null>(null)
  const [draft, setDraft] = useState<number[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const pending = useRef(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [query, setQuery] = useState('')
  const [tab, setTab] = useState<'permissions' | 'users'>('permissions')
  const [permissionQuery, setPermissionQuery] = useState('')
  const [roleUsers, setRoleUsers] = useState<{ user: User; roleIds: number[] }[]>([])
  const [usersError, setUsersError] = useState('')
  const [usersLoading, setUsersLoading] = useState(true)
  const [editor, setEditor] = useState<'create' | 'edit' | 'copy' | null>(null)
  const [form, setForm] = useState(emptyForm)
  const [formBaseline, setFormBaseline] = useState(emptyForm)
  const idsFor = (role: Role, all = permissions) => all.filter(p => role.permissions.includes(p.code)).map(p => p.id)
  const permissionDirty = !!selected && !sameSet(draft, idsFor(selected))
  const formDirty = editor !== null && JSON.stringify(form) !== JSON.stringify(formBaseline)
  const dirty = permissionDirty || formDirty
  const assignedUsers = selected ? roleUsers.filter(entry => entry.roleIds.includes(selected.id)) : []
  const filteredGroups = groups.map(group => ({ ...group, permissions: group.permissions.filter(permission =>
    `${group.name} ${permission.name} ${permission.code}`.toLocaleLowerCase().includes(permissionQuery.trim().toLocaleLowerCase())) })).filter(group => group.permissions.length > 0)
  const editableIds = permissions.filter(permission => permission.active).map(permission => permission.id)
  const loadUsers = async () => {
    setUsersLoading(true); setUsersError('')
    try {
      const response = await api.get<User[]>('/api/v1/users')
      const entries = await Promise.all(response.data.map(async user => {
        const result = await api.get<{ assignedRoles: { id: number }[] }>(`/api/v1/users/${user.id}/access`)
        return { user, roleIds: result.data.assignedRoles.map(role => role.id) }
      }))
      setRoleUsers(entries)
    } catch (e) { setUsersError(getErrorMessage(e, 'Không thể tải người dùng đang sử dụng vai trò.')) }
    finally { setUsersLoading(false) }
  }

  useEffect(() => {
    const guard = (event: BeforeUnloadEvent) => { if (dirty || busy) { event.preventDefault(); event.returnValue = '' } }
    window.addEventListener('beforeunload', guard)
    return () => window.removeEventListener('beforeunload', guard)
  }, [dirty, busy])
  const load = async () => {
    if (pending.current) return
    pending.current = true; setBusy(true); setError(''); setNotice('')
    try {
      const [r, g, p] = await Promise.all([roleService.getRoles(), roleService.getPermissionGroups(), roleService.getPermissions()])
      const grouped = new Set(g.data.flatMap(group => group.permissions.map(item => item.id)))
      const extra = p.data.filter(item => !grouped.has(item.id))
      setRoles(r.data); setPermissions(p.data)
      setGroups(extra.length ? [...g.data, { id: -1, code: 'OTHER', name: 'Quyền khác', description: '', active: true, permissions: extra }] : g.data)
      const current = selected ? r.data.find(role => role.id === selected.id) : r.data[0]
      setSelected(current ?? null); setDraft(current ? idsFor(current, p.data) : []); setEditor(null)
    } catch (e) { setError(getErrorMessage(e, 'Không thể tải vai trò và quyền.')) }
    finally { pending.current = false; setBusy(false); setIsLoading(false) }
  }
  useEffect(() => { void load(); void loadUsers() }, [])
  const confirmDiscard = () => !dirty || window.confirm('Bạn có thay đổi chưa lưu. Bỏ các thay đổi này?')
  const choose = (role: Role) => {
    if (pending.current || !confirmDiscard()) return
    setSelected(role); setDraft(idsFor(role)); setEditor(null); setError(''); setNotice(''); setPermissionQuery('')
  }
  const openEditor = (mode: 'create' | 'edit' | 'copy') => {
    if (pending.current || (formDirty && !window.confirm('Bỏ thông tin vai trò chưa lưu?'))) return
    const value = mode === 'edit' && selected ? metadata(selected) : mode === 'copy' && selected
      ? { ...metadata(selected), code: '', name: `${selected.name} (bản sao)`.slice(0, 100) } : { ...emptyForm }
    setForm(value); setFormBaseline(value); setEditor(mode); setNotice(''); setError('')
  }
  const closeEditor = () => {
    if (pending.current) return
    setEditor(null); setError('')
  }
  const saveMetadata = async (event: FormEvent) => {
    event.preventDefault()
    if (pending.current || !editor) return
    if (editor === 'edit' && selected?.code === 'ADMIN' && !window.confirm('Xác nhận cập nhật thông tin vai trò quản trị ADMIN?')) return
    pending.current = true; setBusy(true); setError(''); setNotice('')
    try {
      const payload = { ...form, code: form.code.trim().toUpperCase(), name: form.name.trim() }
      const response = editor === 'edit' && selected
        ? await roleService.updateRole(selected.id, { ...payload, expected: { code: selected.code, name: selected.name, description: selected.description, active: selected.active } })
        : editor === 'copy' && selected ? await roleService.copyRole(selected.id, payload) : await roleService.createRole(payload)
      const saved = response.data
      setRoles(current => editor === 'edit' ? current.map(r => r.id === saved.id ? saved : r) : [...current, saved])
      // Metadata saves must not advance the baseline of an unsaved permission draft.
      if (editor === 'edit' && selected) setSelected({ ...saved, permissions: selected.permissions })
      else if (!permissionDirty) { setSelected(saved); setDraft(idsFor(saved)) }
      setEditor(null); setNotice('Đã lưu thông tin vai trò.')
    } catch (e) { setError(getErrorMessage(e, 'Không thể lưu vai trò.')) }
    finally { pending.current = false; setBusy(false) }
  }
  const savePermissions = async () => {
    if (!selected || !permissionDirty || pending.current || editor) return
    if (selected.code === 'ADMIN' && !window.confirm('Xác nhận thay đổi permission của ADMIN? Một số API vẫn cấp quyền theo vai trò ADMIN.')) return
    pending.current = true; setBusy(true); setError(''); setNotice('')
    try {
      const response = await roleService.updateRolePermissions(selected.id, draft, idsFor(selected))
      setRoles(current => current.map(r => r.id === response.data.id ? response.data : r))
      setSelected(response.data); setDraft(idsFor(response.data)); setNotice('Đã lưu thay đổi quyền.')
    } catch (e) { setError(getErrorMessage(e, 'Không thể lưu quyền. Các thay đổi chưa lưu được giữ lại.')) }
    finally { pending.current = false; setBusy(false) }
  }
  return <div className="flex flex-col gap-5" aria-busy={busy}>
    <PageHeader eyebrow="Quản trị / Phân quyền" title="Quản lý vai trò & quyền"
      description="Cấp quyền trực tiếp theo vai trò. Chỉ lưu khi bạn chọn Lưu thay đổi."
      actions={<button className="si-btn-primary" disabled={busy || !!editor || isLoading} onClick={() => openEditor('create')}><Plus size={20} />Thêm vai trò</button>} />
    {error && !editor && <ErrorState message={error} onRetry={() => { if (confirmDiscard()) void load() }} />}
    {notice && <p role="status" className="rounded-control bg-parsed-surface p-3 text-parsed">{notice}</p>}
    <div className="flex flex-wrap justify-end gap-2">
      <button className="si-btn-secondary" disabled={busy || !!editor || !selected || selected.code === 'USER'} onClick={() => openEditor('copy')}><Copy size={18} />Sao chép vai trò</button>
      <button type="button" className="si-btn-secondary" disabled={busy || !!editor} onClick={() => { if (confirmDiscard()) { void load(); void loadUsers() } }}><RefreshCw size={18} />Làm mới</button>
      <button className="si-btn-primary" disabled={busy || !permissionDirty || !!editor || selected?.code === 'USER'} onClick={() => void savePermissions()}><Save size={18} />{busy ? 'Đang xử lý…' : 'Lưu thay đổi'}</button>
    </div>
    {editor && <FormModal title={editor === 'create' ? 'Tạo vai trò' : editor === 'copy' ? 'Sao chép vai trò' : 'Chỉnh sửa thông tin vai trò'} busy={busy} onClose={closeEditor}>
      <form onSubmit={saveMetadata}>
      <div className="grid gap-4 p-5">
      {error && <p role="alert" className="text-red-700">{error}</p>}
      {editor === 'copy' && <p className="text-sm text-ink-muted">Sao chép các permission đang hoạt động đã lưu trên máy chủ. Thay đổi quyền chưa lưu không được sao chép; tài khoản đang gán vai trò nguồn không được sao chép.</p>}
      <label className="grid gap-1">Mã vai trò<input required maxLength={50} value={form.code} disabled={busy || (editor === 'edit' && !!selected && isSystemRole(selected.code))}
        onChange={e => setForm({ ...form, code: e.target.value })} className="si-input" /></label>
      <label className="grid gap-1">Tên vai trò<input required maxLength={100} value={form.name} disabled={busy}
        onChange={e => setForm({ ...form, name: e.target.value })} className="si-input" /></label>
      <label className="grid gap-1">Mô tả<textarea maxLength={500} value={form.description} disabled={busy}
        onChange={e => setForm({ ...form, description: e.target.value })} className="si-input min-h-20" /></label>
      <label className="flex items-center gap-2"><input type="checkbox" checked={form.active}
        disabled={busy || (editor === 'edit' && !!selected && isSystemRole(selected.code))}
        onChange={e => setForm({ ...form, active: e.target.checked })} />Hoạt động</label>
      {editor === 'edit' && selected && isSystemRole(selected.code) && <p className="text-sm text-ink-muted">Vai trò hệ thống: không được đổi mã hoặc vô hiệu hóa.</p>}
      </div>
      <div className="border-hairline flex justify-end gap-2 border-t px-5 py-3.5">
        <button type="button" className="si-btn-secondary" disabled={busy} onClick={closeEditor}>Hủy</button>
        <button className="si-btn-primary" disabled={busy || (editor === 'edit' && !formDirty)}>{busy ? 'Đang lưu…' : 'Lưu thông tin'}</button>
      </div>
    </form></FormModal>}
    <div className="grid gap-5 lg:grid-cols-3">
      <ContentCard><div className="p-4"><h2 className="si-section-title">Vai trò ({roles.length})</h2>
        <input className="si-input mt-3" placeholder="Tìm tên hoặc mã vai trò…" value={query} onChange={e => setQuery(e.target.value)} /></div>
        {isLoading ? <LoadingState label="Đang tải vai trò…" /> : roles.filter(r => `${r.name} ${r.code}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())).map(role =>
          <button key={role.id} type="button" disabled={busy} aria-current={selected?.id === role.id}
            onClick={() => choose(role)} className={`border-hairline w-full border-t p-4 text-left ${selected?.id === role.id ? 'bg-brand-50' : 'hover:bg-subtle'}`}>
            <span className="flex flex-wrap items-center justify-between gap-2"><span className="font-semibold">{role.name}</span><span className="si-chip border-hairline bg-subtle text-ink-muted">{isSystemRole(role.code) ? 'Hệ thống' : 'Tùy chỉnh'}</span></span>
            <span className="block text-sm text-ink-muted">{role.code} · {role.active ? 'Hoạt động' : 'Tạm dừng'}</span>
            {role.description && <span className="mt-1 block text-sm text-ink-muted">{role.description}</span>}
          </button>)}
        {!isLoading && roles.length === 0 && <EmptyState title="Chưa có vai trò" description="Tạo vai trò để bắt đầu cấu hình quyền." />}
      </ContentCard>
      <div className="grid min-w-0 content-start gap-5 lg:col-span-2">{selected ? <>
      <ContentCard><div className="grid gap-4 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="si-section-title">Thông tin vai trò</h2><button className="si-btn-secondary" disabled={busy || !!editor} onClick={() => openEditor('edit')}>Chỉnh sửa vai trò</button></div>
        {isSystemRole(selected.code) && <p className="flex items-start gap-2 text-sm text-ink-muted"><ShieldCheck size={20} className="mt-1 shrink-0" />Vai trò hệ thống được bảo vệ; không thể đổi mã hoặc vô hiệu hóa.</p>}
        <dl className="grid gap-4 sm:grid-cols-2"><div><dt className="si-field-label">Tên vai trò</dt><dd className="mt-1 rounded-control border border-hairline bg-subtle px-3 py-2">{selected.name}</dd></div><div><dt className="si-field-label">Mã vai trò</dt><dd className="mt-1 rounded-control border border-hairline bg-subtle px-3 py-2">{selected.code}</dd></div><div className="sm:col-span-2"><dt className="si-field-label">Mô tả</dt><dd className="mt-1 whitespace-pre-wrap break-words">{selected.description || 'Chưa có mô tả'}</dd></div></dl>
      </div></ContentCard>
      <ContentCard><div className="border-hairline flex gap-4 border-b px-5" role="tablist" aria-label="Nội dung vai trò" onKeyDown={event => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
        event.preventDefault()
        const next = event.key === 'Home' ? 'permissions' : event.key === 'End' ? 'users' : tab === 'permissions' ? 'users' : 'permissions'
        setTab(next); document.getElementById(`role-${next}-tab`)?.focus()
      }}>
        <button id="role-permissions-tab" role="tab" tabIndex={tab === 'permissions' ? 0 : -1} aria-selected={tab === 'permissions'} aria-controls="role-panel" onClick={() => setTab('permissions')} className={`border-b-2 py-3 font-semibold ${tab === 'permissions' ? 'border-brand-600 text-brand-600' : 'border-transparent text-ink-muted'}`}>Quyền & Phạm vi</button>
        <button id="role-users-tab" role="tab" tabIndex={tab === 'users' ? 0 : -1} aria-selected={tab === 'users'} aria-controls="role-panel" onClick={() => setTab('users')} className={`border-b-2 py-3 font-semibold ${tab === 'users' ? 'border-brand-600 text-brand-600' : 'border-transparent text-ink-muted'}`}>Người dùng{!usersLoading && !usersError ? ` (${assignedUsers.length})` : ''}</button>
      </div><div id="role-panel" role="tabpanel" aria-labelledby={tab === 'permissions' ? 'role-permissions-tab' : 'role-users-tab'} className="grid gap-4 p-5">
      {tab === 'permissions' ? <>
        <p className="text-sm text-ink-muted">{draft.length} quyền được chọn{permissionDirty ? ' · Có thay đổi chưa lưu' : ''}. Quyền còn phụ thuộc trạng thái vai trò, permission và phạm vi dữ liệu.</p>
        {selected.code === 'ADMIN' && <p className="text-sm text-ink-muted">ADMIN vẫn được truy cập trực tiếp tại các API có ngoại lệ quản trị, kể cả khi bỏ tick permission.</p>}
        {selected.code === 'USER' && <p role="note">Quyền USER lịch sử được bảo vệ và chỉ được xem.</p>}
        <div className="flex flex-wrap items-center gap-2"><input aria-label="Tìm quyền" placeholder="Tìm quyền…" className="si-input min-w-0 flex-1" value={permissionQuery} onChange={e => setPermissionQuery(e.target.value)} />
          <button className="si-btn-secondary" disabled={busy || !!editor || selected.code === 'USER' || editableIds.length === 0} onClick={() => { setDraft(current => toggleValues(current, editableIds, true)); setNotice('') }}>Chọn tất cả ({editableIds.length})</button>
          <button className="si-btn-secondary" disabled={busy || !!editor || selected.code === 'USER' || !draft.some(id => editableIds.includes(id))} onClick={() => { setDraft(current => toggleValues(current, editableIds, false)); setNotice('') }}>Bỏ chọn tất cả</button>
        </div>
        {filteredGroups.length === 0 && <EmptyState title="Không tìm thấy quyền" description="Thử thay đổi từ khóa tìm kiếm." />}
        <PermissionChecklist groups={filteredGroups} selected={draft} disabled={busy || !!editor || selected.code === 'USER'}
          onToggle={(ids, checked) => { setDraft(current => toggleValues(current, ids, checked)); setNotice('') }} />
        <div className="border-hairline flex justify-end gap-3 border-t pt-4">
          <button className="si-btn-secondary" disabled={busy || !permissionDirty || !!editor} onClick={() => { setDraft(idsFor(selected)); setError(''); setNotice('') }}>Hủy</button>
        </div>
      </> : usersLoading ? <LoadingState label="Đang tải người dùng…" /> : usersError ? <ErrorState message={usersError} onRetry={() => void loadUsers()} /> : assignedUsers.length === 0 ? <EmptyState title="Chưa có người dùng" description="Chưa có tài khoản nào được gán vai trò này." /> : <div className="overflow-x-auto"><table className="si-table"><thead><tr><th>Người dùng</th><th>Email</th><th>Trạng thái</th></tr></thead><tbody>{assignedUsers.map(({ user }) => <tr key={user.id}><td><span className="block font-medium">{user.fullName}</span><span className="text-sm text-ink-muted">@{user.username}</span></td><td>{user.email}</td><td>{user.status === 'ACTIVE' ? 'Đang hoạt động' : 'Đã khóa'}</td></tr>)}</tbody></table></div>}
      </div></ContentCard>
      </> : <ContentCard><EmptyState title="Chọn một vai trò" description="Chọn vai trò để chỉnh sửa thông tin và cấu hình quyền." /></ContentCard>}</div>
    </div>
  </div>
}

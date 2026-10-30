import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import roleService from '../../services/roleService'
import type { Permission, PermissionGroup, Role, RoleCreateRequest } from '../../services/roleService'
import { getErrorMessage } from '../../services/authService'
import { isSystemRole, sameSet, toggleValues } from '../../utils/rbacDraft'
import PermissionChecklist from '../../components/admin/PermissionChecklist'
import ContentCard from '../../components/ui/ContentCard'
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
  const [editor, setEditor] = useState<'create' | 'edit' | 'copy' | null>(null)
  const [form, setForm] = useState(emptyForm)
  const [formBaseline, setFormBaseline] = useState(emptyForm)
  const idsFor = (role: Role, all = permissions) => all.filter(p => role.permissions.includes(p.code)).map(p => p.id)
  const permissionDirty = !!selected && !sameSet(draft, idsFor(selected))
  const formDirty = editor !== null && JSON.stringify(form) !== JSON.stringify(formBaseline)
  const dirty = permissionDirty || formDirty

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
      setSelected(null); setDraft([]); setEditor(null)
    } catch (e) { setError(getErrorMessage(e, 'Không thể tải vai trò và quyền.')) }
    finally { pending.current = false; setBusy(false); setIsLoading(false) }
  }
  useEffect(() => { void load() }, [])
  const confirmDiscard = () => !dirty || window.confirm('Bạn có thay đổi chưa lưu. Bỏ các thay đổi này?')
  const choose = (role: Role) => {
    if (pending.current || !confirmDiscard()) return
    setSelected(role); setDraft(idsFor(role)); setEditor(null); setError(''); setNotice('')
  }
  const openEditor = (mode: 'create' | 'edit' | 'copy') => {
    if (pending.current || (formDirty && !window.confirm('Bỏ thông tin vai trò chưa lưu?'))) return
    const value = mode === 'edit' && selected ? metadata(selected) : mode === 'copy' && selected
      ? { ...metadata(selected), code: '', name: `${selected.name} (bản sao)`.slice(0, 100) } : { ...emptyForm }
    setForm(value); setFormBaseline(value); setEditor(mode); setNotice(''); setError('')
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
      actions={<button className="si-btn-primary" disabled={busy || !!editor} onClick={() => openEditor('create')}>Thêm vai trò</button>} />
    {error && <ErrorState message={error} onRetry={() => { if (confirmDiscard()) void load() }} />}
    {notice && <p role="status" className="rounded-control bg-parsed-surface p-3 text-parsed">{notice}</p>}
    <button type="button" className="si-btn-secondary self-start" disabled={busy} onClick={() => { if (confirmDiscard()) void load() }}>Tải lại dữ liệu</button>
    {editor && <ContentCard><form onSubmit={saveMetadata} className="grid gap-4 p-5 md:grid-cols-2">
      <h2 className="si-section-title md:col-span-2">{editor === 'create' ? 'Tạo vai trò' : editor === 'copy' ? 'Sao chép vai trò' : 'Chỉnh sửa thông tin vai trò'}</h2>
      {editor === 'copy' && <p className="text-sm text-ink-muted md:col-span-2">Sao chép các permission đang hoạt động đã lưu trên máy chủ. Thay đổi quyền chưa lưu không được sao chép; tài khoản đang gán vai trò nguồn không được sao chép.</p>}
      <label className="grid gap-1">Mã vai trò<input required maxLength={50} value={form.code} disabled={busy || (editor === 'edit' && !!selected && isSystemRole(selected.code))}
        onChange={e => setForm({ ...form, code: e.target.value })} className="si-input" /></label>
      <label className="grid gap-1">Tên vai trò<input required maxLength={100} value={form.name} disabled={busy}
        onChange={e => setForm({ ...form, name: e.target.value })} className="si-input" /></label>
      <label className="grid gap-1 md:col-span-2">Mô tả<textarea maxLength={500} value={form.description} disabled={busy}
        onChange={e => setForm({ ...form, description: e.target.value })} className="si-input" /></label>
      <label className="flex items-center gap-2"><input type="checkbox" checked={form.active}
        disabled={busy || (editor === 'edit' && !!selected && isSystemRole(selected.code))}
        onChange={e => setForm({ ...form, active: e.target.checked })} />Hoạt động</label>
      {editor === 'edit' && selected && isSystemRole(selected.code) && <p className="text-sm text-ink-muted">Vai trò hệ thống: không được đổi mã hoặc vô hiệu hóa.</p>}
      <div className="flex justify-end gap-2 md:col-span-2">
        <button type="button" className="si-btn-secondary" disabled={busy} onClick={() => setEditor(null)}>Hủy</button>
        <button className="si-btn-primary" disabled={busy || (editor === 'edit' && !formDirty)}>{busy ? 'Đang lưu…' : 'Lưu thông tin'}</button>
      </div>
    </form></ContentCard>}
    <div className="grid gap-5 lg:grid-cols-3">
      <ContentCard><div className="p-4"><h2 className="si-section-title">Vai trò ({roles.length})</h2>
        <input className="si-input mt-3" placeholder="Tìm tên hoặc mã vai trò…" value={query} onChange={e => setQuery(e.target.value)} /></div>
        {isLoading ? <LoadingState label="Đang tải vai trò…" /> : roles.filter(r => `${r.name} ${r.code}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())).map(role =>
          <button key={role.id} type="button" disabled={busy} aria-current={selected?.id === role.id}
            onClick={() => choose(role)} className={`border-hairline w-full border-t p-4 text-left ${selected?.id === role.id ? 'bg-brand-50' : 'hover:bg-subtle'}`}>
            <span className="block font-semibold">{role.name}</span><span className="text-sm text-ink-muted">{role.code} · {role.active ? 'Hoạt động' : 'Tạm dừng'}</span>
          </button>)}
        {!isLoading && roles.length === 0 && <EmptyState title="Chưa có vai trò" description="Tạo vai trò để bắt đầu cấu hình quyền." />}
      </ContentCard>
      <ContentCard className="lg:col-span-2">{selected ? <div className="grid gap-4 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="si-section-title">Quyền: {selected.name}</h2>
          <div className="flex flex-wrap gap-2"><button className="si-btn-secondary" disabled={busy || !!editor} onClick={() => openEditor('edit')}>Chỉnh sửa vai trò</button>
            <button className="si-btn-secondary" disabled={busy || !!editor || selected.code === 'USER'} onClick={() => openEditor('copy')}>Sao chép vai trò</button></div></div>
        <p className="text-sm text-ink-muted">{draft.length} quyền được chọn{permissionDirty ? ' · Có thay đổi chưa lưu' : ''}. Quyền còn phụ thuộc trạng thái vai trò, permission và phạm vi dữ liệu.</p>
        {selected.code === 'ADMIN' && <p className="text-sm text-ink-muted">ADMIN vẫn được truy cập trực tiếp tại các API có ngoại lệ quản trị, kể cả khi bỏ tick permission.</p>}
        {selected.code === 'USER' && <p role="note">Quyền USER lịch sử được bảo vệ và chỉ được xem.</p>}
        <PermissionChecklist groups={groups} selected={draft} disabled={busy || !!editor || selected.code === 'USER'}
          onToggle={(ids, checked) => { setDraft(current => toggleValues(current, ids, checked)); setNotice('') }} />
        <div className="border-hairline flex justify-end gap-3 border-t pt-4">
          <button className="si-btn-secondary" disabled={busy || !permissionDirty || !!editor} onClick={() => { setDraft(idsFor(selected)); setError(''); setNotice('') }}>Hủy</button>
          <button className="si-btn-primary" disabled={busy || !permissionDirty || !!editor || selected.code === 'USER'} onClick={() => void savePermissions()}>{busy ? 'Đang xử lý…' : 'Lưu thay đổi'}</button>
        </div>
      </div> : <EmptyState title="Chọn một vai trò" description="Chọn vai trò để chỉnh sửa thông tin và cấu hình quyền." />}</ContentCard>
    </div>
  </div>
}

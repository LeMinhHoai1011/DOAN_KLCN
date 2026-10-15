import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { CheckCircle2, Shield, Plus } from 'lucide-react'
import clsx from 'clsx'
import roleService from '../../services/roleService'
import type { Role, PermissionGroup } from '../../services/roleService'
import ContentCard from '../../components/ui/ContentCard'
import EmptyState from '../../components/ui/EmptyState'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'

const RoleManagement = () => {
  const [roles, setRoles] = useState<Role[]>([])
  const [permissions, setPermissions] = useState<PermissionGroup[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedRole, setSelectedRole] = useState<Role | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [form, setForm] = useState({ code: '', name: '', description: '', active: true })
  const [roleQuery, setRoleQuery] = useState('')

  const loadData = async () => {
    try {
      const [rolesRes, permsRes] = await Promise.all([
        roleService.getRoles(),
        roleService.getPermissionGroups()
      ])
      setRoles(rolesRes.data)
      setPermissions(permsRes.data)
    } catch {
      setError('Không thể tải danh sách vai trò')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadData()
  }, [])

  const createRole = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    try {
      await roleService.createRole(form)
      setForm({ code: '', name: '', description: '', active: true })
      setIsCreating(false)
      await loadData()
    } catch {
      setError('Không thể tạo vai trò. Kiểm tra mã vai trò đã tồn tại.')
    }
  }

  const togglePermission = async (role: Role, permissionCode: string) => {
    try {
      // Find the ID of the permission
      const allPerms = permissions.flatMap(g => g.permissions)
      const p = allPerms.find(p => p.code === permissionCode)
      if (!p) return;

      const currentPerms = role.permissions || [];
      let newCodes = [...currentPerms];

      if (newCodes.includes(permissionCode)) {
        newCodes = newCodes.filter(c => c !== permissionCode)
      } else {
        newCodes.push(permissionCode)
      }

      const newPermIds = newCodes.map(code => allPerms.find(p => p.code === code)?.id).filter(id => id !== undefined) as number[];

      const res = await roleService.updateRolePermissions(role.id, newPermIds);
      setSelectedRole(res.data);
      await loadData();
    } catch {
      setError('Lỗi cập nhật quyền')
    }
  }

  const visibleRoles = roles.filter((role) => {
    const query = roleQuery.trim().toLocaleLowerCase()
    return !query || role.name.toLocaleLowerCase().includes(query) || role.code.toLocaleLowerCase().includes(query)
  })

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        eyebrow="Quản trị / Phân quyền"
        title="Quản lý vai trò & quyền"
        description="Tạo vai trò và phân quyền theo từng nhóm nghiệp vụ (RBAC)."
        actions={
          <button type="button" onClick={() => setIsCreating(!isCreating)} className="si-btn-primary">
            <Plus size={20} />Thêm vai trò
          </button>
        }
      />

      {error && <ErrorState message={error} onRetry={() => void loadData()} />}

      {isCreating && (
        <ContentCard>
          <div className="border-hairline border-b px-5 py-3.5">
            <h2 className="si-section-title">Thêm vai trò</h2>
            <p className="si-section-subtitle mt-0.5">Vai trò mới dùng cùng role/permission API hiện có.</p>
          </div>
          <form onSubmit={createRole} className="grid gap-4 p-5 md:grid-cols-2">
            <label className="grid gap-1">
              <span className="si-field-label">Mã vai trò</span>
              <input required placeholder="VD: MANAGER" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} className="si-input uppercase" />
            </label>
            <label className="grid gap-1">
              <span className="si-field-label">Tên vai trò</span>
              <input required placeholder="VD: Quản lý" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="si-input" />
            </label>
            <label className="grid gap-1 md:col-span-2">
              <span className="si-field-label">Mô tả</span>
              <input placeholder="Phạm vi nghiệp vụ của vai trò" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} className="si-input" />
            </label>
            <div className="border-hairline flex justify-end gap-2 border-t pt-4 md:col-span-2">
              <button type="button" onClick={() => setIsCreating(false)} className="si-btn-secondary">Hủy</button>
              <button type="submit" className="si-btn-primary">Lưu vai trò</button>
            </div>
          </form>
        </ContentCard>
      )}
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-3">
        <ContentCard className="lg:col-span-1">
          <div className="border-hairline flex items-center justify-between gap-3 border-b px-5 py-3.5">
            <h2 className="si-section-title">Danh sách vai trò</h2>
            <span className="si-chip border-hairline bg-subtle text-ink-muted">{roles.length}</span>
          </div>
          <div className="border-hairline border-b p-4">
            <input value={roleQuery} onChange={(event) => setRoleQuery(event.target.value)} placeholder="Tìm theo tên hoặc mã..." className="si-input" />
          </div>
          {isLoading ? (
            <LoadingState label="Đang tải vai trò..." />
          ) : visibleRoles.length === 0 ? (
            <EmptyState title="Không tìm thấy vai trò" description="Thử thay đổi từ khóa tìm kiếm hoặc tạo một vai trò mới." />
          ) : (
            <ul>
              {visibleRoles.map(r => (
                <li key={r.id}>
                  <button
                    type="button"
                    onClick={() => setSelectedRole(r)}
                    aria-current={selectedRole?.id === r.id}
                    className={clsx(
                      'border-hairline w-full cursor-pointer border-b px-5 py-3.5 text-left transition-colors last:border-b-0',
                      selectedRole?.id === r.id ? 'bg-brand-50' : 'hover:bg-subtle',
                    )}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="truncate font-medium text-ink">{r.name}</span>
                      <span className={r.active ? 'si-chip border-parsed/30 bg-parsed-surface text-parsed' : 'si-chip border-hairline bg-subtle text-ink-muted'}>
                        {r.active ? 'Hoạt động' : 'Tạm dừng'}
                      </span>
                    </div>
                    <div className="si-num mt-1 text-2xs tracking-[0.06em] text-ink-muted">{r.code}</div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </ContentCard>

        <ContentCard className="lg:col-span-2">
          <div className="border-hairline flex items-center justify-between gap-3 border-b px-5 py-3.5">
            <h2 className="si-section-title">
              {selectedRole ? `Chi tiết quyền: ${selectedRole.name}` : 'Chọn một vai trò để phân quyền'}
            </h2>
            {selectedRole && <span className="si-num text-[17px] text-ink-muted">{selectedRole.permissions?.length ?? 0} quyền</span>}
          </div>
          {selectedRole ? (
            <div className="grid gap-4 p-5">
              {permissions.map(group => (
                <div key={group.id} className="rounded-card border-hairline border">
                  <div className="border-hairline flex items-center gap-2 border-b px-4 py-2.5">
                    <CheckCircle2 size={20} className="text-brand-600" />
                    <h3 className="text-[18px] font-semibold text-ink">{group.name}</h3>
                  </div>
                  <div className="grid grid-cols-1 gap-1.5 p-3 md:grid-cols-2">
                    {group.permissions.map(p => (
                      <label key={p.id} className="hover:bg-subtle flex cursor-pointer items-start gap-2 rounded-control p-2 transition-colors">
                        <input
                          type="checkbox"
                          checked={selectedRole.permissions?.includes(p.code) || false}
                          onChange={() => togglePermission(selectedRole, p.code)}
                          className="accent-brand-600 mt-0.5"
                        />
                        <span className="min-w-0">
                          <span className="block truncate text-[18px] font-medium text-ink-soft">{p.name}</span>
                          <span className="si-num block text-2xs text-ink-muted">{p.code}</span>
                        </span>
                      </label>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <EmptyState
              title="Chọn một vai trò"
              description="Chọn một vai trò ở danh sách bên trái để xem và cập nhật các quyền theo nhóm."
              action={<Shield size={22} className="text-ink-muted" />}
            />
          )}
        </ContentCard>
      </div>
    </div>
  )
}

export default RoleManagement

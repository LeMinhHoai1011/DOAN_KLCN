import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Shield, Plus } from 'lucide-react'
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
    <div className="space-y-6">
      <PageHeader
        title="Quản lý vai trò & quyền"
        description="Tạo vai trò và phân quyền theo từng nhóm nghiệp vụ (RBAC)."
        actions={<button type="button" onClick={() => setIsCreating(!isCreating)} className="flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-blue-700"><Plus size={18} /> Thêm vai trò</button>}
      />

      {error && <ErrorState message={error} onRetry={() => void loadData()} />}

      {isCreating && (
        <ContentCard className="p-5">
          <form onSubmit={createRole} className="grid gap-3 md:grid-cols-2">
            <div className="md:col-span-2"><h2 className="text-base font-semibold text-slate-800">Tạo vai trò mới</h2><p className="mt-1 text-sm text-slate-500">Vai trò mới sẽ dùng cùng role/permission API hiện có.</p></div>
            <input required placeholder="Mã vai trò (VD: MANAGER)" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} className="rounded-lg border border-slate-200 px-3 py-2 uppercase outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
            <input required placeholder="Tên vai trò" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="rounded-lg border border-slate-200 px-3 py-2 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
            <input placeholder="Mô tả" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} className="rounded-lg border border-slate-200 px-3 py-2 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 md:col-span-2" />
            <div className="flex justify-end gap-2 md:col-span-2"><button type="button" onClick={() => setIsCreating(false)} className="rounded-lg px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100">Hủy</button><button type="submit" className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700">Lưu vai trò</button></div>
          </form>
        </ContentCard>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <ContentCard className="lg:col-span-1">
          <div className="border-b border-slate-200 bg-slate-50 p-4"><div className="flex items-center justify-between gap-3"><span className="font-semibold text-slate-700">Danh sách vai trò</span><span className="rounded-full bg-white px-2 py-0.5 text-xs text-slate-500">{roles.length}</span></div><input value={roleQuery} onChange={(event) => setRoleQuery(event.target.value)} placeholder="Tìm theo tên hoặc mã..." className="mt-3 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" /></div>
          {isLoading ? <LoadingState label="Đang tải vai trò..." /> : visibleRoles.length === 0 ? <EmptyState title="Không tìm thấy vai trò" description="Thử thay đổi từ khóa tìm kiếm hoặc tạo một vai trò mới." /> : (
            <ul className="divide-y divide-slate-100">
              {visibleRoles.map(r => (
                <li key={r.id} onClick={() => setSelectedRole(r)} className={`p-4 cursor-pointer hover:bg-blue-50 transition-colors ${selectedRole?.id === r.id ? 'bg-blue-50 border-l-4 border-blue-600' : 'border-l-4 border-transparent'}`}>
                  <div className="flex items-center justify-between gap-2"><div className="font-medium text-slate-800">{r.name}</div><span className={r.active ? 'rounded-full bg-emerald-50 px-2 py-0.5 text-xs text-emerald-700' : 'rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500'}>{r.active ? 'Hoạt động' : 'Tạm dừng'}</span></div>
                  <div className="mt-1 text-xs font-medium tracking-wide text-slate-500">{r.code}</div>
                </li>
              ))}
            </ul>
          )}
        </ContentCard>
        
        <ContentCard className="lg:col-span-2">
          <div className="bg-slate-50 p-4 font-semibold text-slate-700 border-b border-slate-200">
            {selectedRole ? `Phân quyền cho: ${selectedRole.name}` : 'Chọn một vai trò để phân quyền'}
          </div>
          {selectedRole ? (
            <div className="p-4 space-y-6">
              {permissions.map(group => (
                <div key={group.id} className="border border-slate-100 rounded-lg p-4">
                  <h3 className="font-semibold text-slate-700 mb-3">{group.name}</h3>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                    {group.permissions.map(p => (
                      <label key={p.id} className="flex items-center gap-2 cursor-pointer p-2 hover:bg-slate-50 rounded">
                        <input 
                          type="checkbox" 
                          checked={selectedRole.permissions?.includes(p.code) || false}
                          onChange={() => togglePermission(selectedRole, p.code)}
                          className="rounded text-blue-600"
                        />
                        <div>
                          <div className="text-sm font-medium text-slate-700">{p.name}</div>
                          <div className="text-xs text-slate-400">{p.code}</div>
                        </div>
                      </label>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          ) : <EmptyState title="Chọn một vai trò" description="Chọn một vai trò ở danh sách bên trái để xem và cập nhật các quyền theo nhóm." action={<Shield size={22} className="text-slate-300" />} />}
        </ContentCard>
      </div>
    </div>
  )
}

export default RoleManagement

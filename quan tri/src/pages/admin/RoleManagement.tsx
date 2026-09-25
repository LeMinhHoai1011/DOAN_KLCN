import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Shield, Plus } from 'lucide-react'
import roleService from '../../services/roleService'
import type { Role, PermissionGroup } from '../../services/roleService'

const RoleManagement = () => {
  const [roles, setRoles] = useState<Role[]>([])
  const [permissions, setPermissions] = useState<PermissionGroup[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedRole, setSelectedRole] = useState<Role | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [form, setForm] = useState({ code: '', name: '', description: '', active: true })

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

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Quản lý Vai trò & Quyền</h1>
          <p className="text-slate-500 mt-1">Tạo vai trò và gán quyền (RBAC)</p>
        </div>
        <button type="button" onClick={() => setIsCreating(!isCreating)} className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
          <Plus size={18} /> Thêm vai trò
        </button>
      </div>

      {error && <div className="rounded-lg bg-red-50 px-4 py-3 text-red-700">{error}</div>}

      {isCreating && (
        <form onSubmit={createRole} className="grid gap-3 rounded-xl border border-slate-200 bg-white p-5 shadow-sm md:grid-cols-2">
          <input required placeholder="Mã vai trò (VD: MANAGER)" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} className="rounded-lg border border-slate-200 px-3 py-2 uppercase" />
          <input required placeholder="Tên vai trò" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="rounded-lg border border-slate-200 px-3 py-2" />
          <input placeholder="Mô tả" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} className="rounded-lg border border-slate-200 px-3 py-2 md:col-span-2" />
          <button type="submit" className="rounded-lg bg-emerald-600 px-4 py-2 text-white hover:bg-emerald-700">Lưu vai trò</button>
        </form>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-1 border border-slate-200 rounded-xl bg-white shadow-sm overflow-hidden">
          <div className="bg-slate-50 p-4 font-semibold text-slate-700 border-b border-slate-200">Danh sách Vai trò</div>
          {isLoading ? <div className="p-4 text-slate-500">Đang tải...</div> : (
            <ul className="divide-y divide-slate-100">
              {roles.map(r => (
                <li key={r.id} onClick={() => setSelectedRole(r)} className={`p-4 cursor-pointer hover:bg-blue-50 transition-colors ${selectedRole?.id === r.id ? 'bg-blue-50 border-l-4 border-blue-600' : 'border-l-4 border-transparent'}`}>
                  <div className="font-medium text-slate-800">{r.name}</div>
                  <div className="text-xs text-slate-500">{r.code}</div>
                </li>
              ))}
            </ul>
          )}
        </div>
        
        <div className="lg:col-span-2 border border-slate-200 rounded-xl bg-white shadow-sm overflow-hidden">
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
          ) : (
            <div className="p-10 text-center text-slate-500 flex flex-col items-center">
              <Shield size={48} className="text-slate-300 mb-4" />
              <p>Chọn vai trò ở cột bên trái để xem và chỉnh sửa quyền</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default RoleManagement

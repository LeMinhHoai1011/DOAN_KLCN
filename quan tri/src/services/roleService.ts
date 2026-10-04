import api from './api';

export interface Permission {
  id: number;
  code: string;
  name: string;
  description: string;
  active: boolean;
  groupCode?: string;
}

export interface PermissionGroup {
  id: number;
  code: string;
  name: string;
  description: string;
  active: boolean;
  permissions: Permission[];
}

export interface Role {
  id: number;
  code: string;
  name: string;
  description: string;
  active: boolean;
  permissions: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface RoleCreateRequest {
  code: string;
  name: string;
  description: string;
  active: boolean;
}

const roleService = {
  getRoles: () => api.get<Role[]>('/api/v1/roles'),
  getRole: (id: number) => api.get<Role>(`/api/v1/roles/${id}`),
  createRole: (data: RoleCreateRequest) => api.post<Role>('/api/v1/roles', data),
  updateRole: (id: number, data: RoleCreateRequest) => api.put<Role>(`/api/v1/roles/${id}`, data),
  deleteRole: (id: number) => api.delete(`/api/v1/roles/${id}`),
  
  getRolePermissions: (roleId: number) => api.get<Permission[]>(`/api/v1/roles/${roleId}/permissions`),
  updateRolePermissions: (roleId: number, permissionIds: number[]) => 
    api.put<Role>(`/api/v1/roles/${roleId}/permissions`, { permissionIds }),
    
  getPermissions: () => api.get<Permission[]>('/api/v1/permissions'),
  getPermissionGroups: () => api.get<PermissionGroup[]>('/api/v1/permissions/groups'),
};

export default roleService;

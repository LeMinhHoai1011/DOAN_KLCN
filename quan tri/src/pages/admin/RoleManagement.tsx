import { useMemo, useState } from 'react';
import { Check, ShieldCheck, Users, Wand2 } from 'lucide-react';

type Permission = {
  id: string;
  label: string;
  category: string;
};

type RoleItem = {
  code: 'ADMIN' | 'ACCOUNTANT' | 'EMPLOYEE';
  name: string;
  description: string;
  users: number;
  permissions: string[];
};

const permissionCatalog: Permission[] = [
  { id: 'manage_users', label: 'Quản lý người dùng', category: 'Người dùng' },
  { id: 'manage_roles', label: 'Cấu hình vai trò', category: 'Người dùng' },
  { id: 'view_dashboard', label: 'Xem dashboard', category: 'Tổng quan' },
  { id: 'view_reports', label: 'Xem báo cáo', category: 'Báo cáo' },
  { id: 'approve_documents', label: 'Duyệt chứng từ', category: 'Chứng từ' },
  { id: 'upload_documents', label: 'Tải lên chứng từ', category: 'Chứng từ' },
  { id: 'classify_ai', label: 'Sử dụng AI phân loại', category: 'AI' },
  { id: 'settings_config', label: 'Cấu hình hệ thống', category: 'Hệ thống' },
  { id: 'data_export', label: 'Xuất dữ liệu', category: 'Hệ thống' },
  { id: 'audit_logs', label: 'Xem nhật ký hoạt động', category: 'Bảo mật' },
];

const roleData: RoleItem[] = [
  {
    code: 'ADMIN',
    name: 'Quản trị viên',
    description: 'Quản lý toàn bộ hệ thống và quyền phân quyền.',
    users: 2,
    permissions: ['manage_users', 'manage_roles', 'view_dashboard', 'view_reports', 'approve_documents', 'settings_config', 'audit_logs', 'data_export'],
  },
  {
    code: 'ACCOUNTANT',
    name: 'Kế toán',
    description: 'Theo dõi, duyệt và kiểm tra chứng từ tài chính.',
    users: 5,
    permissions: ['view_dashboard', 'view_reports', 'approve_documents', 'upload_documents', 'classify_ai', 'data_export'],
  },
  {
    code: 'EMPLOYEE',
    name: 'Nhân viên',
    description: 'Tải lên, xử lý và theo dõi hồ sơ công việc.',
    users: 9,
    permissions: ['view_dashboard', 'upload_documents', 'classify_ai'],
  },
];

function RoleManagement() {
    const [selectedRole, setSelectedRole] = useState<RoleItem['code']>('ADMIN');

    const activeRole = useMemo(
        () => roleData.find((role) => role.code === selectedRole) ?? roleData[0],
        [selectedRole]
    );

    const totalPermissions = permissionCatalog.length;
    const totalUsers = roleData.reduce((sum, role) => sum + role.users, 0);

    return (
        <div className="space-y-6">
            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                <div>
                    <h1 className="text-2xl font-bold text-slate-800">Quản lý vai trò</h1>
                    <p className="mt-1 text-sm text-slate-500">Cập nhật quyền truy cập và phân bổ chức năng theo nhóm người dùng</p>
                </div>
                <button
                    type="button"
                    className="inline-flex items-center justify-center rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white shadow-sm transition hover:bg-blue-700"
                >
                    <ShieldCheck className="mr-2 h-4 w-4" />
                    Lưu thay đổi
                </button>
            </div>

            <div className="grid gap-4 md:grid-cols-3">
                <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <div className="flex items-center justify-between">
                        <span className="text-sm text-slate-500">Tổng vai trò</span>
                        <ShieldCheck className="h-5 w-5 text-blue-600" />
                    </div>
                    <div className="mt-3 text-3xl font-bold text-slate-800">{roleData.length}</div>
                    <p className="mt-1 text-xs text-slate-500">Đã cấu hình theo nhóm quyền</p>
                </div>

                <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <div className="flex items-center justify-between">
                        <span className="text-sm text-slate-500">Tổng quyền</span>
                        <Wand2 className="h-5 w-5 text-violet-600" />
                    </div>
                    <div className="mt-3 text-3xl font-bold text-slate-800">{totalPermissions}</div>
                    <p className="mt-1 text-xs text-slate-500">Phân bổ linh hoạt cho từng vai trò</p>
                </div>

                <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <div className="flex items-center justify-between">
                        <span className="text-sm text-slate-500">Người dùng được gán</span>
                        <Users className="h-5 w-5 text-emerald-600" />
                    </div>
                    <div className="mt-3 text-3xl font-bold text-slate-800">{totalUsers}</div>
                    <p className="mt-1 text-xs text-slate-500">Tổng số tài khoản đang hoạt động</p>
                </div>
            </div>

            <div className="grid gap-6 xl:grid-cols-[360px_minmax(0,1fr)]">
                <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                    <h2 className="mb-4 text-lg font-semibold text-slate-800">Danh sách vai trò</h2>
                    <div className="space-y-3">
                        {roleData.map((role) => {
                            const isActive = selectedRole === role.code;

                            return (
                                <button
                                    key={role.code}
                                    type="button"
                                    onClick={() => setSelectedRole(role.code)}
                                    className={`w-full rounded-xl border p-4 text-left transition ${isActive
                                            ? 'border-blue-200 bg-blue-50 shadow-sm'
                                            : 'border-slate-200 bg-slate-50 hover:border-slate-300 hover:bg-white'}`}
                                >
                                    <div className="flex items-center justify-between gap-3">
                                        <div>
                                            <div className="text-base font-semibold text-slate-800">{role.name}</div>
                                            <div className="text-xs text-slate-500">{role.code}</div>
                                        </div>
                                        <span className="rounded-full bg-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700">
                                            {role.users} user
                                        </span>
                                    </div>
                                    <p className="mt-2 text-sm text-slate-600">{role.description}</p>
                                </button>
                            );
                        })}
                    </div>
                </div>

                <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <div className="mb-5 flex items-center justify-between gap-3">
                        <div>
                            <h2 className="text-lg font-semibold text-slate-800">Phân quyền chi tiết</h2>
                            <p className="text-sm text-slate-500">Vai trò: {activeRole.name}</p>
                        </div>
                        <span className="rounded-full bg-emerald-100 px-3 py-1 text-xs font-medium text-emerald-700">
                            {activeRole.permissions.length}/{permissionCatalog.length} quyền
                        </span>
                    </div>

                    <div className="grid gap-4 md:grid-cols-2">
                        {permissionCatalog.map((permission) => {
                            const isEnabled = activeRole.permissions.includes(permission.id);

                            return (
                                <label
                                    key={permission.id}
                                    className="flex cursor-pointer items-start gap-3 rounded-xl border border-slate-200 p-3 transition hover:border-blue-200 hover:bg-blue-50/40"
                                >
                                    <span
                                        className={`mt-0.5 flex h-5 w-5 items-center justify-center rounded border ${isEnabled
                                                ? 'border-blue-600 bg-blue-600 text-white'
                                                : 'border-slate-300 bg-white'}`}
                                    >
                                        {isEnabled && <Check className="h-3.5 w-3.5" />}
                                    </span>

                                    <span className="flex-1">
                                        <span className="block text-sm font-medium text-slate-700">{permission.label}</span>
                                        <span className="mt-0.5 block text-xs text-slate-500">{permission.category}</span>
                                    </span>
                                </label>
                            );
                        })}
                    </div>
                </div>
            </div>
        </div>
    );
}

export default RoleManagement;

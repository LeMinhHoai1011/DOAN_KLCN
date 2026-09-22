import { useEffect, useState } from 'react';
import { Search, UserPlus, Shield, User, Trash2 } from 'lucide-react';

interface UserData {
  id: number;
  fullName: string;
  email: string;
  role: string;
}

const UserManagement = () => {
  const [users, setUsers] = useState<UserData[]>([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('http://localhost:8080/api/users')
      .then((response) => {
        if (!response.ok) {
          throw new Error('Không thể lấy danh sách người dùng');
        }

        return response.json();
      })
      .then((data) => {
        setUsers(data);
        setLoading(false);
      })
      .catch((error) => {
        console.error(error);
        setLoading(false);
      });
  }, []);

  const filteredUsers = users.filter(
    (user) =>
      user.fullName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      user.email.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const getRoleName = (role: string) => {
    if (role === 'ADMIN') return 'Quản trị viên';
    if (role === 'USER') return 'Người dùng';
    return role;
  };

  return (
    <div className="space-y-6">

      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">
            Quản trị người dùng
          </h1>

          <p className="text-slate-500 mt-1">
            Quản lý tài khoản và phân quyền người dùng
          </p>
        </div>

        <button
          className="flex items-center gap-2 px-4 py-2.5
                     bg-blue-600 text-white rounded-lg
                     hover:bg-blue-700 transition-colors"
        >
          <UserPlus size={18} />
          Thêm người dùng
        </button>
      </div>

      {/* Search */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm">
        <div className="p-4 border-b border-slate-200 bg-slate-50/50">

          <div className="relative max-w-md">
            <Search
              size={18}
              className="absolute left-3 top-1/2
                         -translate-y-1/2 text-slate-400"
            />

            <input
              type="text"
              placeholder="Tìm kiếm theo tên hoặc email..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full bg-white border border-slate-200
                         rounded-lg py-2 pl-9 pr-4
                         outline-none text-sm
                         focus:border-blue-500
                         focus:ring-2 focus:ring-blue-500/20"
            />
          </div>

        </div>

        {/* Table */}
        <div className="overflow-x-auto">

          <table className="w-full text-left">

            <thead>
              <tr className="bg-slate-50 text-slate-500
                             text-sm border-b border-slate-200">

                <th className="py-3 px-6 font-medium">
                  ID
                </th>

                <th className="py-3 px-6 font-medium">
                  Người dùng
                </th>

                <th className="py-3 px-6 font-medium">
                  Email
                </th>

                <th className="py-3 px-6 font-medium">
                  Vai trò
                </th>

                <th className="py-3 px-6 font-medium text-center">
                  Thao tác
                </th>

              </tr>
            </thead>

            <tbody className="text-sm divide-y divide-slate-100">

              {loading ? (
                <tr>
                  <td
                    colSpan={5}
                    className="py-10 text-center text-slate-500"
                  >
                    Đang tải danh sách người dùng...
                  </td>
                </tr>
              ) : filteredUsers.length === 0 ? (
                <tr>
                  <td
                    colSpan={5}
                    className="py-10 text-center text-slate-500"
                  >
                    Không tìm thấy người dùng
                  </td>
                </tr>
              ) : (
                filteredUsers.map((user) => (
                  <tr
                    key={user.id}
                    className="hover:bg-slate-50"
                  >

                    <td className="py-4 px-6 font-medium text-blue-600">
                      #{user.id}
                    </td>

                    <td className="py-4 px-6">

                      <div className="flex items-center gap-3">

                        <div className="w-9 h-9 rounded-full
                                        bg-blue-100 text-blue-600
                                        flex items-center justify-center">

                          <User size={18} />

                        </div>

                        <span className="font-medium text-slate-800">
                          {user.fullName}
                        </span>

                      </div>

                    </td>

                    <td className="py-4 px-6 text-slate-600">
                      {user.email}
                    </td>

                    <td className="py-4 px-6">

                      <span
                        className={
                          user.role === 'ADMIN'
                            ? 'inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-medium bg-purple-100 text-purple-700'
                            : 'inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-700'
                        }
                      >

                        {user.role === 'ADMIN' ? (
                          <Shield size={14} />
                        ) : (
                          <User size={14} />
                        )}

                        {getRoleName(user.role)}

                      </span>

                    </td>

                    <td className="py-4 px-6">

                      <div className="flex justify-center">

                        <button
                          className="p-2 text-slate-400
                                     hover:text-red-600
                                     hover:bg-red-50
                                     rounded-lg"
                          title="Xóa người dùng"
                        >
                          <Trash2 size={18} />
                        </button>

                      </div>

                    </td>

                  </tr>
                ))
              )}

            </tbody>

          </table>

        </div>

        {/* Footer */}
        <div className="p-4 border-t border-slate-200
                        bg-slate-50/50 text-sm text-slate-500">

          Tổng số người dùng: {filteredUsers.length}

        </div>

      </div>

    </div>
  );
};

export default UserManagement;
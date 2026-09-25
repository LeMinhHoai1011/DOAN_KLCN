import { ArrowUpRight, BarChart3, Clock3, FileText, ShieldAlert } from 'lucide-react';

const summaryCards = [
  { label: 'Tổng chứng từ', value: '3,486', note: '+12.4% so với tháng trước', color: 'blue', icon: FileText },
  { label: 'Tỷ lệ xử lý xong', value: '84.6%', note: '+6.1% so với tuần trước', color: 'emerald', icon: ArrowUpRight },
  { label: 'Thời gian xử lý TB', value: '1.8 ngày', note: '-0.4 ngày so với tháng trước', color: 'violet', icon: Clock3 },
  { label: 'Cần kiểm tra', value: '126', note: '12% công việc có rủi ro', color: 'amber', icon: ShieldAlert },
];

const monthlyTrend = [48, 64, 58, 70, 88, 76, 94, 102];

const departmentLoad = [
  { name: 'Kế toán', value: 92, amount: '1,280 công việc' },
  { name: 'Nhân sự', value: 74, amount: '980 công việc' },
  { name: 'Kho vận', value: 61, amount: '760 công việc' },
  { name: 'Bán hàng', value: 54, amount: '640 công việc' },
];

const topUsers = [
  { name: 'Nguyễn Thị Hương', tasks: 148, accuracy: '96.2%' },
  { name: 'Trần Văn Minh', tasks: 134, accuracy: '94.8%' },
  { name: 'Lê Hoài Nam', tasks: 121, accuracy: '93.5%' },
  { name: 'Phạm Quỳnh Anh', tasks: 110, accuracy: '92.9%' },
];

const SystemStatistics = () => {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Thống kê hệ thống</h1>
        <p className="mt-1 text-sm text-slate-500">Theo dõi hiệu suất xử lý, tiến độ hoạt động và áp lực công việc của tổ chức</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {summaryCards.map(({ label, value, note, color, icon: Icon }) => (
          <div key={label} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between">
              <span className="text-sm text-slate-500">{label}</span>
              <div
                className={`rounded-lg p-2 ${
                  color === 'blue'
                    ? 'bg-blue-100 text-blue-600'
                    : color === 'emerald'
                      ? 'bg-emerald-100 text-emerald-600'
                      : color === 'violet'
                        ? 'bg-violet-100 text-violet-600'
                        : 'bg-amber-100 text-amber-600'
                }`}
              >
                <Icon className="h-4 w-4" />
              </div>
            </div>
            <div className="mt-4 text-3xl font-bold text-slate-800">{value}</div>
            <div className="mt-2 text-xs text-slate-500">{note}</div>
          </div>
        ))}
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.3fr_0.7fr]">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="mb-5 flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold text-slate-800">Xu hướng hoạt động</h2>
              <p className="text-sm text-slate-500">Số lượng chứng từ xử lý theo 8 tuần gần nhất</p>
            </div>
            <div className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700">+18.4%</div>
          </div>

          <div className="flex h-52 items-end gap-3">
            {monthlyTrend.map((value, index) => (
              <div key={index} className="flex flex-1 flex-col items-center gap-2">
                <div className="flex w-full items-end justify-center rounded-t-xl bg-gradient-to-t from-blue-600 to-blue-400" style={{ height: `${value}%` }} />
                <span className="text-[10px] font-medium text-slate-500">T{index + 1}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="mb-4 flex items-center gap-2">
            <BarChart3 className="h-5 w-5 text-blue-600" />
            <h2 className="text-lg font-semibold text-slate-800">Phân bổ công việc</h2>
          </div>

          <div className="space-y-4">
            {departmentLoad.map((item) => (
              <div key={item.name}>
                <div className="mb-1 flex items-center justify-between text-sm">
                  <span className="font-medium text-slate-700">{item.name}</span>
                  <span className="text-slate-500">{item.amount}</span>
                </div>
                <div className="h-2.5 rounded-full bg-slate-100">
                  <div className="h-2.5 rounded-full bg-gradient-to-r from-blue-500 to-indigo-500" style={{ width: `${item.value}%` }} />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_0.95fr]">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold text-slate-800">Top người dùng hiệu quả</h2>
          <div className="overflow-hidden rounded-xl border border-slate-200">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500">
                <tr>
                  <th className="px-4 py-3 font-medium">Nhân sự</th>
                  <th className="px-4 py-3 font-medium">Công việc</th>
                  <th className="px-4 py-3 font-medium">Độ chính xác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {topUsers.map((user) => (
                  <tr key={user.name} className="bg-white">
                    <td className="px-4 py-3 font-medium text-slate-700">{user.name}</td>
                    <td className="px-4 py-3 text-slate-600">{user.tasks}</td>
                    <td className="px-4 py-3">
                      <span className="rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-medium text-emerald-700">
                        {user.accuracy}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold text-slate-800">Tóm tắt hệ thống</h2>
          <div className="space-y-4">
            <div className="rounded-xl bg-slate-50 p-4">
              <div className="text-sm text-slate-500">Tỷ lệ tự động hóa</div>
              <div className="mt-2 flex items-end justify-between">
                <span className="text-2xl font-bold text-slate-800">76%</span>
                <span className="text-xs text-emerald-600">+8.3%</span>
              </div>
            </div>

            <div className="rounded-xl bg-slate-50 p-4">
              <div className="text-sm text-slate-500">Sai số phân loại</div>
              <div className="mt-2 flex items-end justify-between">
                <span className="text-2xl font-bold text-slate-800">3.1%</span>
                <span className="text-xs text-blue-600">Giảm 1.2%</span>
              </div>
            </div>

            <div className="rounded-xl bg-slate-50 p-4">
              <div className="text-sm text-slate-500">Bảo mật hệ thống</div>
              <div className="mt-2 flex items-end justify-between">
                <span className="text-2xl font-bold text-slate-800">99.2%</span>
                <span className="text-xs text-amber-600">Ổn định</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SystemStatistics;

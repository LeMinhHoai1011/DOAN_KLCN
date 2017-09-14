import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  DoughnutController,
  ArcElement
} from 'chart.js';
import { Doughnut, Line } from 'react-chartjs-2';
import StatCard from '../components/StatCard';
import StatusBadge from '../components/StatusBadge';
import { mockDashboardStats, mockDocuments } from '../services/mockData';
import { FileText, CheckCircle2, Loader2, AlertTriangle, AlertCircle } from 'lucide-react';
import clsx from 'clsx';

ChartJS.register(
  CategoryScale,
  LinearScale,
  BarElement,
  PointElement,
  LineElement,
  DoughnutController,
  ArcElement,
  Title,
  Tooltip,
  Legend
);

const Dashboard = () => {
  const lineChartData = {
    labels: ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'],
    datasets: [
      {
        label: 'Chứng từ đã xử lý',
        data: [120, 190, 150, 220, 280, 140, 110],
        borderColor: '#3b82f6',
        backgroundColor: 'rgba(59, 130, 246, 0.1)',
        fill: true,
        tension: 0.4,
      },
    ],
  };

  const doughnutData = {
    labels: ['Hóa đơn mua hàng', 'Hóa đơn bán hàng', 'Phiếu thu', 'Phiếu chi', 'Khác'],
    datasets: [
      {
        data: [856, 624, 320, 285, 273],
        backgroundColor: [
          '#3b82f6', // blue
          '#10b981', // green
          '#f59e0b', // yellow
          '#ef4444', // red
          '#8b5cf6', // purple
        ],
        borderWidth: 0,
      },
    ],
  };

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'bottom' as const,
        labels: {
          usePointStyle: true,
          padding: 20,
        }
      }
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Tổng quan hệ thống</h1>
        <p className="text-slate-500 mt-1">Theo dõi hoạt động số hóa và xử lý chứng từ</p>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
        <StatCard title="Tổng chứng từ" value={mockDashboardStats.total} icon={FileText} type="primary" />
        <StatCard title="Đã xử lý" value={mockDashboardStats.processed} icon={CheckCircle2} type="success" />
        <StatCard title="Đang xử lý" value={mockDashboardStats.processing} icon={Loader2} type="default" />
        <StatCard title="Cần kiểm tra" value={mockDashboardStats.needsReview} icon={AlertTriangle} type="warning" />
        <StatCard title="Lỗi OCR/AI" value={mockDashboardStats.error} icon={AlertCircle} type="error" />
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm lg:col-span-2">
          <h2 className="text-lg font-semibold text-slate-800 mb-4">Lưu lượng chứng từ (7 ngày qua)</h2>
          <div className="h-[300px]">
            <Line data={lineChartData} options={chartOptions} />
          </div>
        </div>
        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-800 mb-4">Phân loại chứng từ</h2>
          <div className="h-[300px]">
            <Doughnut data={doughnutData} options={chartOptions} />
          </div>
        </div>
      </div>

      {/* Recent Documents Table */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-6 border-b border-slate-200 flex justify-between items-center">
          <h2 className="text-lg font-semibold text-slate-800">Chứng từ mới nhất</h2>
          <button className="text-sm text-blue-600 font-medium hover:text-blue-700">Xem tất cả</button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-slate-50 text-slate-500 text-sm border-b border-slate-200">
                <th className="py-3 px-6 font-medium">Mã</th>
                <th className="py-3 px-6 font-medium">Tên chứng từ</th>
                <th className="py-3 px-6 font-medium">Loại</th>
                <th className="py-3 px-6 font-medium">Ngày</th>
                <th className="py-3 px-6 font-medium">AI Confidence</th>
                <th className="py-3 px-6 font-medium">Trạng thái</th>
              </tr>
            </thead>
            <tbody className="text-sm divide-y divide-slate-100">
              {mockDocuments.slice(0, 5).map((doc) => (
                <tr key={doc.id} className="hover:bg-slate-50 transition-colors">
                  <td className="py-3 px-6 font-medium text-blue-600">{doc.id}</td>
                  <td className="py-3 px-6">{doc.fileName}</td>
                  <td className="py-3 px-6">{doc.type}</td>
                  <td className="py-3 px-6">{doc.date}</td>
                  <td className="py-3 px-6">
                    <span className={clsx(
                      "font-medium",
                      doc.aiConfidence >= 90 ? "text-emerald-600" : doc.aiConfidence >= 80 ? "text-amber-600" : "text-red-600"
                    )}>
                      {doc.aiConfidence}%
                    </span>
                  </td>
                  <td className="py-3 px-6">
                    <StatusBadge status={doc.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;

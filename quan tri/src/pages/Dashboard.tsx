import { useEffect, useState } from 'react';
import StatCard from '../components/StatCard';
import StatusBadge from '../components/StatusBadge';
import dashboardService from '../services/dashboardService';
import type { DashboardStatistics } from '../services/dashboardService';
import documentService from '../services/documentService';
import type { DocumentListItem } from '../services/documentService';
import { AlertTriangle, CheckCircle2, FileText, Receipt } from 'lucide-react';

const Dashboard = () => {
  const [statistics, setStatistics] = useState<DashboardStatistics | null>(null);
  const [recentDocuments, setRecentDocuments] = useState<DocumentListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let isMounted = true;

    const loadDashboard = async () => {
      try {
        const [dashboardStatistics, documents] = await Promise.all([
          dashboardService.getDashboardStatistics(),
          documentService.getDocuments(),
        ]);

        if (isMounted) {
          setStatistics(dashboardStatistics);
          setRecentDocuments(documents.slice(0, 5));
        }
      } catch {
        if (isMounted) {
          setError('Không thể tải dữ liệu dashboard');
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    loadDashboard();

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Tổng quan hệ thống</h1>
        <p className="text-slate-500 mt-1">Theo dõi hoạt động số hóa và xử lý chứng từ</p>
      </div>

      {isLoading && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 text-center text-slate-500">
          Đang tải dữ liệu dashboard...
        </div>
      )}

      {error && (
        <div className="rounded-2xl border border-red-200 bg-red-50 p-6 text-center text-red-600">
          {error}
        </div>
      )}

      {statistics && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard title="Tổng chứng từ" value={statistics.totalDocuments} icon={FileText} type="primary" />
          <StatCard title="Tổng hóa đơn" value={statistics.totalInvoices} icon={Receipt} type="default" />
          <StatCard title="Đã phân loại" value={statistics.totalClassified} icon={CheckCircle2} type="success" />
          <StatCard title="Cần kiểm tra" value={statistics.totalReviewRequired} icon={AlertTriangle} type="warning" />
        </div>
      )}

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
        <h2 className="text-lg font-semibold text-slate-800 mb-2">Biểu đồ dashboard</h2>
        <p className="text-sm text-slate-500">Chưa có API backend cho dữ liệu theo ngày hoặc phân loại để hiển thị biểu đồ.</p>
      </div>

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
              {isLoading && (
                <tr>
                  <td colSpan={6} className="py-10 px-6 text-center text-slate-500">Đang tải chứng từ...</td>
                </tr>
              )}
              {!isLoading && !error && recentDocuments.length === 0 && (
                <tr>
                  <td colSpan={6} className="py-10 px-6 text-center text-slate-500">Chưa có chứng từ nào</td>
                </tr>
              )}
              {!isLoading && !error && recentDocuments.map((doc) => (
                <tr key={doc.id} className="hover:bg-slate-50 transition-colors">
                  <td className="py-3 px-6 font-medium text-blue-600">{doc.id}</td>
                  <td className="py-3 px-6">{doc.fileName}</td>
                  <td className="py-3 px-6">{doc.fileType || '-'}</td>
                  <td className="py-3 px-6">{doc.date}</td>
                  <td className="py-3 px-6">-</td>
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
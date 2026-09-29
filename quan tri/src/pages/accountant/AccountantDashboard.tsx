import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import StatCard from '../../components/StatCard';
import StatusBadge from '../../components/StatusBadge';
import dashboardService from '../../services/dashboardService';
import type { DashboardStatistics } from '../../services/dashboardService';
import type { FinancialDashboard } from '../../services/dashboardService';
import documentService from '../../services/documentService';
import type { DocumentListItem } from '../../services/documentService';
import { AlertTriangle, CheckCircle2, FileText, Receipt } from 'lucide-react';
import { Line } from 'react-chartjs-2';
import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend } from 'chart.js';
import type { FinancialSeriesPoint } from '../../services/dashboardService';
ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend);

const AccountantDashboard = () => {
  const navigate = useNavigate();
  const [statistics, setStatistics] = useState<DashboardStatistics | null>(null);
  const [financial, setFinancial] = useState<FinancialDashboard | null>(null);
  const [recentDocuments, setRecentDocuments] = useState<DocumentListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [series, setSeries] = useState<FinancialSeriesPoint[]>([]);
  const [interval, setInterval] = useState<'DAILY' | 'MONTHLY'>('MONTHLY');
  const [seriesLoading, setSeriesLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;

    const loadDashboard = async () => {
      try {
        const [dashboardStatistics, documents, financialDashboard, timeSeries] = await Promise.all([
          dashboardService.getDashboardStatistics(),
          documentService.getDocuments(),
          dashboardService.getFinancialDashboard(),
          dashboardService.getFinancialTimeSeries({ interval }),
        ]);

        if (isMounted) {
          setStatistics(dashboardStatistics);
          setFinancial(financialDashboard);
          setRecentDocuments(documents.slice(0, 5));
          setSeries(timeSeries);
        }
      } catch {
        if (isMounted) {
          setError('Không thể tải dữ liệu dashboard');
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
          setSeriesLoading(false);
        }
      }
    };

    loadDashboard();

    return () => {
      isMounted = false;
    };
  }, [interval]);

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

      {financial && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <StatCard title="Tổng doanh thu (VND)" value={Number(financial.totalRevenue)} icon={Receipt} type="success" />
          <StatCard title="Tổng chi phí (VND)" value={Number(financial.totalExpense)} icon={AlertTriangle} type="warning" />
          <StatCard title="Dòng tiền (VND)" value={Number(financial.cashFlow)} icon={CheckCircle2} type="primary" />
        </div>
      )}

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
        <div className="mb-4 flex items-center justify-between"><h2 className="text-lg font-semibold text-slate-800">Thu chi theo thoi gian</h2><select value={interval} onChange={(event) => setInterval(event.target.value as 'DAILY' | 'MONTHLY')} className="rounded border border-slate-200 px-3 py-2 text-sm"><option value="MONTHLY">Theo thang</option><option value="DAILY">Theo ngay</option></select></div>
        {seriesLoading ? <p className="text-sm text-slate-500">Dang tai bieu do...</p> : series.length === 0 ? <p className="text-sm text-slate-500">Chua co du lieu trong khoang thoi gian da chon.</p> : <Line data={{labels:series.map(x=>x.period),datasets:[{label:'Thu',data:series.map(x=>Number(x.income)),borderColor:'#059669'},{label:'Chi',data:series.map(x=>Number(x.expense)),borderColor:'#e11d48'},{label:'Dong tien',data:series.map(x=>Number(x.cashFlow)),borderColor:'#2563eb'}]}} options={{responsive:true}} />}
      </div>

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
        <h2 className="text-lg font-semibold text-slate-800 mb-2">Chi phí theo nhóm</h2>
        {financial?.expensesByCategory.length ? <ul className="space-y-2 text-sm text-slate-700">{financial.expensesByCategory.map((item) => <li key={item.category || 'other'} className="flex justify-between border-b border-slate-100 pb-2"><span>{item.category || 'Chưa phân nhóm'}</span><strong>{Number(item.amount).toLocaleString('vi-VN')} đ</strong></li>)}</ul> : <p className="text-sm text-slate-500">Chưa có dữ liệu chi phí trong khoảng thời gian đã chọn.</p>}
      </div>

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-6 border-b border-slate-200 flex justify-between items-center">
          <h2 className="text-lg font-semibold text-slate-800">Chứng từ mới nhất</h2>
          <button
            className="text-sm text-blue-600 font-medium hover:text-blue-700"
            onClick={() => navigate('/accountant/documents')}
          >
            Xem tất cả
          </button>
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
                <tr
                  key={doc.id}
                  className="hover:bg-slate-50 transition-colors cursor-pointer"
                  onClick={() => navigate(`/accountant/documents/${doc.id}`)}
                >
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

export default AccountantDashboard;

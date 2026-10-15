import { useEffect, useState } from 'react';
import StatCard from '../../components/StatCard';
import StatusBadge from '../../components/StatusBadge';
import dashboardService from '../../services/dashboardService';
import type { DashboardStatistics } from '../../services/dashboardService';
import documentService from '../../services/documentService';
import type { DocumentListItem } from '../../services/documentService';
import { AlertTriangle, ArrowRight, CheckCircle2, FileText, Receipt, Upload } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import PageHeader from '../../components/ui/PageHeader';
import ContentCard from '../../components/ui/ContentCard';
import LoadingState from '../../components/ui/LoadingState';
import ErrorState from '../../components/ui/ErrorState';

const EmployeeDashboard = () => {
  const navigate = useNavigate();
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
      <PageHeader eyebrow="Nhân viên" title="Tổng quan của tôi" description="Theo dõi các chứng từ thuộc tài khoản của bạn." actions={<button type="button" onClick={() => navigate('/employee/upload')} className="si-btn-primary"><Upload size={20} />Tải lên chứng từ</button>} />

      {isLoading && (
        <LoadingState label="Đang tải dữ liệu của bạn..." />
      )}

      {error && (
        <ErrorState message={error} />
      )}

      {statistics && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard title="Tổng chứng từ" value={statistics.totalDocuments} icon={FileText} type="primary" unit="chứng từ" />
          <StatCard title="Tổng hóa đơn" value={statistics.totalInvoices} icon={Receipt} type="default" unit="hóa đơn" />
          <StatCard title="Đã phân loại" value={statistics.totalClassified} icon={CheckCircle2} type="success" unit="chứng từ" />
          <StatCard title="Cần kiểm tra" value={statistics.totalReviewRequired} icon={AlertTriangle} type="warning" unit="chứng từ" />
        </div>
      )}

      <ContentCard className="p-5">
        <h2 className="si-section-title">Biểu đồ dashboard</h2>
        <p className="si-section-subtitle mt-1">Chưa có API backend cho dữ liệu theo ngày hoặc phân loại để hiển thị biểu đồ.</p>
      </ContentCard>

      <ContentCard>
        <div className="flex items-center justify-between gap-3 border-b border-hairline px-5 py-3.5">
          <h2 className="si-section-title">Chứng từ mới nhất</h2>
          <button type="button" onClick={() => navigate('/employee/documents')} className="si-link">Xem tất cả<ArrowRight size={20} /></button>
        </div>
        <div className="overflow-x-auto">
          <table className="si-table">
            <thead>
              <tr>
                <th>Mã &amp; tệp</th>
                <th>Loại</th>
                <th>Ngày</th>
                <th>Khớp AI</th>
                <th>Trạng thái</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr>
                  <td colSpan={5} className="py-10 text-center text-ink-muted">Đang tải chứng từ...</td>
                </tr>
              )}
              {!isLoading && !error && recentDocuments.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-10 text-center text-ink-muted">Chưa có chứng từ nào</td>
                </tr>
              )}
              {!isLoading && !error && recentDocuments.map((doc) => (
                <tr key={doc.id} onClick={() => navigate(`/employee/documents/${doc.id}`)} className="cursor-pointer">
                  <td>
                    <div className="flex flex-col">
                      <span className="si-num text-[17px] font-semibold text-brand-700">#{doc.id}</span>
                      <span className="max-w-56 truncate text-2xs text-ink-muted" title={doc.fileName}>{doc.fileName}</span>
                    </div>
                  </td>
                  <td className="text-ink-soft">{doc.documentTypeName}</td>
                  <td className="si-num text-[17px] text-ink-soft">{doc.date}</td>
                  <td className="si-num text-[17px] text-ink-soft">{doc.aiConfidence == null ? '—' : `${doc.aiConfidence}%`}</td>
                  <td>
                    <StatusBadge status={doc.status} reviewStatus={doc.reviewStatus} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ContentCard>
    </div>
  );
};

export default EmployeeDashboard;

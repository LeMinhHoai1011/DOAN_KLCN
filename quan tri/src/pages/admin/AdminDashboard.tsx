import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bar } from 'react-chartjs-2'
import { BarElement, CategoryScale, Chart as ChartJS, Legend, LinearScale, Tooltip } from 'chart.js'
import { AlertTriangle, ArrowRight, BarChart3, CheckCircle2, FileText, UserPlus } from 'lucide-react'
import StatCard from '../../components/StatCard'
import StatusBadge, { getStatusPresentation } from '../../components/StatusBadge'
import ContentCard from '../../components/ui/ContentCard'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import api from '../../services/api'
import dashboardService, { type DashboardStatistics } from '../../services/dashboardService'
import documentService, { type DocumentListItem } from '../../services/documentService'

ChartJS.register(CategoryScale, LinearScale, BarElement, Tooltip, Legend)

/** Tên người tải lên cho cột "NGƯỜI TẢI LÊN" của Stitch (tra từ danh sách người dùng). */
interface DirectoryUser { id: number; fullName?: string | null; username?: string | null }

const AdminDashboard = () => {
  const navigate = useNavigate()
  const [statistics, setStatistics] = useState<DashboardStatistics | null>(null)
  const [recentDocuments, setRecentDocuments] = useState<DocumentListItem[]>([])
  const [totalDocuments, setTotalDocuments] = useState(0)
  const [users, setUsers] = useState<DirectoryUser[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    void api.get<DirectoryUser[]>('/api/v1/users').then(({ data }) => { if (active) setUsers(Array.isArray(data) ? data : []) }).catch(() => undefined)
    Promise.all([
      dashboardService.getDashboardStatistics(),
      documentService.getDocumentPage({ page: 1, size: 5, sort: 'createdAt,desc' }),
    ]).then(([summary, page]) => {
      if (!active) return
      setStatistics(summary)
      setRecentDocuments(page.content)
      setTotalDocuments(page.totalElements)
    }).catch(() => active && setError('Không thể tải dữ liệu tổng quan. Vui lòng thử lại.'))
      .finally(() => active && setIsLoading(false))
    return () => { active = false }
  }, [])

  const uploaderName = (uploadedById?: number) => {
    if (!uploadedById) return '—'
    const match = users.find((item) => item.id === uploadedById)
    return match?.fullName || match?.username || `#${uploadedById}`
  }


  const chartData = useMemo(() => statistics && statistics.documentsByStatus.length ? ({
    labels: statistics.documentsByStatus.map(item => getStatusPresentation(item.status).label),
    datasets: [{
      label: 'Số lượng',
      data: statistics.documentsByStatus.map(item => item.count),
      backgroundColor: ['#475569', '#2563eb', '#059669', '#d97706', '#dc2626', '#7c3aed'],
      borderRadius: 6,
      maxBarThickness: 56,
    }],
  }) : null, [statistics])

  const typeBreakdown = useMemo(() => {
    if (!statistics || statistics.documentsByType.length === 0) return []
    const peak = Math.max(...statistics.documentsByType.map(item => item.count), 1)
    return [...statistics.documentsByType]
      .sort((a, b) => b.count - a.count)
      .map(item => ({ ...item, share: Math.round((item.count / peak) * 100) }))
  }, [statistics])

  if (isLoading) return <LoadingState label="Đang tải dữ liệu tổng quan..." />

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        eyebrow="Quản trị"
        title="Tổng quan Quản trị Hệ thống"
        description="Giám sát tình trạng vận hành, tải xử lý bóc tách OCR AI, luồng chứng từ và tài nguyên người dùng nội bộ."
        actions={
          <>
            <button type="button" onClick={() => navigate('/admin/statistics')} className="si-btn-secondary"><BarChart3 size={20} />Thống kê hệ thống</button>
            <button type="button" onClick={() => navigate('/admin/users')} className="si-btn-primary"><UserPlus size={20} />Thêm người dùng mới</button>
          </>
        }
      />

      {error && <ErrorState message={error} />}

      {statistics && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <StatCard title="Tổng chứng từ số hóa" value={statistics.totalDocuments} icon={FileText} type="primary" unit="chứng từ" hint="Toàn hệ thống" />
          <StatCard title="Đã phân loại tự động" value={statistics.totalClassified} icon={CheckCircle2} type="success" unit="chứng từ" hint="Sẵn sàng kết chuyển hạch toán" />
          <StatCard title="Chứng từ chờ duyệt" value={statistics.totalReviewRequired} icon={AlertTriangle} type="warning" unit="chứng từ" hint={`${statistics.totalFailed} lỗi xử lý`} />
        </div>
      )}

      {statistics && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
          <ContentCard className="p-5 lg:col-span-7">
            <div className="mb-4">
              <h2 className="si-section-title">Tình hình xử lý chứng từ theo trạng thái</h2>
              <p className="si-section-subtitle mt-0.5">Phân bổ khối lượng xử lý chứng từ theo từng trạng thái.</p>
            </div>
            {chartData ? (
              <div className="h-64">
                <Bar
                  data={chartData}
                  options={{
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                      legend: { display: false },
                      tooltip: { backgroundColor: '#0f172a', padding: 14, titleFont: { size: 17 }, bodyFont: { size: 17 } },
                    },
                    scales: {
                      y: { beginAtZero: true, border: { display: false }, grid: { color: '#cbd5e1' }, ticks: { color: '#334155', font: { size: 16 }, precision: 0 } },
                      x: { grid: { display: false }, border: { color: '#94a3b8' }, ticks: { color: '#334155', font: { size: 16 } } },
                    },
                  }}
                />
              </div>
            ) : (
              <p className="py-16 text-center text-[18px] text-ink-muted">Chưa có dữ liệu trạng thái.</p>
            )}
          </ContentCard>

          <ContentCard className="flex flex-col gap-4 p-5 lg:col-span-5">
            <div>
              <h2 className="si-section-title">Cơ cấu loại chứng từ</h2>
              <p className="si-section-subtitle mt-0.5">Tỷ trọng tương đối giữa các loại chứng từ đã nhận diện.</p>
            </div>
            {typeBreakdown.length === 0 ? (
              <p className="py-10 text-center text-[18px] text-ink-muted">Chưa có chứng từ được phân loại.</p>
            ) : (
              <ul className="flex flex-col gap-3">
                {typeBreakdown.map(item => (
                  <li key={item.code} className="flex flex-col gap-2 rounded-control bg-canvas p-3">
                    <div className="flex items-baseline justify-between gap-3">
                      <span className="truncate text-[18px] font-medium text-ink">{item.name}</span>
                      <span className="si-num shrink-0 text-[17px] font-semibold text-ink">{item.count.toLocaleString('vi-VN')}</span>
                    </div>
                    <div className="h-2 w-full overflow-hidden rounded-full bg-subtle">
                      <div className="bg-brand-600 h-full rounded-full" style={{ width: `${item.share}%` }} />
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </ContentCard>
        </div>
      )}

      <ContentCard>
        <div className="flex flex-col gap-1 border-b border-hairline px-5 py-3.5">
          <h2 className="si-section-title">Chứng từ mới nhất toàn hệ thống</h2>
          <p className="si-section-subtitle">Nhật ký xử lý tài liệu từ các phân hệ nghiệp vụ.</p>
        </div>
        <div className="overflow-x-auto">
          <table className="si-table">
            <thead>
              <tr>
                <th>Mã chứng từ &amp; File</th>
                <th>Người tải lên</th>
                <th>Loại chứng từ</th>
                <th>Ngày tải</th>
                <th>Độ tin cậy AI</th>
                <th>Trạng thái</th>
                <th className="text-center">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {!error && recentDocuments.length === 0 && (
                <tr><td colSpan={7} className="py-10 text-center text-ink-muted">Chưa có chứng từ nào</td></tr>
              )}
              {recentDocuments.map(doc => (
                <tr key={doc.id} onClick={() => navigate(`/admin/documents/${doc.id}`)} className="cursor-pointer">
                  <td>
                    <div className="flex flex-col">
                      <span className="si-num text-[17px] font-semibold text-brand-700">#{doc.id}</span>
                      <span className="max-w-56 truncate text-2xs text-ink-muted" title={doc.fileName}>{doc.fileName}</span>
                    </div>
                  </td>
                  <td className="text-ink-soft">{uploaderName(doc.uploadedById)}</td>
                  <td className="text-ink-soft">{doc.documentTypeName}</td>
                  <td className="si-num text-[17px] text-ink-soft">{doc.date}</td>
                  <td className="si-num text-[17px] text-ink-soft">{doc.aiConfidence == null ? '—' : `${doc.aiConfidence}%`}</td>
                  <td><StatusBadge status={doc.status} reviewStatus={doc.reviewStatus} /></td>
                  <td className="text-center">
                    <button
                      type="button"
                      onClick={(event) => { event.stopPropagation(); navigate(`/admin/documents/${doc.id}`) }}
                      className="si-btn-secondary !px-2.5 !py-1 text-2xs"
                    >
                      Chi tiết<ArrowRight size={20} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="border-t border-hairline px-5 py-3">
          <span className="si-num text-[17px] text-ink-soft">
            Hiển thị {recentDocuments.length} trên tổng số {totalDocuments.toLocaleString('vi-VN')} chứng từ đã ghi nhận trong kỳ
          </span>
        </div>
      </ContentCard>

    </div>
  )
}
export default AdminDashboard

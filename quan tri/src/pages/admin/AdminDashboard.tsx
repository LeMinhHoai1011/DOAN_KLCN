import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bar } from 'react-chartjs-2'
import { BarElement, CategoryScale, Chart as ChartJS, Legend, LinearScale, Tooltip } from 'chart.js'
import { AlertTriangle, CheckCircle2, FileText, Receipt } from 'lucide-react'
import StatCard from '../../components/StatCard'
import StatusBadge, { getStatusPresentation } from '../../components/StatusBadge'
import ContentCard from '../../components/ui/ContentCard'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import dashboardService, { type DashboardStatistics } from '../../services/dashboardService'
import documentService, { type DocumentListItem } from '../../services/documentService'

ChartJS.register(CategoryScale, LinearScale, BarElement, Tooltip, Legend)

const AdminDashboard = () => {
  const navigate = useNavigate()
  const [statistics, setStatistics] = useState<DashboardStatistics | null>(null)
  const [recentDocuments, setRecentDocuments] = useState<DocumentListItem[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    Promise.all([
      dashboardService.getDashboardStatistics(),
      documentService.getDocumentPage({ page: 1, size: 5, sort: 'createdAt,desc' }),
    ]).then(([summary, page]) => {
      if (!active) return
      setStatistics(summary)
      setRecentDocuments(page.content)
    }).catch(() => active && setError('Không thể tải dữ liệu tổng quan. Vui lòng thử lại.'))
      .finally(() => active && setIsLoading(false))
    return () => { active = false }
  }, [])

  const chartData = useMemo(() => statistics && statistics.documentsByStatus.length ? ({
    labels: statistics.documentsByStatus.map(item => getStatusPresentation(item.status).label),
    datasets: [{ label: 'Số lượng', data: statistics.documentsByStatus.map(item => item.count), backgroundColor: ['#64748b', '#2563eb', '#059669', '#d97706', '#dc2626', '#7c3aed'], borderRadius: 8, maxBarThickness: 64 }],
  }) : null, [statistics])

  if (isLoading) return <LoadingState label="Đang tải dữ liệu tổng quan..." />
  return <div className="space-y-6">
    <PageHeader title="Tổng quan quản trị" description="Theo dõi tình hình số hóa và xử lý chứng từ trên toàn hệ thống." />
    {error && <ErrorState message={error} />}
    {statistics && <><div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
      <StatCard title="Tổng chứng từ" value={statistics.totalDocuments} icon={FileText} type="primary" />
      <StatCard title="Tổng hóa đơn" value={statistics.totalInvoices} icon={Receipt} type="default" />
      <StatCard title="Đã phân loại" value={statistics.totalClassified} icon={CheckCircle2} type="success" />
      <StatCard title="Cần kiểm tra" value={statistics.totalReviewRequired} icon={AlertTriangle} type="warning" />
    </div>{chartData && <ContentCard className="p-6"><h2 className="text-lg font-semibold text-slate-800">Tình hình xử lý chứng từ</h2><p className="mb-5 mt-1 text-sm text-slate-500">Số liệu tổng hợp trực tiếp từ hệ thống.</p><div className="h-72"><Bar data={chartData} options={{ responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 } }, x: { grid: { display: false } } } }} /></div></ContentCard>}</>}
    <ContentCard><div className="flex items-center justify-between border-b border-slate-200 p-6"><h2 className="text-lg font-semibold text-slate-800">Chứng từ mới nhất</h2><button type="button" onClick={() => navigate('/admin/documents')} className="text-sm font-medium text-blue-600 hover:text-blue-700">Xem tất cả</button></div>
      <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b border-slate-200 bg-slate-50 text-slate-500"><tr><th className="px-6 py-3">Mã</th><th className="px-6 py-3">Tên chứng từ</th><th className="px-6 py-3">Loại</th><th className="px-6 py-3">Ngày</th><th className="px-6 py-3">AI Confidence</th><th className="px-6 py-3">Trạng thái</th></tr></thead><tbody className="divide-y divide-slate-100">
        {!error && recentDocuments.length === 0 && <tr><td colSpan={6} className="px-6 py-10 text-center text-slate-500">Chưa có chứng từ nào</td></tr>}
        {recentDocuments.map(doc => <tr key={doc.id} onClick={() => navigate(`/admin/documents/${doc.id}`)} className="cursor-pointer hover:bg-slate-50"><td className="px-6 py-3 font-medium text-blue-600">{doc.id}</td><td className="px-6 py-3">{doc.fileName}</td><td className="px-6 py-3">{doc.documentTypeName}</td><td className="px-6 py-3">{doc.date}</td><td className="px-6 py-3">{doc.aiConfidence == null ? '—' : `${doc.aiConfidence}%`}</td><td className="px-6 py-3"><StatusBadge status={doc.status} reviewStatus={doc.reviewStatus} /></td></tr>)}
      </tbody></table></div></ContentCard>
  </div>
}
export default AdminDashboard

import { useEffect, useMemo, useState } from 'react'
import { Bar, Doughnut } from 'react-chartjs-2'
import { ArcElement, BarElement, CategoryScale, Chart as ChartJS, Legend, LinearScale, Tooltip } from 'chart.js'
import { AlertTriangle, CheckCircle2, FileText, XCircle } from 'lucide-react'
import StatCard from '../../components/StatCard'
import ContentCard from '../../components/ui/ContentCard'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import dashboardService, { type DashboardStatistics } from '../../services/dashboardService'

ChartJS.register(ArcElement, BarElement, CategoryScale, LinearScale, Tooltip, Legend)
const statusLabels: Record<string, string> = { UPLOADED: 'Đã tải lên', PROCESSING: 'Đang xử lý', PROCESSED: 'Đã xử lý', NEED_REVIEW: 'Cần kiểm tra', FAILED: 'Lỗi xử lý', COMPLETED: 'Hoàn thành' }
const colors = ['#2563eb', '#7c3aed', '#059669', '#d97706', '#dc2626', '#0891b2', '#64748b']

const SystemStatistics = () => {
  const [data, setData] = useState<DashboardStatistics | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = () => { setLoading(true); setError(''); dashboardService.getDashboardStatistics().then(setData).catch(() => setError('Không thể tải thống kê hệ thống. Vui lòng thử lại.')).finally(() => setLoading(false)) }
  useEffect(load, [])
  const typeChart = useMemo(() => data && data.documentsByType.length ? ({ labels: data.documentsByType.map(x => x.name), datasets: [{ data: data.documentsByType.map(x => x.count), backgroundColor: data.documentsByType.map((_, i) => colors[i % colors.length]), borderWidth: 0 }] }) : null, [data])
  const statusChart = useMemo(() => data && data.documentsByStatus.length ? ({ labels: data.documentsByStatus.map(x => statusLabels[x.status] || x.status), datasets: [{ label: 'Số chứng từ', data: data.documentsByStatus.map(x => x.count), backgroundColor: data.documentsByStatus.map((_, i) => colors[i % colors.length]), borderRadius: 7 }] }) : null, [data])
  return <div className="space-y-6"><PageHeader title="Thống kê hệ thống" description="Số liệu tổng hợp trực tiếp từ dữ liệu chứng từ hiện tại." />{loading && <LoadingState label="Đang tải thống kê..." />}{error && <ErrorState message={error} onRetry={load} />}{data && <>
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4"><StatCard title="Tổng chứng từ" value={data.totalDocuments} icon={FileText} type="primary" /><StatCard title="Đã phân loại" value={data.totalClassified} icon={CheckCircle2} type="success" /><StatCard title="Cần kiểm tra" value={data.totalReviewRequired} icon={AlertTriangle} type="warning" /><StatCard title="Xử lý thất bại" value={data.totalFailed} icon={XCircle} type="error" /></div>
    {data.totalDocuments === 0 ? <ContentCard className="p-10 text-center text-slate-500">Chưa có chứng từ để thống kê.</ContentCard> : <div className="grid gap-6 xl:grid-cols-2"><ContentCard className="p-6"><h2 className="text-lg font-semibold text-slate-800">Phân bố theo loại chứng từ</h2>{typeChart ? <div className="mt-5 h-80"><Doughnut data={typeChart} options={{ responsive: true, maintainAspectRatio: false, cutout: '55%', plugins: { legend: { position: 'bottom' } } }} /></div> : <p className="py-16 text-center text-sm text-slate-500">Chưa có chứng từ được gán loại.</p>}</ContentCard><ContentCard className="p-6"><h2 className="text-lg font-semibold text-slate-800">Trạng thái xử lý</h2>{statusChart ? <div className="mt-5 h-80"><Bar data={statusChart} options={{ responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 } }, x: { grid: { display: false } } } }} /></div> : <p className="py-16 text-center text-sm text-slate-500">Chưa có dữ liệu trạng thái xử lý.</p>}</ContentCard></div>}
  </>}</div>
}
export default SystemStatistics

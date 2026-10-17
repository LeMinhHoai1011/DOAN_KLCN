import { useEffect, useMemo, useState } from 'react'
import { Bar, Doughnut } from 'react-chartjs-2'
import { ArcElement, BarElement, CategoryScale, Chart as ChartJS, Legend, LinearScale, Tooltip } from 'chart.js'
import { AlertTriangle, CheckCircle2, FileText, XCircle } from 'lucide-react'
import StatCard from '../../components/StatCard'
import OcrAiPerformance from '../../components/OcrAiPerformance'
import ContentCard from '../../components/ui/ContentCard'
import EmptyState from '../../components/ui/EmptyState'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import dashboardService, { type DashboardStatistics } from '../../services/dashboardService'

ChartJS.register(ArcElement, BarElement, CategoryScale, LinearScale, Tooltip, Legend)
const statusLabels: Record<string, string> = { UPLOADED: 'Đã tải lên', PROCESSING: 'Đang xử lý', PROCESSED: 'Đã xử lý', NEED_REVIEW: 'Cần kiểm tra', FAILED: 'Lỗi xử lý', COMPLETED: 'Hoàn thành' }
const colors = ['#2563eb', '#7c3aed', '#059669', '#d97706', '#dc2626', '#0891b2', '#475569']

const SystemStatistics = () => {
  const [data, setData] = useState<DashboardStatistics | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = () => { setLoading(true); setError(''); dashboardService.getDashboardStatistics().then(setData).catch(() => setError('Không thể tải thống kê hệ thống. Vui lòng thử lại.')).finally(() => setLoading(false)) }
  useEffect(load, [])
  const typeChart = useMemo(() => data && data.documentsByType.length ? ({ labels: data.documentsByType.map(x => x.name), datasets: [{ data: data.documentsByType.map(x => x.count), backgroundColor: data.documentsByType.map((_, i) => colors[i % colors.length]), borderColor: '#ffffff', borderWidth: 2 }] }) : null, [data])
  const statusChart = useMemo(() => data && data.documentsByStatus.length ? ({ labels: data.documentsByStatus.map(x => statusLabels[x.status] || x.status), datasets: [{ label: 'Số chứng từ', data: data.documentsByStatus.map(x => x.count), backgroundColor: data.documentsByStatus.map((_, i) => colors[i % colors.length]), borderRadius: 7 }] }) : null, [data])
  return <div className="space-y-6"><PageHeader title="Thống kê Hệ thống & Hiệu suất AI OCR" description="Số liệu tổng hợp trực tiếp từ dữ liệu chứng từ hiện tại." actions={<button type="button" onClick={load} className="si-btn-secondary">Làm mới</button>} />{loading && <LoadingState label="Đang tải thống kê..." />}{error && <ErrorState message={error} onRetry={load} />}{data && <>
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4"><StatCard title="Tổng chứng từ" value={data.totalDocuments} icon={FileText} type="primary" /><StatCard title="Đã phân loại tự động" value={data.totalClassified} icon={CheckCircle2} type="success" /><StatCard title="Cần kiểm tra" value={data.totalReviewRequired} icon={AlertTriangle} type="warning" /><StatCard title="Lỗi / đã từ chối" value={data.totalFailed} icon={XCircle} type="error" /></div>
    {data.totalDocuments === 0 ? <EmptyState title="Chưa có chứng từ để thống kê" /> : <div className="grid gap-5 xl:grid-cols-2"><ContentCard><div className="border-hairline border-b px-5 py-3.5"><h2 className="si-section-title">Cơ cấu loại chứng từ</h2></div><div className="p-5">{typeChart ? <div className="h-96"><Doughnut data={typeChart} options={{ responsive: true, maintainAspectRatio: false, cutout: '55%', plugins: { legend: { position: 'bottom', labels: { boxWidth: 18, boxHeight: 18, usePointStyle: true, pointStyle: 'rectRounded', color: '#1e293b', font: { size: 17 }, padding: 16 } }, tooltip: { backgroundColor: '#0f172a', padding: 14, titleFont: { size: 17 }, bodyFont: { size: 17 } } } }} /></div> : <p className="py-16 text-center text-[18px] text-ink-muted">Chưa có chứng từ được gán loại.</p>}</div></ContentCard><ContentCard><div className="border-hairline border-b px-5 py-3.5"><h2 className="si-section-title">Tình hình xử lý &amp; phân loại chứng từ</h2></div><div className="p-5">{statusChart ? <div className="h-96"><Bar data={statusChart} options={{ responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { backgroundColor: '#0f172a', padding: 14, titleFont: { size: 17 }, bodyFont: { size: 17 } } }, scales: { y: { beginAtZero: true, border: { display: false }, grid: { color: '#cbd5e1' }, ticks: { color: '#334155', font: { size: 16 }, precision: 0 } }, x: { grid: { display: false }, border: { color: '#94a3b8' }, ticks: { color: '#334155', font: { size: 16 } } } } }} /></div> : <p className="py-16 text-center text-[18px] text-ink-muted">Chưa có dữ liệu trạng thái xử lý.</p>}</div></ContentCard></div>}
  </>}<OcrAiPerformance /></div>
}
export default SystemStatistics

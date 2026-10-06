import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Line } from 'react-chartjs-2'
import { CategoryScale, Chart as ChartJS, Legend, LinearScale, LineElement, PointElement, Tooltip } from 'chart.js'
import { AlertTriangle, CheckCircle2, FileText, Receipt, TrendingDown, TrendingUp, WalletCards } from 'lucide-react'
import StatCard from '../../components/StatCard'
import StatusBadge from '../../components/StatusBadge'
import ContentCard from '../../components/ui/ContentCard'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import dashboardService, { type DashboardStatistics, type FinancialDashboard, type FinancialSeriesPoint } from '../../services/dashboardService'
import documentService, { type DocumentListItem } from '../../services/documentService'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend)
const money = (value: number) => `${Number(value || 0).toLocaleString('vi-VN')} ₫`

const AccountantDashboard = () => {
  const navigate = useNavigate()
  const [statistics, setStatistics] = useState<DashboardStatistics | null>(null)
  const [financial, setFinancial] = useState<FinancialDashboard | null>(null)
  const [documents, setDocuments] = useState<DocumentListItem[]>([])
  const [series, setSeries] = useState<FinancialSeriesPoint[]>([])
  const [interval, setInterval] = useState<'DAILY' | 'MONTHLY'>('MONTHLY')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true; setLoading(true); setError('')
    Promise.all([dashboardService.getDashboardStatistics(), dashboardService.getFinancialDashboard(), dashboardService.getFinancialTimeSeries({ interval }), documentService.getDocumentPage({ page: 1, size: 8, sort: 'createdAt,desc' })])
      .then(([stats, finance, points, page]) => { if (!active) return; setStatistics(stats); setFinancial(finance); setSeries(points); setDocuments([...page.content].sort((a, b) => { const priority = { NEED_REVIEW: 0, FAILED: 1, PROCESSING: 2 } as Record<string, number>; return (priority[a.status] ?? 3) - (priority[b.status] ?? 3) }).slice(0, 5)) })
      .catch(() => active && setError('Không thể tải dữ liệu tổng quan. Vui lòng thử lại.'))
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [interval])

  if (loading) return <LoadingState label="Đang tải dữ liệu tổng quan..." />
  return <div className="space-y-6"><PageHeader title="Tổng quan kế toán" description="Theo dõi chứng từ và tài chính trong phạm vi công ty của bạn." />{error && <ErrorState message={error} />}
    {statistics && <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4"><StatCard title="Tổng chứng từ" value={statistics.totalDocuments} icon={FileText} type="primary" /><StatCard title="Tổng hóa đơn" value={statistics.totalInvoices} icon={Receipt} type="default" /><StatCard title="Đã phân loại" value={statistics.totalClassified} icon={CheckCircle2} type="success" /><StatCard title="Cần kiểm tra" value={statistics.totalReviewRequired} icon={AlertTriangle} type="warning" /></div>}
    {financial && <div className="grid gap-4 md:grid-cols-3"><StatCard title="Tổng thu" value={money(financial.totalRevenue)} icon={TrendingUp} type="success" /><StatCard title="Tổng chi" value={money(financial.totalExpense)} icon={TrendingDown} type="warning" /><StatCard title="Dòng tiền" value={money(financial.cashFlow)} icon={WalletCards} type="primary" /></div>}
    <ContentCard className="p-6"><div className="mb-4 flex items-center justify-between"><div><h2 className="text-lg font-semibold text-slate-800">Thu chi theo thời gian</h2><p className="text-sm text-slate-500">Dữ liệu giao dịch tài chính thực tế.</p></div><select value={interval} onChange={e => setInterval(e.target.value as 'DAILY' | 'MONTHLY')} className="rounded-lg border border-slate-200 px-3 py-2 text-sm"><option value="MONTHLY">Theo tháng</option><option value="DAILY">Theo ngày</option></select></div>{series.length === 0 ? <p className="py-12 text-center text-sm text-slate-500">Chưa có giao dịch trong khoảng thời gian này.</p> : <div className="h-72"><Line data={{ labels: series.map(x => x.period), datasets: [{ label: 'Thu', data: series.map(x => Number(x.income)), borderColor: '#059669', backgroundColor: '#059669' }, { label: 'Chi', data: series.map(x => Number(x.expense)), borderColor: '#e11d48', backgroundColor: '#e11d48' }, { label: 'Dòng tiền', data: series.map(x => Number(x.cashFlow)), borderColor: '#2563eb', backgroundColor: '#2563eb' }] }} options={{ responsive: true, maintainAspectRatio: false }} /></div>}</ContentCard>
    <ContentCard><div className="flex items-center justify-between border-b border-slate-200 p-5"><h2 className="text-lg font-semibold text-slate-800">Chứng từ gần đây cần xử lý</h2><button onClick={() => navigate('/accountant/documents')} className="text-sm font-medium text-blue-600">Xem tất cả</button></div><div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="bg-slate-50 text-slate-500"><tr><th className="p-3">Mã</th><th className="p-3">Tên chứng từ</th><th className="p-3">Loại</th><th className="p-3">Ngày</th><th className="p-3">Số tiền</th><th className="p-3">Trạng thái</th></tr></thead><tbody className="divide-y">{documents.length === 0 ? <tr><td colSpan={6} className="p-8 text-center text-slate-500">Chưa có chứng từ nào</td></tr> : documents.map(doc => <tr key={doc.id} onClick={() => navigate(`/accountant/documents/${doc.id}`)} className="cursor-pointer hover:bg-slate-50"><td className="p-3 font-medium text-blue-600">{doc.id}</td><td className="p-3">{doc.fileName}</td><td className="p-3">{doc.fileType || '—'}</td><td className="p-3">{doc.date}</td><td className="p-3">{doc.amount == null ? '—' : money(doc.amount)}</td><td className="p-3"><StatusBadge status={doc.status} reviewStatus={doc.reviewStatus} /></td></tr>)}</tbody></table></div></ContentCard>
  </div>
}
export default AccountantDashboard

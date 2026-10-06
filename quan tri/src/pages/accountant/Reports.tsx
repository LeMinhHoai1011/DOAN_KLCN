import { useEffect, useState } from 'react'
import { AlertTriangle, CheckCircle2, FileText, Receipt, TrendingDown, TrendingUp, WalletCards } from 'lucide-react'
import dashboardService, { type DashboardStatistics, type FinancialDashboard } from '../../services/dashboardService'
import exportService from '../../services/exportService'
import StatCard from '../../components/StatCard'
import ContentCard from '../../components/ui/ContentCard'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import Toast, { type ToastTone } from '../../components/ui/Toast'

const money = (value: number) => `${Number(value || 0).toLocaleString('vi-VN')} ₫`

export default function Reports() {
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [financial, setFinancial] = useState<FinancialDashboard | null>(null)
  const [statistics, setStatistics] = useState<DashboardStatistics | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [exporting, setExporting] = useState(false)
  const [feedback, setFeedback] = useState<{ tone: ToastTone; message: string } | null>(null)

  const load = () => {
    if (dateFrom && dateTo && dateFrom > dateTo) { setError('Ngày bắt đầu không được sau ngày kết thúc.'); return }
    setLoading(true); setError('')
    Promise.all([dashboardService.getFinancialDashboard({ dateFrom: dateFrom || undefined, dateTo: dateTo || undefined }), dashboardService.getDashboardStatistics()])
      .then(([finance, stats]) => { setFinancial(finance); setStatistics(stats) })
      .catch(() => setError('Không thể tải báo cáo. Vui lòng thử lại.'))
      .finally(() => setLoading(false))
  }
  useEffect(load, [])

  const exportReport = async () => {
    setExporting(true); setFeedback(null)
    try { await exportService.exportFinancialReport({ fromDate: dateFrom || undefined, toDate: dateTo || undefined }); setFeedback({ tone: 'success', message: 'Đã xuất báo cáo Excel.' }) }
    catch { setFeedback({ tone: 'error', message: 'Không thể xuất báo cáo. Vui lòng thử lại.' }) }
    finally { setExporting(false) }
  }

  return <div className="space-y-6">
    <PageHeader title="Báo cáo tài chính" description="Tổng hợp dữ liệu trong phạm vi công ty được cấp quyền." actions={<button type="button" onClick={() => void exportReport()} disabled={exporting} className="rounded-lg bg-emerald-600 px-4 py-2 text-white disabled:opacity-60">{exporting ? 'Đang xuất...' : 'Xuất báo cáo Excel'}</button>} />
    {feedback && <Toast tone={feedback.tone}>{feedback.message}</Toast>}
    <ContentCard className="flex flex-wrap items-end gap-3 p-4"><label className="grid gap-1 text-sm text-slate-600">Từ ngày<input type="date" value={dateFrom} onChange={e => setDateFrom(e.target.value)} className="rounded-lg border border-slate-200 p-2" /></label><label className="grid gap-1 text-sm text-slate-600">Đến ngày<input type="date" value={dateTo} onChange={e => setDateTo(e.target.value)} className="rounded-lg border border-slate-200 p-2" /></label><button type="button" onClick={load} className="rounded-lg bg-blue-600 px-4 py-2 text-white">Áp dụng</button></ContentCard>
    {loading ? <LoadingState label="Đang tải báo cáo..." /> : error ? <ErrorState message={error} onRetry={load} /> : financial && statistics && <>
      <div className="grid gap-4 md:grid-cols-3"><StatCard title="Tổng thu" value={money(financial.totalRevenue)} icon={TrendingUp} type="success" /><StatCard title="Tổng chi" value={money(financial.totalExpense)} icon={TrendingDown} type="warning" /><StatCard title="Dòng tiền" value={money(financial.cashFlow)} icon={WalletCards} type="primary" /></div>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4"><StatCard title="Tổng chứng từ" value={statistics.totalDocuments} icon={FileText} type="primary" /><StatCard title="Tổng hóa đơn" value={statistics.totalInvoices} icon={Receipt} type="default" /><StatCard title="Đã phân loại" value={statistics.totalClassified} icon={CheckCircle2} type="success" /><StatCard title="Cần kiểm tra" value={statistics.totalReviewRequired} icon={AlertTriangle} type="warning" /></div>
      <ContentCard><h2 className="border-b border-slate-200 p-4 font-semibold text-slate-800">Chi phí theo nhóm</h2>{financial.expensesByCategory.length === 0 ? <p className="p-6 text-center text-sm text-slate-500">Chưa có dữ liệu chi phí trong khoảng thời gian đã chọn.</p> : <div className="divide-y divide-slate-100">{financial.expensesByCategory.map(item => <div key={item.category || 'none'} className="flex justify-between p-4"><span>{item.category || 'Chưa phân nhóm'}</span><strong>{money(item.amount)}</strong></div>)}</div>}</ContentCard>
    </>}
  </div>
}

import { useEffect, useState } from 'react'
import dashboardService, { type DashboardStatistics, type FinancialDashboard } from '../../services/dashboardService'
import exportService from '../../services/exportService'
import ContentCard from '../../components/ui/ContentCard'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import Toast, { type ToastTone } from '../../components/ui/Toast'

const money = (n: number) => Number(n || 0).toLocaleString('vi-VN')

export default function Reports() {
  const [fromDate, setFrom] = useState('')
  const [toDate, setTo] = useState('')
  const [financial, setFinancial] = useState<FinancialDashboard | null>(null)
  const [statistics, setStatistics] = useState<DashboardStatistics | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [exportingInvoices, setExportingInvoices] = useState(false)
  const [exportingFinancialReport, setExportingFinancialReport] = useState(false)
  const [exportFeedback, setExportFeedback] = useState<{ tone: ToastTone, message: string } | null>(null)

  const filters = () => ({ fromDate: fromDate || undefined, toDate: toDate || undefined })
  const load = () => {
    setLoading(true)
    setError('')
    void Promise.all([
      dashboardService.getFinancialDashboard({ dateFrom: fromDate || undefined, dateTo: toDate || undefined }),
      dashboardService.getDashboardStatistics(),
    ])
      .then(([financialDashboard, dashboardStatistics]) => {
        setFinancial(financialDashboard)
        setStatistics(dashboardStatistics)
      })
      .catch(() => setError('Không thể tải báo cáo.'))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  const exportInvoices = async () => {
    setExportingInvoices(true)
    setExportFeedback(null)
    try {
      await exportService.exportInvoices(filters())
      setExportFeedback({ tone: 'success', message: 'Xuất hóa đơn Excel thành công.' })
    } catch {
      setExportFeedback({ tone: 'error', message: 'Không thể xuất hóa đơn. Vui lòng thử lại.' })
    } finally {
      setExportingInvoices(false)
    }
  }

  const exportFinancialReport = async () => {
    setExportingFinancialReport(true)
    setExportFeedback(null)
    try {
      await exportService.exportFinancialReport(filters())
      setExportFeedback({ tone: 'success', message: 'Xuất báo cáo thành công.' })
    } catch {
      setExportFeedback({ tone: 'error', message: 'Không thể xuất báo cáo. Vui lòng thử lại.' })
    } finally {
      setExportingFinancialReport(false)
    }
  }

  return <div className="space-y-6">
    <PageHeader
      title="Bao cao tai chinh"
      description="Tong hop du lieu tai chinh trong pham vi duoc cap quyen."
      actions={<div className="flex gap-2">
        <button onClick={() => void exportFinancialReport()} disabled={exportingFinancialReport} className="rounded bg-emerald-600 px-3 py-2 text-white disabled:cursor-not-allowed disabled:opacity-60">
          {exportingFinancialReport ? 'Dang xuat bao cao...' : 'Xuat bao cao Excel'}
        </button>
        <button onClick={() => void exportInvoices()} disabled={exportingInvoices} className="rounded border px-3 py-2 disabled:cursor-not-allowed disabled:opacity-60">
          {exportingInvoices ? 'Dang xuat hoa don...' : 'Xuat hoa don'}
        </button>
      </div>}
    />
    {exportFeedback && <Toast tone={exportFeedback.tone}>{exportFeedback.message}</Toast>}
    <ContentCard className="flex flex-wrap gap-3 p-4">
      <input type="date" value={fromDate} onChange={event => setFrom(event.target.value)} className="rounded border p-2" />
      <input type="date" value={toDate} onChange={event => setTo(event.target.value)} className="rounded border p-2" />
      <button onClick={load} className="rounded bg-blue-600 px-4 text-white">Ap dung</button>
    </ContentCard>
    {loading ? <LoadingState /> : error ? <ErrorState message={error} /> : <>
      <div className="grid gap-4 md:grid-cols-3">
        <ContentCard className="p-4">Tong thu <b>{money(financial?.totalRevenue || 0)}</b></ContentCard>
        <ContentCard className="p-4">Tong chi <b>{money(financial?.totalExpense || 0)}</b></ContentCard>
        <ContentCard className="p-4">Dong tien <b>{money(financial?.cashFlow || 0)}</b></ContentCard>
      </div>
      <ContentCard className="p-4">Chung tu: <b>{statistics?.totalDocuments || 0}</b> · Hoa don: <b>{statistics?.totalInvoices || 0}</b> · Da phan loai: <b>{statistics?.totalClassified || 0}</b> · Can review: <b>{statistics?.totalReviewRequired || 0}</b></ContentCard>
      <ContentCard><h2 className="p-4 font-semibold">Chi phi theo nhom</h2><div className="divide-y">{(financial?.expensesByCategory || []).map(item => <div key={item.category || 'none'} className="flex justify-between p-3"><span>{item.category || 'Chua phan nhom'}</span><b>{money(item.amount)}</b></div>)}</div></ContentCard>
    </>}
  </div>
}

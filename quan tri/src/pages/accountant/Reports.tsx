import { useEffect, useState } from 'react'
import { Line } from 'react-chartjs-2'
import { CategoryScale, Chart as ChartJS, Legend, LinearScale, LineElement, PointElement, Tooltip } from 'chart.js'
import { Download, TrendingDown, TrendingUp, WalletCards } from 'lucide-react'
import dashboardService, { type DashboardStatistics, type FinancialDashboard, type FinancialSeriesPoint } from '../../services/dashboardService'
import exportService from '../../services/exportService'
import StatCard from '../../components/StatCard'
import ContentCard from '../../components/ui/ContentCard'
import EmptyState from '../../components/ui/EmptyState'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import Toast, { type ToastTone } from '../../components/ui/Toast'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend)

const money = (value: number) => `${Number(value || 0).toLocaleString('vi-VN')} ₫`

export default function Reports() {
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [financial, setFinancial] = useState<FinancialDashboard | null>(null)
  const [statistics, setStatistics] = useState<DashboardStatistics | null>(null)
  const [series, setSeries] = useState<FinancialSeriesPoint[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [exporting, setExporting] = useState(false)
  const [feedback, setFeedback] = useState<{ tone: ToastTone; message: string } | null>(null)

  const load = () => {
    if (dateFrom && dateTo && dateFrom > dateTo) { setError('Ngày bắt đầu không được sau ngày kết thúc.'); return }
    setLoading(true); setError('')
    Promise.all([
      dashboardService.getFinancialDashboard({ dateFrom: dateFrom || undefined, dateTo: dateTo || undefined }),
      dashboardService.getDashboardStatistics(),
      dashboardService.getFinancialTimeSeries({ dateFrom: dateFrom || undefined, dateTo: dateTo || undefined, interval: 'MONTHLY' }),
    ])
      .then(([finance, stats, points]) => { setFinancial(finance); setStatistics(stats); setSeries(points) })
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

  const categoryTotal = financial?.expensesByCategory.reduce((total, item) => total + Number(item.amount || 0), 0) ?? 0

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        eyebrow="Phân hệ kế toán / Báo cáo"
        title="Báo cáo Tài chính & Tổng hợp Số hóa"
        description="Tổng hợp dữ liệu trong phạm vi công ty được cấp quyền."
        actions={
          <button type="button" onClick={() => void exportReport()} disabled={exporting} className="si-btn-primary">
            <Download size={20} />{exporting ? 'Đang xuất...' : 'Xuất Excel BTC'}
          </button>
        }
      />

      {feedback && <Toast tone={feedback.tone}>{feedback.message}</Toast>}

      <ContentCard>
        <div className="border-hairline flex items-center justify-between gap-3 border-b px-5 py-3.5">
          <h2 className="si-section-title">Kỳ báo cáo</h2>
          <span className="si-label">Tùy chọn</span>
        </div>
        <div className="flex flex-wrap items-end gap-3 p-5">
          <label className="grid gap-1"><span className="si-field-label">Từ ngày</span><input type="date" value={dateFrom} onChange={(event) => setDateFrom(event.target.value)} className="si-input" /></label>
          <label className="grid gap-1"><span className="si-field-label">Đến ngày</span><input type="date" value={dateTo} onChange={(event) => setDateTo(event.target.value)} className="si-input" /></label>
          <button type="button" onClick={load} className="si-btn-primary">Áp dụng</button>
        </div>
      </ContentCard>

      {loading ? (
        <LoadingState label="Đang tải báo cáo..." />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : financial && statistics ? (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <StatCard title="Tổng thu" value={money(financial.totalRevenue)} icon={TrendingUp} type="success" hint="Theo khoảng thời gian đã chọn" />
            <StatCard title="Tổng chi" value={money(financial.totalExpense)} icon={TrendingDown} type="warning" hint="Theo khoảng thời gian đã chọn" />
            <StatCard title="Dòng tiền ròng" value={money(financial.cashFlow)} icon={WalletCards} type="primary" hint="Tổng thu trừ tổng chi" />
          </div>

          <ContentCard className="p-5">
            <div className="mb-4">
              <h2 className="si-section-title">Diễn biến Dòng tiền Thu - Chi theo Tháng</h2>
              <p className="si-section-subtitle mt-0.5">Chuẩn hạch toán theo kỳ kế toán đã chọn.</p>
            </div>
            {series.length === 0 ? (
              <p className="py-12 text-center text-[18px] text-ink-muted">Chưa có dữ liệu dòng tiền trong kỳ đã chọn.</p>
            ) : (
              <div className="h-72">
                <Line
                  data={{
                    labels: series.map((point) => point.period),
                    datasets: [
                      { label: 'Thu thực tế', data: series.map((point) => Number(point.income)), borderColor: '#059669', backgroundColor: '#059669', borderWidth: 3, tension: 0.3, pointRadius: 4.5 },
                      { label: 'Chi thực tế', data: series.map((point) => Number(point.expense)), borderColor: '#e11d48', backgroundColor: '#e11d48', borderWidth: 3, tension: 0.3, pointRadius: 4.5 },
                      { label: 'Dòng tiền ròng', data: series.map((point) => Number(point.cashFlow)), borderColor: '#2563eb', backgroundColor: '#2563eb', borderWidth: 3, tension: 0.3, pointRadius: 4.5 },
                    ],
                  }}
                  options={{
                    responsive: true,
                    maintainAspectRatio: false,
                    interaction: { mode: 'index', intersect: false },
                    plugins: {
                      legend: { position: 'top', align: 'end', labels: { boxWidth: 18, boxHeight: 18, usePointStyle: true, pointStyle: 'rectRounded', color: '#1e293b', font: { size: 17 } } },
                      tooltip: { backgroundColor: '#0f172a', padding: 14, titleFont: { size: 17 }, bodyFont: { size: 17 } },
                    },
                    scales: {
                      y: { beginAtZero: true, border: { display: false }, grid: { color: '#cbd5e1' }, ticks: { color: '#334155', font: { size: 16 }, callback: (value) => Number(value).toLocaleString('vi-VN') } },
                      x: { grid: { display: false }, border: { color: '#94a3b8' }, ticks: { color: '#334155', font: { size: 16 } } },
                    },
                  }}
                />
              </div>
            )}
          </ContentCard>

          <ContentCard>
            <div className="border-hairline flex items-center justify-between gap-3 border-b px-5 py-3.5">
              <h2 className="si-section-title">Tổng hợp Chi phí theo Danh mục Kế toán</h2>
              <span className="si-num text-[17px] text-ink-soft">{money(categoryTotal)}</span>
            </div>
            {financial.expensesByCategory.length === 0 ? (
              <EmptyState title="Chưa có dữ liệu chi phí" description="Chưa có dữ liệu chi phí trong khoảng thời gian đã chọn." />
            ) : (
              <div className="overflow-x-auto">
                <table className="si-table">
                  <thead><tr><th>Tên chi phí / Danh mục kế toán</th><th className="text-right">Tổng thanh toán (VND)</th><th className="text-right">Tỷ trọng</th></tr></thead>
                  <tbody>
                    {financial.expensesByCategory.map((item) => (
                      <tr key={item.category || 'none'}>
                        <td className="text-ink-soft">{item.category || 'Chưa phân nhóm'}</td>
                        <td className="si-num text-right text-[17px] font-semibold text-ink">{money(item.amount)}</td>
                        <td className="si-num text-right text-[17px] text-ink-soft">{categoryTotal > 0 ? `${((Number(item.amount || 0) / categoryTotal) * 100).toFixed(1)}%` : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </ContentCard>
        </>
      ) : null}
    </div>
  )
}

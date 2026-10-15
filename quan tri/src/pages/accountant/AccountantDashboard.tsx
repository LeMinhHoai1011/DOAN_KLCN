import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Line } from 'react-chartjs-2'
import { CategoryScale, Chart as ChartJS, Legend, LinearScale, LineElement, PointElement, Tooltip } from 'chart.js'
import { AlertTriangle, ArrowRight, CalendarDays, CheckCircle2, Download, FileText, WalletCards } from 'lucide-react'
import StatCard from '../../components/StatCard'
import ContentCard from '../../components/ui/ContentCard'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import dashboardService, { type DashboardStatistics, type FinancialDashboard, type FinancialSeriesPoint } from '../../services/dashboardService'
import documentService, { type DocumentListItem } from '../../services/documentService'
import exportService from '../../services/exportService'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend)
const confidenceTone = (value: number | undefined) => (value === undefined ? 'bg-draft' : value >= 90 ? 'bg-parsed' : value >= 75 ? 'bg-review' : 'bg-failed')
/** Nội dung cột "Vấn đề cần xác nhận" của Stitch, suy ra từ trạng thái xử lý và độ tin cậy AI. */
const issueLabel = (doc: DocumentListItem) => {
  if (doc.status === 'FAILED') return 'Lỗi xử lý AI, cần chạy lại'
  if (doc.status === 'PROCESSING' || doc.status === 'UPLOADED') return 'Đang bóc tách OCR'
  if (doc.aiConfidence != null && doc.aiConfidence < 85) return `Độ khớp AI ${doc.aiConfidence}% — cần xác minh`
  if (doc.reviewStatus === 'PENDING') return 'Chờ duyệt hạch toán'
  return 'Cần kế toán xác nhận'
}

const AccountantDashboard = () => {
  const navigate = useNavigate()
  const [statistics, setStatistics] = useState<DashboardStatistics | null>(null)
  const [financial, setFinancial] = useState<FinancialDashboard | null>(null)
  const [documents, setDocuments] = useState<DocumentListItem[]>([])
  const [series, setSeries] = useState<FinancialSeriesPoint[]>([])
  const [pendingTotal, setPendingTotal] = useState(0)
  const [interval, setInterval] = useState<'DAILY' | 'MONTHLY'>('MONTHLY')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [exporting, setExporting] = useState(false)
  const periodLabel = `Tháng ${new Date().getMonth() + 1}/${new Date().getFullYear()}`

  useEffect(() => {
    let active = true; setLoading(true); setError('')
    Promise.all([
      dashboardService.getDashboardStatistics(),
      dashboardService.getFinancialDashboard(),
      dashboardService.getFinancialTimeSeries({ interval }),
      documentService.getDocumentPage({ page: 1, size: 5, processingStatus: 'NEED_REVIEW', sort: 'createdAt,asc' }),
      documentService.getDocumentPage({ page: 1, size: 5, processingStatus: 'FAILED', sort: 'createdAt,asc' }),
      documentService.getDocumentPage({ page: 1, size: 5, processingStatus: 'PROCESSING', sort: 'createdAt,asc' }),
    ])
      .then(([stats, finance, points, needReview, failed, processing]) => {
        if (!active) return
        setStatistics(stats)
        setFinancial(finance)
        setSeries(points)
        setPendingTotal(needReview.totalElements + failed.totalElements + processing.totalElements)
        setDocuments([...needReview.content, ...failed.content, ...processing.content].slice(0, 5))
      })
      .catch(() => active && setError('Không thể tải dữ liệu tổng quan. Vui lòng thử lại.'))
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [interval])

  const exportReport = async () => {
    setExporting(true)
    try { await exportService.exportFinancialReport({}) ; setError('') }
    catch { setError('Không thể xuất báo cáo. Vui lòng thử lại.') }
    finally { setExporting(false) }
  }

  if (loading) return <LoadingState label="Đang tải dữ liệu tổng quan..." />

  const chartColors = { income: '#059669', expense: '#e11d48', cashFlow: '#2563eb' }

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        eyebrow="Kế toán"
        title="Tổng quan Chứng từ & Tài chính"
        description="Đối soát dữ liệu hóa đơn tự động và cân đối dòng tiền kế toán thực tế."
        actions={
          <>
            <span className="si-chip border-hairline bg-canvas text-ink-soft"><CalendarDays size={20} />{periodLabel}</span>
            <button type="button" disabled={exporting} onClick={() => void exportReport()} className="si-btn-primary"><Download size={20} />{exporting ? 'Đang xuất...' : 'Xuất báo cáo'}</button>
          </>
        }
      />

      {error && <ErrorState message={error} />}

      {statistics && financial && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard title="Tổng tiếp nhận" value={statistics.totalDocuments} icon={FileText} type="primary" unit="chứng từ" hint="Tổng số chứng từ đã nhận trong kỳ" />
          <StatCard title="Đã tự động khớp" value={statistics.totalClassified} icon={CheckCircle2} type="success" unit="chứng từ" hint="Xử lý qua mô hình OCR / AI" />
          <StatCard title="Cần kiểm tra lại" value={statistics.totalReviewRequired} icon={AlertTriangle} type="warning" unit="chứng từ" hint="Lệch thuế, làm tròn hoặc mờ chữ ký" />
          <StatCard title="Dòng tiền ròng" value={Math.round(financial.cashFlow)} icon={WalletCards} type="primary" unit="₫" hint="Thu trừ chi trong kỳ" />
        </div>
      )}

      <ContentCard className="p-5">
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h2 className="si-section-title">Biến động Thu - Chi &amp; Dòng tiền</h2>
            <p className="si-section-subtitle mt-0.5">Dữ liệu phân bổ theo các mốc thời gian đã chọn.</p>
          </div>
          <div className="inline-flex shrink-0 self-start rounded-control border border-hairline bg-canvas p-0.5">
            {([['MONTHLY', 'Theo tháng'], ['DAILY', 'Theo ngày']] as const).map(([value, label]) => (
              <button
                key={value}
                type="button"
                onClick={() => setInterval(value)}
                className={`rounded-[4px] px-2.5 py-1 text-2xs font-semibold transition-colors ${interval === value ? 'bg-surface text-brand-600 shadow-elev1' : 'text-ink-soft hover:text-ink'}`}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
        {series.length === 0 ? (
          <p className="py-12 text-center text-[18px] text-ink-muted">Chưa có giao dịch trong khoảng thời gian này.</p>
        ) : (
          <div className="h-72">
            <Line
              data={{
                labels: series.map(x => x.period),
                datasets: [
                  { label: 'Thu thực tế', data: series.map(x => Number(x.income)), borderColor: chartColors.income, backgroundColor: chartColors.income, borderWidth: 3, tension: 0.3, pointRadius: 4.5 },
                  { label: 'Chi thực tế', data: series.map(x => Number(x.expense)), borderColor: chartColors.expense, backgroundColor: chartColors.expense, borderWidth: 3, tension: 0.3, pointRadius: 4.5 },
                  { label: 'Dòng tiền ròng', data: series.map(x => Number(x.cashFlow)), borderColor: chartColors.cashFlow, backgroundColor: chartColors.cashFlow, borderWidth: 3, tension: 0.3, pointRadius: 4.5 },
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
        <div className="flex flex-col gap-1 border-b border-hairline px-5 py-3.5">
          <h2 className="si-section-title">Chứng từ cần kiểm tra &amp; xử lý</h2>
          <p className="si-section-subtitle">Phát hiện tự động từ hệ thống đối soát hóa đơn.</p>
        </div>
        <div className="overflow-x-auto">
          <table className="si-table">
            <thead>
              <tr>
                <th>Mã chứng từ &amp; File</th>
                <th>Loại chứng từ</th>
                <th>Ngày lập</th>
                <th className="text-right">Số tiền (VNĐ)</th>
                <th>Vấn đề cần xác nhận</th>
                <th className="text-center">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {documents.length === 0 ? (
                <tr><td colSpan={6} className="py-10 text-center text-ink-muted">Chưa có chứng từ nào cần xử lý.</td></tr>
              ) : documents.map(doc => (
                <tr key={doc.id} onClick={() => navigate(`/accountant/documents/${doc.id}`)} className="cursor-pointer">
                  <td>
                    <div className="flex flex-col">
                      <span className="si-num text-[17px] font-semibold text-brand-700">#{doc.id}</span>
                      <span className="max-w-56 truncate text-2xs text-ink-muted" title={doc.fileName}>{doc.fileName}</span>
                    </div>
                  </td>
                  <td className="text-ink-soft">{doc.documentTypeName}</td>
                  <td className="si-num text-[17px] text-ink-soft">{doc.date}</td>
                  <td className="si-num text-right text-[17px] font-medium">{doc.amount == null ? '—' : `${Number(doc.amount).toLocaleString('vi-VN')} ₫`}</td>
                  <td>
                    <span className="inline-flex items-center gap-1.5 text-[17px] text-ink-soft">
                      <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${confidenceTone(doc.aiConfidence)}`} />
                      {issueLabel(doc)}
                    </span>
                  </td>
                  <td className="text-center">
                    <button
                      type="button"
                      onClick={(event) => { event.stopPropagation(); navigate(`/accountant/documents/${doc.id}`) }}
                      className="si-btn-secondary !px-2.5 !py-1 text-2xs"
                    >
                      Xử lý<ArrowRight size={20} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="flex flex-col items-start justify-between gap-2 border-t border-hairline px-5 py-3 sm:flex-row sm:items-center">
          <span className="si-num text-[17px] text-ink-soft">
            Hiển thị {documents.length} trên {pendingTotal.toLocaleString('vi-VN')} chứng từ cần duyệt
          </span>
          <button
            type="button"
            onClick={() => navigate('/accountant/documents', { state: { processingStatus: 'NEED_REVIEW' } })}
            className="si-link"
          >
            Xem toàn bộ {(statistics?.totalDocuments ?? 0).toLocaleString('vi-VN')} chứng từ<ArrowRight size={20} />
          </button>
        </div>
      </ContentCard>

    </div>
  )
}
export default AccountantDashboard

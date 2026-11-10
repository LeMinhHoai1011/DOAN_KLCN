import { useEffect, useState } from 'react'
import axios from 'axios'
import { Link } from 'react-router-dom'
import ContentCard from './ui/ContentCard'
import DateInput from './ui/DateInput'
import ErrorState from './ui/ErrorState'
import EmptyState from './ui/EmptyState'
import LoadingState from './ui/LoadingState'
import { AiChangeTable } from './AiChangesCard'
import { dateRangeError, formatDateTime } from '../utils/dateTime'
import { evaluationService, type AiAccuracyReport } from '../services/evaluationService'
import { evaluationError, evaluationPercent as percent } from '../utils/evaluationPresentation'
import { aiFieldLabel } from '../utils/aiChangePresentation'
import { aiDocumentStatusLabel, aiGroupLabel, aiSkipReasonLabel, countAiDocuments, filterAiDocuments, type AiDocumentFilter } from '../utils/aiAccuracyPresentation'

const STATUS_STYLE = { EXACT: 'text-approved', CHANGED: 'text-review', SKIPPED: 'text-ink-muted' } as const

/** Accuracy from approved documents only: every value is judged by what the accountant kept or changed. */
export default function AiAccuracyPanel({ documentBasePath = '/admin/documents' }: { documentBasePath?: string }) {
  const [report, setReport] = useState<AiAccuracyReport | null>(null)
  const [loading, setLoading] = useState(true), [error, setError] = useState('')
  const [from, setFrom] = useState(''), [to, setTo] = useState('')
  const [filter, setFilter] = useState<{ from?: string; to?: string }>({})
  const [documentFilter, setDocumentFilter] = useState<AiDocumentFilter>('ALL')
  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    evaluationService.aiAccuracy(filter).then(data => { if (active) setReport(data) })
      .catch(e => { if (active) setError(evaluationError(axios.isAxiosError(e) ? e.response?.status : undefined)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [filter])
  const apply = () => { const invalid = dateRangeError(from, to); if (invalid) { setError(invalid); return }; setFilter({ from: from || undefined, to: to || undefined }) }
  const tile = (label: string, value: string, hint?: string) => <div className="rounded-control border border-hairline p-3"><p className="text-xs text-ink-muted">{label}</p><p className="text-lg font-semibold">{value}</p>{hint && <p className="text-xs text-ink-muted">{hint}</p>}</div>
  const documents = report?.documents ?? []
  const counts = countAiDocuments(documents)
  const shown = filterAiDocuments(documents, documentFilter)
  const all = report?.groups?.find(group => group.group === 'ALL')

  return <ContentCard><div className="space-y-4 p-4">
    <div><h2 className="text-lg font-semibold">Độ chính xác AI theo kế toán</h2><p className="text-sm text-ink-muted">So sánh kết quả sau AI với dữ liệu lúc kế toán duyệt (kế toán đã xác nhận đúng, đủ). Giá trị được giữ nguyên tính là đúng; giá trị bị sửa, thêm hoặc xóa tính là sai. Không dùng điểm AI tự báo.</p></div>
    <div className="flex flex-wrap items-end gap-2">
      <label className="text-sm">Từ ngày<DateInput className="si-input mt-1" value={from} onChange={e => setFrom(e.target.value)} /></label>
      <label className="text-sm">Đến ngày<DateInput className="si-input mt-1" value={to} onChange={e => setTo(e.target.value)} /></label>
      <button type="button" className="si-btn-primary" onClick={apply}>Áp dụng</button>
    </div>
    {loading && <LoadingState />}
    {!loading && error && <ErrorState message={error} onRetry={() => setFilter({ ...filter })} />}
    {!loading && !error && report && report.comparedDocuments === 0 && <EmptyState title="Chưa có chứng từ để tính độ chính xác" description={report.approvedDocuments ? `${report.skippedDocuments} chứng từ đã duyệt không có đủ lịch sử để so sánh.` : 'Cần kế toán duyệt chứng từ sau khi AI xử lý.'} />}
    {!loading && !error && report && report.comparedDocuments > 0 && <>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {tile('Độ chính xác', percent(report.accuracy), `${report.correct}/${report.compared} giá trị được giữ nguyên`)}
        {tile('F1', percent(all?.f1), all ? `Precision ${percent(all.precision)} · Recall ${percent(all.recall)}` : undefined)}
        {tile('AI đúng hoàn toàn', `${report.unchangedDocuments}/${report.comparedDocuments}`, 'Chứng từ kế toán không phải sửa')}
        {tile('Sai / thiếu / thừa', `${report.wrong} / ${report.missing} / ${report.extra}`, 'Sửa giá trị / điền thêm / xóa')}
      </div>
      <p className="text-sm text-ink-muted">Đã so sánh {report.comparedDocuments}/{report.approvedDocuments} chứng từ đã duyệt.{report.skippedDocuments > 0 && ` ${report.skippedDocuments} chứng từ không đủ lịch sử nên không tính (xem cột Ghi chú bên dưới).`}</p>

      {report.groups && report.groups.length > 0 && <div className="overflow-x-auto"><table className="w-full text-left text-sm">
        <thead><tr><th className="p-2">Nhóm trường</th><th className="text-right">Đã so</th><th className="text-right">Đúng</th><th className="text-right">Sai</th><th className="text-right">Thiếu</th><th className="text-right">Thừa</th><th className="text-right">Precision</th><th className="text-right">Recall</th><th className="p-2 text-right">F1</th></tr></thead>
        <tbody>{report.groups.map(row => <tr key={row.group} className={`border-t border-hairline ${row.group === 'ALL' ? 'font-semibold' : ''}`}>
          <td className="p-2">{aiGroupLabel(row.group)}</td><td className="text-right">{row.compared}</td><td className="text-right">{row.correct}</td><td className="text-right">{row.wrong}</td><td className="text-right">{row.missing}</td><td className="text-right">{row.extra}</td>
          <td className="text-right">{percent(row.precision)}</td><td className="text-right">{percent(row.recall)}</td><td className="p-2 text-right">{percent(row.f1)}</td></tr>)}</tbody>
      </table><p className="mt-1 text-xs text-ink-muted">Precision = đúng / (đúng + sai + thừa); Recall = đúng / (đúng + sai + thiếu). Một giá trị bị sửa tính là một lần sai ở cả hai phía.</p></div>}

      {report.byField.length > 0 && <details className="rounded-control border border-hairline p-3"><summary className="cursor-pointer text-sm font-medium">Trường hay bị sửa ({report.byField.length})</summary><div className="mt-2 overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Trường</th><th>Sai</th><th>Thiếu</th><th>Thừa</th></tr></thead>
        <tbody>{report.byField.map(row => <tr key={row.field} className="border-t border-hairline"><td className="p-2">{aiFieldLabel(row.field)}</td><td>{row.wrong}</td><td>{row.missing}</td><td>{row.extra}</td></tr>)}</tbody></table></div></details>}
    </>}

    {!loading && !error && documents.length > 0 && <div className="space-y-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-semibold">Chứng từ kế toán đã duyệt</h3>
        <div className="flex flex-wrap gap-1" role="group" aria-label="Lọc chứng từ">
          {(['ALL', 'EXACT', 'CHANGED', 'SKIPPED'] as const).map(key => <button key={key} type="button" aria-pressed={documentFilter === key}
            className={documentFilter === key ? 'si-btn-primary text-xs' : 'si-btn-secondary text-xs'} onClick={() => setDocumentFilter(key)}>
            {key === 'ALL' ? 'Tất cả' : aiDocumentStatusLabel(key)} ({counts[key]})</button>)}
        </div>
      </div>
      <div className="overflow-x-auto"><table className="w-full min-w-[640px] text-left text-sm">
        <thead><tr><th className="p-2">Chứng từ</th><th>Ngày duyệt</th><th>Kết quả</th><th className="text-right">Đúng / đã so</th><th className="text-right">Độ chính xác</th><th className="p-2">Ghi chú</th></tr></thead>
        <tbody>{shown.map(row => <tr key={row.documentId} className="border-t border-hairline">
          <td className="p-2"><Link className="text-brand-700 hover:underline" to={`${documentBasePath}/${row.documentId}`}>#{row.documentId} · {row.fileName || 'Chưa có tên tệp'}</Link></td>
          <td>{formatDateTime(row.approvedAt)}</td>
          <td className={STATUS_STYLE[row.status]}>{aiDocumentStatusLabel(row.status)}</td>
          <td className="text-right">{row.status === 'SKIPPED' ? '—' : `${row.correct} / ${row.compared}`}</td>
          <td className="text-right">{row.status === 'SKIPPED' ? '—' : percent(row.accuracy)}</td>
          <td className="p-2 text-xs text-ink-muted">{row.status === 'CHANGED' ? `${row.changes} giá trị bị sửa` : aiSkipReasonLabel(row.skipReason)}</td>
        </tr>)}</tbody>
      </table></div>
      {!shown.length && <p className="text-sm text-ink-muted">Không có chứng từ trong nhóm này.</p>}
    </div>}

    {!loading && !error && report && report.recentChanges.length > 0 && <details className="rounded-control border border-hairline p-3"><summary className="cursor-pointer text-sm font-medium">Chi tiết các giá trị kế toán đã sửa ({report.recentChanges.length} chứng từ gần nhất)</summary>
      <div className="mt-2 space-y-2">{report.recentChanges.map(item => <details key={item.documentId} className="rounded-control border border-hairline p-3"><summary className="cursor-pointer text-sm font-medium">#{item.documentId} · {item.fileName} · {item.changes.length} thay đổi</summary><div className="mt-2"><AiChangeTable changes={item.changes} /></div></details>)}</div>
    </details>}
  </div></ContentCard>
}

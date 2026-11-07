import { useEffect, useState } from 'react'
import axios from 'axios'
import ContentCard from './ui/ContentCard'
import DateInput from './ui/DateInput'
import ErrorState from './ui/ErrorState'
import EmptyState from './ui/EmptyState'
import LoadingState from './ui/LoadingState'
import { AiChangeTable } from './AiChangesCard'
import { dateRangeError } from '../utils/dateTime'
import { evaluationService, type AiAccuracyReport } from '../services/evaluationService'
import { evaluationError, evaluationPercent as percent } from '../utils/evaluationPresentation'
import { aiFieldLabel } from '../utils/aiChangePresentation'

/** Accuracy from approved documents only: every value is judged by what the accountant kept or changed. */
export default function AiAccuracyPanel() {
  const [report, setReport] = useState<AiAccuracyReport | null>(null)
  const [loading, setLoading] = useState(true), [error, setError] = useState('')
  const [from, setFrom] = useState(''), [to, setTo] = useState('')
  const [filter, setFilter] = useState<{ from?: string; to?: string }>({})
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

  return <ContentCard><div className="space-y-4 p-4">
    <div><h2 className="text-lg font-semibold">Độ chính xác AI theo kế toán</h2><p className="text-sm text-ink-muted">So sánh kết quả sau AI với dữ liệu lúc kế toán duyệt. Giá trị được giữ nguyên tính là đúng; giá trị bị sửa, thêm hoặc xóa tính là sai. Không dùng điểm AI tự báo.</p></div>
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
        {tile('AI sai', String(report.wrong), 'Kế toán sửa giá trị')}
        {tile('AI thiếu', String(report.missing), 'Kế toán phải điền thêm')}
        {tile('AI thừa', String(report.extra), 'Kế toán phải xóa')}
      </div>
      <p className="text-sm text-ink-muted">Đã so sánh {report.comparedDocuments}/{report.approvedDocuments} chứng từ đã duyệt, {report.unchangedDocuments} chứng từ không phải sửa.{report.skippedDocuments > 0 && ` ${report.skippedDocuments} chứng từ không đủ lịch sử nên không tính.`}</p>
      {report.byField.length > 0 && <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Trường hay bị sửa</th><th>Sai</th><th>Thiếu</th><th>Thừa</th></tr></thead>
        <tbody>{report.byField.map(row => <tr key={row.field} className="border-t border-hairline"><td className="p-2">{aiFieldLabel(row.field)}</td><td>{row.wrong}</td><td>{row.missing}</td><td>{row.extra}</td></tr>)}</tbody></table></div>}
      {report.recentChanges.map(item => <details key={item.documentId} className="rounded-control border border-hairline p-3"><summary className="cursor-pointer text-sm font-medium">#{item.documentId} · {item.fileName} · {item.changes.length} thay đổi</summary><div className="mt-2"><AiChangeTable changes={item.changes} /></div></details>)}
    </>}
  </div></ContentCard>
}

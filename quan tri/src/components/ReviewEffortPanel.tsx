import { useEffect, useState } from 'react'
import axios from 'axios'
import ContentCard from './ui/ContentCard'
import DateInput from './ui/DateInput'
import ErrorState from './ui/ErrorState'
import EmptyState from './ui/EmptyState'
import LoadingState from './ui/LoadingState'
import { dateRangeError } from '../utils/dateTime'
import { evaluationService, type ReviewEffortReport } from '../services/evaluationService'
import { evaluationDuration as duration, evaluationError, evaluationPercent as percent } from '../utils/evaluationPresentation'

/** Real review effort after AI processing: the operational half of the AI-vs-manual comparison. */
export default function ReviewEffortPanel() {
  const [report, setReport] = useState<ReviewEffortReport | null>(null)
  const [loading, setLoading] = useState(true), [error, setError] = useState('')
  const [from, setFrom] = useState(''), [to, setTo] = useState('')
  const [filter, setFilter] = useState<{ from?: string; to?: string }>({})
  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    evaluationService.reviewEffort(filter).then(data => { if (active) setReport(data) })
      .catch(e => { if (active) setError(evaluationError(axios.isAxiosError(e) ? e.response?.status : undefined)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [filter])
  const apply = () => { const invalid = dateRangeError(from, to); if (invalid) { setError(invalid); return }; setFilter({ from: from || undefined, to: to || undefined }) }
  const tile = (label: string, value: string, hint?: string) => <div className="rounded-control border border-hairline p-3"><p className="text-xs text-ink-muted">{label}</p><p className="text-lg font-semibold">{value}</p>{hint && <p className="text-xs text-ink-muted">{hint}</p>}</div>

  return <ContentCard><div className="space-y-4 p-4">
    <div><h2 className="text-lg font-semibold">Công sức kiểm duyệt sau AI</h2><p className="text-sm text-ink-muted">Thời gian kế toán thực sự xem chứng từ và tỉ lệ duyệt không cần sửa kết quả AI. So sánh với thời gian nhập tay đo riêng để tính mức tiết kiệm.</p></div>
    <div className="flex flex-wrap items-end gap-2">
      <label className="text-sm">Từ ngày<DateInput className="si-input mt-1" value={from} onChange={e => setFrom(e.target.value)} /></label>
      <label className="text-sm">Đến ngày<DateInput className="si-input mt-1" value={to} onChange={e => setTo(e.target.value)} /></label>
      <button type="button" className="si-btn-primary" onClick={apply}>Áp dụng</button>
    </div>
    {loading && <LoadingState />}
    {!loading && error && <ErrorState message={error} onRetry={() => setFilter({ ...filter })} />}
    {!loading && !error && report && report.decisions === 0 && <EmptyState title="Chưa có quyết định duyệt" description="Chưa có lượt duyệt, từ chối hoặc yêu cầu bổ sung trong khoảng thời gian này." />}
    {!loading && !error && report && report.decisions > 0 && <>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {tile('Chứng từ đã duyệt', String(report.approvedDocuments), `${report.decisions} quyết định · ${report.rejections} từ chối · ${report.infoRequests} yêu cầu bổ sung`)}
        {tile('Duyệt không cần sửa (touchless)', percent(report.touchlessRate), `${report.touchlessApproved}/${report.approvedDocuments} chứng từ`)}
        {tile('Thời gian duyệt TB / chứng từ', duration(report.averageReviewMsPerDocument), `Trung vị ${duration(report.medianReviewMsPerDocument)} · đo được ${report.timedApprovedDocuments}/${report.approvedDocuments}`)}
        {tile('Số lần sửa TB / chứng từ', report.averageEditsPerApprovedDocument == null ? 'Chưa xác định' : report.averageEditsPerApprovedDocument.toFixed(2))}
      </div>
      <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Loại chứng từ</th><th>Đã duyệt</th><th>Touchless</th><th>Đo thời gian</th><th>Thời gian TB</th></tr></thead>
        <tbody>{Object.entries(report.byDocumentType).map(([type, t]) => <tr key={type} className="border-t border-hairline"><td className="p-2">{type}</td><td>{t.approved}</td><td>{t.touchless} · {percent(t.touchlessRate)}</td><td>{t.timed}/{t.approved}</td><td>{duration(t.averageReviewMs)}</td></tr>)}</tbody></table></div>
      <div className="text-sm text-ink-muted">{report.notes.map(note => <p key={note}>{note}</p>)}</div>
    </>}
  </div></ContentCard>
}

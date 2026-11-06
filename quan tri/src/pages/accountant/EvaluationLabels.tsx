import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import PageHeader from '../../components/ui/PageHeader'
import ContentCard from '../../components/ui/ContentCard'
import EmptyState from '../../components/ui/EmptyState'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import DateInput from '../../components/ui/DateInput'
import { getEffectiveRole, getErrorMessage } from '../../services/authService'
import { liveEvaluationService, type SampleSummary } from '../../services/liveEvaluationService'
import { formatDateTime } from '../../utils/dateTime'
import { statusLabels } from '../../utils/liveEvaluationPresentation'

export default function EvaluationLabels() {
  const navigate = useNavigate()
  const basePath = getEffectiveRole() === 'ADMIN' ? '/admin' : '/accountant'
  const [samples, setSamples] = useState<SampleSummary[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [perType, setPerType] = useState('10')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [isSelecting, setIsSelecting] = useState(false)
  const [message, setMessage] = useState('')

  const load = useCallback(() => {
    setIsLoading(true); setError('')
    liveEvaluationService.list().then(setSamples).catch(failure => setError(getErrorMessage(failure, 'Không thể tải bộ đánh giá.'))).finally(() => setIsLoading(false))
  }, [])
  useEffect(load, [load])

  const selectRandom = async (event: FormEvent) => {
    event.preventDefault()
    setIsSelecting(true); setMessage(''); setError('')
    try {
      const result = await liveEvaluationService.selectRandom({ perType: Number(perType), dateFrom: dateFrom || undefined, dateTo: dateTo || undefined })
      setMessage(`Đã thêm ${result.added} mẫu ngẫu nhiên từ ${result.candidates} chứng từ phù hợp.`)
      load()
    } catch (failure) { setError(getErrorMessage(failure, 'Không thể chọn mẫu.')) }
    finally { setIsSelecting(false) }
  }

  const count = (status: SampleSummary['status']) => samples.filter(sample => sample.status === status).length
  return <div className="flex flex-col gap-5">
    <PageHeader eyebrow="Đánh giá OCR/AI" title="Gán nhãn chứng từ thực" description="Đối chiếu file gốc để tạo đáp án chuẩn. Giá trị AI chỉ hiện sau khi xác nhận nhãn, và nhãn được lưu riêng với dữ liệu nghiệp vụ." />
    <ContentCard><form onSubmit={event => void selectRandom(event)} className="flex flex-wrap items-end gap-3 p-4">
      <label className="grid gap-1"><span className="si-field-label">Số mẫu mỗi loại chứng từ</span><input type="number" min={1} max={50} value={perType} onChange={event => setPerType(event.target.value)} className="si-input w-32" /></label>
      <label className="grid gap-1"><span className="si-field-label">Từ ngày</span><DateInput value={dateFrom} onChange={event => setDateFrom(event.target.value)} className="si-input" /></label>
      <label className="grid gap-1"><span className="si-field-label">Đến ngày</span><DateInput value={dateTo} onChange={event => setDateTo(event.target.value)} className="si-input" /></label>
      <button disabled={isSelecting} className="si-btn-primary">{isSelecting ? 'Đang chọn...' : 'Chọn ngẫu nhiên'}</button>
      <p className="w-full text-xs text-ink-muted">Nên chọn ngẫu nhiên thay vì chọn tay, để bộ mẫu không chỉ gồm chứng từ AI đọc tốt hoặc đọc sai.</p>
      {message && <p className="w-full text-sm text-parsed">{message}</p>}
    </form></ContentCard>
    <ContentCard>
      <div className="flex flex-wrap gap-x-6 gap-y-1 border-b border-hairline p-4 text-sm"><span>Đã chọn: <strong>{samples.length}</strong></span><span>Đang gán nhãn: <strong>{count('DRAFT')}</strong></span><span>Đã xác nhận: <strong>{count('CONFIRMED')}</strong></span><span>File đã đổi: <strong>{samples.filter(sample => sample.stale).length}</strong></span></div>
      {error ? <div className="p-4"><ErrorState message={error} onRetry={load} /></div> : isLoading ? <LoadingState label="Đang tải bộ đánh giá..." /> : samples.length === 0
        ? <EmptyState title="Chưa có mẫu đánh giá" description="Chọn ngẫu nhiên ở trên, hoặc mở một chứng từ và bấm “Đưa vào bộ đánh giá”." />
        : <div className="overflow-x-auto"><table className="si-table"><thead><tr><th>Chứng từ</th><th>Loại CT</th><th>Cách chọn</th><th>Thời điểm chọn</th><th>Trạng thái</th></tr></thead><tbody>{samples.map(sample => <tr key={sample.documentId} onClick={() => navigate(`${basePath}/evaluation-labels/${sample.documentId}`)} className="cursor-pointer"><td><span className="si-num font-semibold text-brand-700">#{sample.documentId}</span> <span className="text-ink-muted">{sample.fileName}</span></td><td>{sample.documentType ?? '—'}</td><td>{sample.selection === 'RANDOM' ? 'Ngẫu nhiên' : 'Chọn tay'}</td><td>{formatDateTime(sample.selectedAt)}</td><td>{statusLabels[sample.status]}{sample.stale && <span className="ml-2 text-review">· File đã đổi</span>}{sample.revisedAfterReveal && <span className="ml-2 text-review">· Sửa sau khi xem AI</span>}</td></tr>)}</tbody></table></div>}
    </ContentCard>
  </div>
}

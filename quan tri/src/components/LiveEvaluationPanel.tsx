import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import ContentCard from './ui/ContentCard'
import EmptyState from './ui/EmptyState'
import ErrorState from './ui/ErrorState'
import LoadingState from './ui/LoadingState'
import { fieldTable, ReconciliationScoreBlock } from './OcrAiPerformance'
import { getErrorMessage } from '../services/authService'
import { liveEvaluationService, type LiveReport } from '../services/liveEvaluationService'
import { evaluationDuration, evaluationPercent as percent } from '../utils/evaluationPresentation'

export default function LiveEvaluationPanel() {
  const [report, setReport] = useState<LiveReport | null>(null)
  const [error, setError] = useState('')
  const load = useCallback(() => {
    setError(''); setReport(null)
    liveEvaluationService.report().then(setReport).catch(failure => setError(getErrorMessage(failure, 'Không thể tải báo cáo chứng từ thực.')))
  }, [])
  useEffect(load, [load])

  return <ContentCard><div className="flex flex-wrap items-center justify-between gap-2 border-b border-hairline px-5 py-3.5"><div><h2 className="si-section-title">Đánh giá trên chứng từ thực</h2><p className="mt-1 text-sm text-ink-muted">So sánh kết quả OCR/AI gốc với nhãn đã xác nhận. Chưa có nhãn thì chưa có độ chính xác.</p></div><Link to="/admin/evaluation-labels" className="si-btn-secondary">Gán nhãn</Link></div><div className="space-y-5 p-5">
    {error && <ErrorState message={error} onRetry={load} />}{!error && !report && <LoadingState label="Đang tải báo cáo..." />}
    {report && <>
      <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm"><span>Đã chọn: <strong>{report.selected}</strong> ({report.randomSelected} ngẫu nhiên, {report.manualSelected} chọn tay)</span><span>Đang gán nhãn: <strong>{report.labelling}</strong></span><span>Đã xác nhận: <strong>{report.confirmed}</strong></span><span>Được tính điểm: <strong>{report.scored}</strong></span><span>Thời gian AI TB: <strong>{evaluationDuration(report.averageAiMs)}</strong></span></div>
      <p className="text-sm text-ink-muted">Trường không đọc được (không tính): {report.unreadableFields} · File đã đổi: {report.stale} · Văn bản chuẩn dựa trên OCR: {report.ocrAssisted} · Sửa sau khi xem AI: {report.revisedAfterReveal}</p>
      {report.models.length === 0 ? <EmptyState title="Chưa có mẫu được tính điểm" description="Xác nhận nhãn ít nhất một mẫu để xem độ chính xác." /> : report.models.map(({ model, samples, score }) => <section key={model} className="space-y-3 border-t border-hairline pt-4"><h3 className="font-semibold">Model {model} · {samples} mẫu</h3>
        <p className="text-sm">CER {percent(score.cer)} · WER {percent(score.wer)} ({score.ocrSamples} mẫu có văn bản chuẩn) · Phân loại loại chứng từ {percent(score.classificationAccuracy)}</p>
        {fieldTable(score.fields)}
        <p className="text-sm">Gộp mọi trường: Precision {percent(score.fieldTotals.precision)} · Recall {percent(score.fieldTotals.recall)} · F1 {percent(score.fieldTotals.f1)}. Chứng từ đúng hoàn toàn: {score.exactDocuments}/{score.exactEligible} · {percent(score.exactDocumentRate)}.</p>
        <p className="text-sm">Hàng hóa: {score.itemCells.correct}/{score.itemCells.expected} ô đúng · {percent(score.itemCells.accuracy)}; ô thiếu {score.itemCells.missing}, ô sai {score.itemCells.incorrect}, dòng thừa {score.extraItemRows}.</p>
        {Object.entries(score.costCategoryMetrics ?? {}).map(([taxonomy, category]) => <p key={taxonomy} className="text-sm">Nhóm chi phí ({category.samples} mẫu): Accuracy {percent(category.accuracy)} · Macro F1 {percent(category.macroF1)}</p>)}
        <div><h4 className="font-semibold">Tỷ lệ đối soát đúng</h4><ReconciliationScoreBlock score={score.reconciliation} /></div>
      </section>)}
      <ul className="list-disc space-y-1 pl-5 text-xs text-ink-muted">{report.notes.map(note => <li key={note}>{note}</li>)}</ul>
    </>}
  </div></ContentCard>
}

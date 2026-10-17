import { dateRangeError } from '../utils/dateTime'
import { formatDateTime } from '../utils/dateTime'
import DateInput from './ui/DateInput'
import { useEffect, useState } from 'react'
import axios from 'axios'
import { Bar } from 'react-chartjs-2'
import { BarElement, CategoryScale, Chart as ChartJS, Legend, LinearScale, Tooltip } from 'chart.js'
import ContentCard from './ui/ContentCard'
import ErrorState from './ui/ErrorState'
import EmptyState from './ui/EmptyState'
import LoadingState from './ui/LoadingState'
import { evaluationService, type EvaluationFilter, type EvaluationReport, type FieldScore } from '../services/evaluationService'
import { evaluationDateBounds, evaluationDuration, evaluationError, evaluationPercent as percent } from '../utils/evaluationPresentation'

ChartJS.register(BarElement, CategoryScale, LinearScale, Tooltip, Legend)
const strategies: Record<string, string> = { A: 'A · Tesseract + Text', B: 'B · Tiền xử lý + Tesseract + Text', C: 'C · Đánh giá OCR + Vision fallback' }
const fieldTable = (fields: Record<string, FieldScore>) => <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Trường</th><th>Đúng / chuẩn</th><th>Accuracy</th><th>Thiếu</th><th>Sai</th></tr></thead><tbody>{Object.entries(fields).map(([field, s]) => <tr key={field} className="border-t border-hairline"><td className="p-2">{field}</td><td>{s.correct} / {s.expected}</td><td>{percent(s.accuracy)}</td><td>{s.missing} ({percent(s.missingRate)})</td><td>{s.incorrect} ({percent(s.incorrectRate)})</td></tr>)}</tbody></table></div>

export default function OcrAiPerformance() {
  const [report, setReport] = useState<EvaluationReport | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [exportError, setExportError] = useState('')
  const [exporting, setExporting] = useState(false)
  const [model, setModel] = useState(''), [strategy, setStrategy] = useState('')
  const [from, setFrom] = useState(''), [to, setTo] = useState('')
  const [filter, setFilter] = useState<EvaluationFilter>({})
  const [refresh, setRefresh] = useState(0), [selected, setSelected] = useState('')
  useEffect(() => {
    let active = true
    setLoading(true); setError(''); setReport(null); setExportError('')
    evaluationService.report(filter).then(data => {
      if (active) { setReport(data); setSelected(data.runs[0]?.runId ?? '') }
    }).catch(e => { if (active) setError(evaluationError(axios.isAxiosError(e) ? e.response?.status : undefined)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [filter, refresh])
  const run = report?.runs.find(r => r.runId === selected), s = run?.metrics
  const apply = () => { if (dateRangeError(from,to)) { setError(dateRangeError(from,to)); return }; setFilter({ model: model.trim() || undefined, strategy: strategy || undefined, ...evaluationDateBounds(from, to) }) }
  const download = async () => {
    setExporting(true); setExportError('')
    try { const blob = await evaluationService.export(filter); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = 'ocr-ai-evaluation.json'; a.click(); URL.revokeObjectURL(url) }
    catch (e) { setExportError(evaluationError(axios.isAxiosError(e) ? e.response?.status : undefined)) }
    finally { setExporting(false) }
  }
  return <ContentCard><div className="border-b border-hairline px-5 py-3.5"><h2 className="si-section-title">Hiệu suất OCR/AI</h2><p className="mt-1 text-sm text-ink-muted">Đánh giá trên mẫu có ground truth kiểm tra thủ công. Confidence không phải độ chính xác.</p></div><div className="space-y-5 p-5">
    <form onSubmit={e => { e.preventDefault(); apply() }} className="flex flex-wrap items-end gap-3">
      <label className="text-sm">Model<input className="si-input mt-1" value={model} onChange={e => setModel(e.target.value)} placeholder="Tên model chính xác" /></label>
      <label className="text-sm">Phương án<select className="si-input mt-1" value={strategy} onChange={e => setStrategy(e.target.value)}><option value="">Tất cả</option>{Object.entries(strategies).map(([key, name]) => <option key={key} value={key}>{name}</option>)}</select></label>
      <label className="text-sm">Từ ngày<DateInput className="si-input mt-1"  value={from} onChange={e => setFrom(e.target.value)} /></label>
      <label className="text-sm">Đến ngày<DateInput className="si-input mt-1"  value={to} onChange={e => setTo(e.target.value)} /></label>
      <button className="si-btn-primary" disabled={loading}>Áp dụng</button><button type="button" className="si-btn-secondary" disabled={loading} onClick={() => setRefresh(x => x + 1)}>Làm mới</button>
      <button type="button" className="si-btn-secondary" disabled={exporting || loading || !report?.runs.length} onClick={download}>{exporting ? 'Đang xuất...' : 'Xuất JSON'}</button>
    </form>
    {loading && <LoadingState label="Đang tải dữ liệu đánh giá..." />}{error && <ErrorState message={error} onRetry={() => setRefresh(x => x + 1)} />}{exportError && <ErrorState message={exportError} onRetry={download} />}
    {!loading && report && !report.runs.length && <EmptyState title="Chưa có dữ liệu kiểm thử" description={report.status === 'NOT_CONFIGURED' ? 'Chưa cấu hình bộ dữ liệu đánh giá độc lập đã được xác minh.' : 'Chưa có lần thực nghiệm phù hợp với bộ lọc.'} />}
    {report && report.runs.length > 0 && <>
      <p className={report.comparable ? 'text-sm text-emerald-700' : 'text-sm text-amber-700'}>{report.comparable ? 'A/B/C cùng bộ mẫu, model digest, môi trường và phiên bản code; cấu hình chi tiết có trong bản xuất.' : 'Chưa đủ điều kiện kết luận so sánh A/B/C: cần đúng một lần chạy đầy đủ cho mỗi phương án, cùng mẫu chuẩn, model digest, môi trường và phiên bản code.'}</p>
      <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Lần chạy / phương án</th><th>Mẫu</th><th>CER ↓</th><th>WER ↓</th><th>Accuracy phân loại</th><th>Thành công</th><th>Thất bại</th><th>Thời gian TB</th><th>Vision fallback</th><th>Retry</th></tr></thead><tbody>{report.runs.map(r => <tr key={r.runId} className="border-t border-hairline"><td className="p-2"><button type="button" className="text-primary underline" onClick={() => setSelected(r.runId)}>{r.runId}</button><div>{strategies[r.strategy]} · {r.model}</div></td><td>{r.evaluatedSamples}/{r.datasetSamples}{!r.complete && ' · Chưa đủ'}</td><td>{percent(r.metrics.cer)}</td><td>{percent(r.metrics.wer)}</td><td>{percent(r.metrics.classificationAccuracy)}</td><td>{percent(r.metrics.successRate)}</td><td>{percent(r.metrics.failureRate)}</td><td>{evaluationDuration(r.metrics.averageProcessingMs)}</td><td>{percent(r.metrics.visionFallbackRate)}</td><td>{percent(r.metrics.retryRate)}</td></tr>)}</tbody></table></div>
      {run && s && <>
        <div className="rounded-lg border border-hairline p-3 text-sm"><strong>{run.runId} · {run.datasetId} / {run.datasetVersion}</strong><p>{formatDateTime(run.startedAt)} · {run.environment}</p><p className="break-all text-ink-muted">Ground truth SHA256: {run.groundTruthSha256}</p><p>Phủ mẫu: {run.coverageTags.join(', ') || 'Chưa có nhãn'}.</p><p>Mẫu đo OCR: {s.ocrSamples}; phân loại: {s.classificationSamples}; thời gian: {s.timedSamples}; fallback: {s.fallbackObserved}; số lần thử: {s.attemptsObserved}.</p></div>
        <div className="grid gap-5 xl:grid-cols-2"><div><h3 className="font-semibold">Chất lượng theo loại chứng từ</h3><div className="mt-3 h-64"><Bar data={{ labels: Object.keys(run.byDocumentType), datasets: [{ label: 'Accuracy phân loại (%)', data: Object.values(run.byDocumentType).map(m => m.classificationAccuracy == null ? null : m.classificationAccuracy * 100), backgroundColor: '#7c3aed' }, { label: 'Xử lý thành công (%)', data: Object.values(run.byDocumentType).map(m => m.successRate == null ? null : m.successRate * 100), backgroundColor: '#059669' }] }} options={{ responsive: true, maintainAspectRatio: false, scales: { y: { beginAtZero: true, max: 100 } } }} /></div></div>
        <div><h3 className="font-semibold">Phân loại theo lớp</h3><p className="text-sm">Macro P/R/F1: {percent(s.macroPrecision)} / {percent(s.macroRecall)} / {percent(s.macroF1)}</p><table className="mt-3 w-full text-left text-sm"><thead><tr><th>Lớp</th><th>Mẫu chuẩn</th><th>Precision</th><th>Recall</th><th>F1</th></tr></thead><tbody>{Object.entries(s.classes).map(([name, c]) => <tr key={name}><td>{name}</td><td>{c.support}</td><td>{percent(c.precision)}</td><td>{percent(c.recall)}</td><td>{percent(c.f1)}</td></tr>)}</tbody></table></div></div>
        <div><h3 className="font-semibold">Độ chính xác trích xuất theo trường</h3>{Object.keys(s.fields).length ? fieldTable(s.fields) : <p className="text-sm text-ink-muted">Chưa có ground truth theo trường.</p>}</div>
        <div><h3 className="font-semibold">Text và Vision — nhánh xử lý thực đo</h3><p className="text-sm text-ink-muted">Hai nhóm có thể gồm mẫu với độ khó khác nhau; không dùng bảng này để kết luận Vision tốt hơn Text trên cùng đầu vào.</p><div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Nhánh</th><th>Mẫu</th><th>CER</th><th>WER</th><th>Accuracy phân loại</th><th>Thời gian TB</th></tr></thead><tbody>{Object.entries(run.byProcessingMode).map(([mode, m]) => <tr key={mode} className="border-t border-hairline"><td className="p-2">{mode === 'TEXT' ? 'Text (không fallback)' : mode === 'VISION_FALLBACK' ? 'Có Vision fallback' : 'Chưa ghi nhận nhánh'}</td><td>{m.samples}</td><td>{percent(m.cer)}</td><td>{percent(m.wer)}</td><td>{percent(m.classificationAccuracy)}</td><td>{evaluationDuration(m.averageProcessingMs)}</td></tr>)}</tbody></table></div></div>
        <div><h3 className="font-semibold">Nhận dạng trường OCR</h3>{Object.keys(s.ocrFields).length ? fieldTable(s.ocrFields) : <p className="text-sm text-ink-muted">Chưa có dữ liệu đối chiếu trường OCR.</p>}</div>
        <p className="text-sm">Chi tiết hàng hóa (ô đúng theo thứ tự dòng): {s.itemCells.correct}/{s.itemCells.expected} · {percent(s.itemCells.accuracy)}. Ô thiếu: {s.itemCells.missing}; ô sai: {s.itemCells.incorrect}; dòng thừa: {s.extraItemRows}; trường ngoài bộ chuẩn: {s.extraFields}.</p>
        <div><h3 className="font-semibold">Confusion matrix</h3><p className="text-sm text-ink-muted">Dòng: loại chuẩn; cột: kết quả dự đoán. “Không dự đoán” vẫn được tính là sai.</p><div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Chuẩn / Dự đoán</th>{Object.keys(s.classes).map(k => <th key={k} className="p-2">{k}</th>)}<th>Không dự đoán</th></tr></thead><tbody>{Object.entries(s.confusionMatrix).map(([truth, counts]) => <tr key={truth} className="border-t border-hairline"><td className="p-2">{truth}</td>{Object.keys(s.classes).map(k => <td className="p-2" key={k}>{counts[k] ?? 0}</td>)}<td>{counts.__NO_PREDICTION__ ?? 0}</td></tr>)}</tbody></table></div></div>
        <div><h3 className="font-semibold">Lỗi kỹ thuật: {s.failed} / {s.samples}</h3>{Object.entries(s.failuresByCode).map(([code, count]) => <p key={code} className="text-sm">{code}: {count}</p>)}</div>
      </>}
      <div className="text-sm text-ink-muted">{report.notes.map(note => <p key={note}>{note}</p>)}<p>Phương pháp: {report.metricVersion}. CER/WER có thể vượt 100% khi có nhiều ký tự/từ thừa.</p></div>
    </>}
  </div></ContentCard>
}

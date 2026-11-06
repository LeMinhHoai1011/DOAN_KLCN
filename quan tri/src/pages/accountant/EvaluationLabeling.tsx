import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Plus, Trash2 } from 'lucide-react'
import OcrDocumentPreview from '../../components/OcrDocumentPreview'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import accountingCategoryService, { type AccountingCategory } from '../../services/accountingCategoryService'
import { getEffectiveRole, getErrorMessage } from '../../services/authService'
import documentService, { type DocumentType } from '../../services/documentService'
import { liveEvaluationService, type Label, type LabelState, type Workspace } from '../../services/liveEvaluationService'
import { fieldLabels, itemColumns, missingLabels, outcomeLabels, stateLabels, statusLabels, toEditableRow, toStoredRow } from '../../utils/liveEvaluationPresentation'
import { reconciliationRuleLabel, reconciliationStatusLabel } from '../../utils/reconciliationPresentation'

const STATES: LabelState[] = ['UNCHECKED', 'PRESENT', 'ABSENT', 'UNREADABLE']
const RECONCILIATION_STATUSES = ['MATCHED', 'MISMATCH', 'INSUFFICIENT_DATA']
const noop = async () => undefined
const noFields: never[] = []

interface Draft {
  documentType: string; fields: Record<string, Label>; category: Label; itemsState: LabelState
  items: { key: number; cells: Record<string, string> }[]; ocrText: string
}
let nextRow = 1
const toDraft = ({ sample }: Workspace): Draft => ({
  documentType: sample.documentType ?? '', fields: sample.fields, category: sample.category, itemsState: sample.itemsState,
  items: sample.items.map(row => ({ key: nextRow++, cells: toEditableRow(row) })), ocrText: sample.ocrText ?? '',
})
const toRequestBody = (draft: Draft) => ({
  documentType: draft.documentType || null, fields: draft.fields, category: draft.category, itemsState: draft.itemsState,
  items: draft.items.map(row => toStoredRow(row.cells)).filter(row => Object.keys(row).length > 0), ocrText: draft.ocrText.trim() ? draft.ocrText : null,
})

function LabelInput({ label, value, onChange, disabled, children }: { label: string; value: Label; onChange: (next: Label) => void; disabled: boolean; children?: React.ReactNode }) {
  return <div className="grid gap-1 border-b border-hairline py-2 sm:grid-cols-[minmax(0,10fr)_minmax(0,9fr)_minmax(0,12fr)] sm:items-center sm:gap-2">
    <span className="text-sm font-medium">{label}</span>
    <select aria-label={`Trạng thái ${label}`} value={value.state} disabled={disabled} onChange={event => { const state = event.target.value as LabelState; onChange({ state, value: state === 'PRESENT' ? value.value : null }) }} className="si-select">{STATES.map(state => <option key={state} value={state}>{stateLabels[state]}</option>)}</select>
    {value.state === 'PRESENT' ? children ?? <input aria-label={`Giá trị ${label}`} value={value.value ?? ''} disabled={disabled} onChange={event => onChange({ ...value, value: event.target.value })} className="si-input" /> : <span />}
  </div>
}

export default function EvaluationLabeling() {
  const { id } = useParams()
  const documentId = Number(id)
  const navigate = useNavigate()
  const role = getEffectiveRole()
  const [workspace, setWorkspace] = useState<Workspace | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [saved, setSaved] = useState('')
  const [reconciliation, setReconciliation] = useState<Record<string, string> | null>(null)
  const [documentTypes, setDocumentTypes] = useState<DocumentType[]>([])
  const [categories, setCategories] = useState<AccountingCategory[]>([])
  const [loadError, setLoadError] = useState('')
  const [error, setError] = useState('')
  const [isBusy, setIsBusy] = useState(false)

  const apply = useCallback((next: Workspace) => {
    const nextDraft = toDraft(next)
    setWorkspace(next); setDraft(nextDraft); setSaved(JSON.stringify(toRequestBody(nextDraft)))
    setReconciliation(next.sample.reconciliation ?? next.reconciliationDraft)
  }, [])
  const load = useCallback(() => {
    setLoadError('')
    liveEvaluationService.workspace(documentId).then(next => {
      apply(next)
      void accountingCategoryService.getCategories(role === 'ADMIN' ? next.sample.companyId : undefined).then(setCategories).catch(() => setCategories([]))
    }).catch(failure => setLoadError(getErrorMessage(failure, 'Không thể tải mẫu đánh giá.')))
  }, [apply, documentId, role])
  useEffect(load, [load])
  useEffect(() => { void documentService.getDocumentTypes().then(setDocumentTypes).catch(() => setDocumentTypes([])) }, [])

  const dirty = useMemo(() => Boolean(draft) && JSON.stringify(toRequestBody(draft as Draft)) !== saved, [draft, saved])
  useEffect(() => {
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  if (loadError) return <ErrorState message={loadError} onRetry={load} />
  if (!workspace || !draft) return <LoadingState label="Đang tải mẫu đánh giá..." />

  const { sample, reveal } = workspace
  const confirmed = sample.status === 'CONFIRMED'
  const update = (change: Partial<Draft>) => setDraft({ ...draft, ...change })
  const run = async (action: () => Promise<Workspace>, fallback: string) => {
    setIsBusy(true); setError('')
    try { apply(await action()) } catch (failure) { setError(getErrorMessage(failure, fallback)) } finally { setIsBusy(false) }
  }
  const save = () => run(() => liveEvaluationService.save(documentId, { revision: sample.revision, ...toRequestBody(draft) }), 'Không thể lưu nhãn. Dữ liệu đang nhập vẫn được giữ nguyên.')
  const confirm = () => run(() => liveEvaluationService.confirm(documentId, sample.revision, reconciliation), 'Không thể xác nhận nhãn.')
  const reopen = () => { if (window.confirm('Bạn đã thấy giá trị AI. Mở lại sẽ ghi nhận nhãn này được sửa sau khi xem kết quả. Tiếp tục?')) void run(() => liveEvaluationService.reopen(documentId, sample.revision), 'Không thể mở lại nhãn.') }
  const startFromOcr = async () => {
    if (draft.ocrText.trim() && !window.confirm('Thay nội dung đang nhập bằng văn bản OCR?')) return
    setError('')
    try { const text = await liveEvaluationService.ocrDraft(documentId); update({ ocrText: text }) }
    catch (failure) { setError(getErrorMessage(failure, 'Không thể lấy văn bản OCR.')) }
  }
  const missing = missingLabels(toRequestBody(draft))
  const companyCategories = categories.map(category => <option key={category.id} value={category.categoryCode}>{category.categoryName}</option>)

  return <div className="flex flex-col gap-3">
    <div className="flex flex-wrap items-center gap-3">
      <button aria-label="Quay lại" onClick={() => { if (!dirty || window.confirm('Bạn có nhãn chưa lưu. Rời trang?')) navigate(`/${role === 'ADMIN' ? 'admin' : 'accountant'}/evaluation-labels`) }} className="si-btn-ghost rounded-full p-2"><ArrowLeft size={20} /></button>
      <div className="min-w-0"><h1 className="si-section-title truncate">Gán nhãn · #{documentId} · {workspace.fileName}</h1><p className="text-xs text-ink-muted">{statusLabels[sample.status]}{sample.revisedAfterReveal ? ' · Đã sửa sau khi xem AI' : ''}</p></div>
    </div>
    {workspace.stale && <p className="rounded-control border border-review/25 bg-review-surface p-3 text-sm text-review">File đã đổi so với lần AI được đánh giá. Nhãn này không được tính điểm.</p>}
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(420px,1fr)]">
      <div className="h-[calc(68vh/var(--ui-zoom))] min-h-[520px]"><OcrDocumentPreview documentId={documentId} fileType={workspace.fileType} ocr={null} fields={noFields} showFieldPanel={false} canCorrectFields={false} onCorrectField={noop} /></div>
      <div className="flex min-w-0 flex-col gap-3">
        {!confirmed && <p className="rounded-control bg-subtle p-3 text-sm text-ink-soft">Nhập theo những gì nhìn thấy trên file gốc. Kết quả AI chỉ hiện sau khi xác nhận.</p>}
        <section className="si-card p-4"><label className="grid gap-1"><span className="si-field-label">Loại chứng từ đúng</span><select value={draft.documentType} disabled={confirmed} onChange={event => update({ documentType: event.target.value })} className="si-select"><option value="">Chưa chọn</option>{documentTypes.map(type => <option key={type.id} value={type.code}>{type.name}</option>)}</select></label></section>
        <section className="si-card p-4"><h2 className="si-section-title mb-2">Trường dữ liệu</h2>{workspace.fieldCatalog.map(key => <LabelInput key={key} label={fieldLabels[key] ?? key} value={draft.fields[key]} disabled={confirmed} onChange={next => update({ fields: { ...draft.fields, [key]: next } })} />)}
          <LabelInput label="Nhóm chi phí" value={draft.category} disabled={confirmed} onChange={next => update({ category: next })}><select aria-label="Giá trị nhóm chi phí" value={draft.category.value ?? ''} disabled={confirmed} onChange={event => update({ category: { ...draft.category, value: event.target.value } })} className="si-select"><option value="">Chưa chọn</option>{companyCategories}</select></LabelInput>
        </section>
        <section className="si-card p-4"><div className="flex flex-wrap items-center justify-between gap-2"><h2 className="si-section-title">Bảng hàng hóa</h2><select aria-label="Trạng thái bảng hàng hóa" value={draft.itemsState} disabled={confirmed} onChange={event => update({ itemsState: event.target.value as LabelState })} className="si-select">{STATES.map(state => <option key={state} value={state}>{stateLabels[state]}</option>)}</select></div>
          {draft.itemsState === 'PRESENT' && <div className="mt-3 space-y-3">{draft.items.map((row, index) => <div key={row.key} className="rounded-control border border-hairline p-3"><div className="flex items-center justify-between"><span className="si-label">Dòng {index + 1}</span>{!confirmed && <button type="button" aria-label={`Xóa dòng ${index + 1}`} onClick={() => update({ items: draft.items.filter(item => item.key !== row.key) })} className="si-btn-ghost p-1 text-failed"><Trash2 size={16} /></button>}</div><div className="mt-2 grid grid-cols-2 gap-2">{itemColumns.map(column => <label key={column.key} className={`si-field-label block ${column.key === 'productName' ? 'col-span-2' : ''}`}>{column.label}<input value={row.cells[column.key] ?? ''} disabled={confirmed} onChange={event => update({ items: draft.items.map(item => item.key === row.key ? { ...item, cells: { ...item.cells, [column.key]: event.target.value } } : item) })} className="si-input mt-1" /></label>)}</div></div>)}{!confirmed && <button type="button" onClick={() => update({ items: [...draft.items, { key: nextRow++, cells: {} }] })} className="si-btn-secondary"><Plus size={16} className="mr-1 inline" />Thêm dòng</button>}</div>}
        </section>
        <section className="si-card p-4"><div className="flex flex-wrap items-center justify-between gap-2"><h2 className="si-section-title">Văn bản chuẩn để đo CER/WER</h2>{!confirmed && <button type="button" onClick={() => void startFromOcr()} className="si-btn-secondary !px-2 !py-1 text-xs">Bắt đầu từ văn bản OCR</button>}</div><p className="mt-1 text-xs text-ink-muted">Không bắt buộc. Để trống thì mẫu này không đo CER/WER.{sample.ocrAssisted ? ' Mẫu này đã dùng văn bản OCR làm điểm bắt đầu nên được ghi nhận riêng.' : ''}</p><textarea value={draft.ocrText} disabled={confirmed} onChange={event => update({ ocrText: event.target.value })} rows={6} className="si-input mt-2" aria-label="Văn bản chuẩn" /></section>
        {reconciliation && !dirty && <section className="si-card p-4"><h2 className="si-section-title">Kết quả đối soát chuẩn</h2><p className="mt-1 text-xs text-ink-muted">Tính từ nhãn đã lưu. Đối chiếu với chứng từ và sửa nếu cần.</p>{Object.entries(reconciliation).map(([check, status]) => <div key={check} className="mt-2 flex flex-wrap items-center justify-between gap-2 text-sm"><span>{reconciliationRuleLabel(check)}</span><select aria-label={`Kết quả ${check}`} value={status} disabled={confirmed} onChange={event => setReconciliation({ ...reconciliation, [check]: event.target.value })} className="si-select">{RECONCILIATION_STATUSES.map(option => <option key={option} value={option}>{reconciliationStatusLabel(option)}</option>)}</select></div>)}</section>}
        {error && <p className="rounded-control border border-failed/25 bg-failed-surface p-3 text-sm text-failed">{error}</p>}
        {!confirmed ? <>
          {missing.length > 0 && <p className="text-xs text-review">Còn thiếu: {missing.join(', ')}.</p>}
          {dirty && <p className="text-xs text-ink-muted">Lưu nháp để tính kết quả đối soát trước khi xác nhận.</p>}
          <div className="flex justify-end gap-2"><button type="button" disabled={isBusy || !dirty} onClick={() => void save()} className="si-btn-secondary">{isBusy ? 'Đang lưu...' : 'Lưu nháp'}</button><button type="button" disabled={isBusy || dirty || missing.length > 0 || workspace.stale} onClick={() => void confirm()} className="si-btn-primary">Xác nhận nhãn</button></div>
        </> : <>
          {reveal ? <section className="si-card p-4"><h2 className="si-section-title">So sánh với kết quả AI</h2><p className="mt-1 text-xs text-ink-muted">Model: {reveal.model ?? 'Chưa ghi nhận'} · Loại AI đề xuất: {reveal.documentType ?? '—'} · Nhóm AI đề xuất: {reveal.category ?? '—'}</p><div className="mt-2 overflow-x-auto"><table className="si-table"><thead><tr><th>Trường</th><th>Nhãn chuẩn</th><th>AI</th><th>Kết quả</th></tr></thead><tbody>{reveal.fields.map(item => <tr key={item.field}><td>{fieldLabels[item.field] ?? item.field}</td><td>{item.truth ?? '—'}</td><td>{item.predicted ?? '—'}</td><td>{outcomeLabels[item.outcome]}</td></tr>)}</tbody></table></div></section> : <p className="text-sm text-review">Không còn đọc được kết quả AI của lần xử lý này.</p>}
          <div className="flex justify-end"><button type="button" disabled={isBusy} onClick={reopen} className="si-btn-secondary">Mở lại để sửa</button></div>
        </>}
      </div>
    </div>
  </div>
}

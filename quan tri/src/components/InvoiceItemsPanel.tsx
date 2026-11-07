import { useEffect, useMemo, useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import invoiceService, { type InvoiceResponse } from '../services/invoiceService'
import { getErrorMessage } from '../services/authService'
import EmptyState from './ui/EmptyState'
import { invoiceQuantity } from '../utils/invoiceNumber'
import { emptyItemDraft, itemDraftHints, itemDraftsChanged, itemDraftTotals, toItemDrafts, toItemPayloads, totalDiffers, type InvoiceItemDraft, type InvoiceItemPayload } from '../utils/invoiceItemEditor'

const money = (value: number | null | undefined) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(value)} đ`
const rate = (value: number | null) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(Math.abs(value) <= 1 ? value * 100 : value)}%`
const valueOrDash = (value: string | null | undefined) => value || '—'

type DraftField = Exclude<keyof InvoiceItemDraft, 'key'>
const numberFields: { field: DraftField; label: string }[] = [
  { field: 'quantity', label: 'Số lượng' }, { field: 'unitPrice', label: 'Đơn giá' }, { field: 'taxRate', label: 'Thuế suất (%)' },
  { field: 'taxAmount', label: 'Tiền thuế' }, { field: 'amount', label: 'Thành tiền' },
]

export default function InvoiceItemsPanel({ invoice, canEdit, needsReconciliation, onSaved, onDirtyChange, onOpenReconciliation }: {
  invoice: InvoiceResponse | null; canEdit: boolean; needsReconciliation: boolean
  onSaved: (invoice: InvoiceResponse) => void; onDirtyChange: (dirty: boolean) => void; onOpenReconciliation: () => void
}) {
  const items = useMemo(() => invoice?.items ?? [], [invoice])
  const [drafts, setDrafts] = useState<InvoiceItemDraft[] | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  const dirty = canEdit && drafts !== null && itemDraftsChanged(drafts, items)
  useEffect(() => { onDirtyChange(dirty); return () => onDirtyChange(false) }, [dirty, onDirtyChange])

  if (!invoice) return <EmptyState title="Chưa có dữ liệu hàng hóa" />

  const update = (key: number, field: DraftField, value: string) => setDrafts(current => current && current.map(draft => draft.key === key ? { ...draft, [field]: value } : draft))
  const save = async () => {
    if (!drafts) return
    setError('')
    let payloads: InvoiceItemPayload[]
    try { payloads = toItemPayloads(drafts) } catch (invalid) { setError((invalid as Error).message); return }
    setIsSaving(true)
    try { onSaved(await invoiceService.updateItems(invoice.id, payloads)); setDrafts(null); setSaved(true) }
    catch (failure) { setError(getErrorMessage(failure, 'Không thể lưu bảng hàng hóa. Dữ liệu đang nhập vẫn được giữ nguyên.')) }
    finally { setIsSaving(false) }
  }
  const cancel = () => { setDrafts(null); setError('') }

  if (!canEdit || drafts === null) return <div className="space-y-3">
    {canEdit && <div className="flex justify-end"><button type="button" onClick={() => { setDrafts(toItemDrafts(items)); setSaved(false) }} className="si-btn-secondary">Sửa hàng hóa</button></div>}
    {saved && needsReconciliation && <div className="flex flex-wrap items-center justify-between gap-2 rounded-control border border-review/25 bg-review-surface p-3 text-sm text-review"><span>Đã lưu bảng hàng hóa. Các tổng trên hóa đơn không tự thay đổi; cần chạy lại đối soát.</span><button type="button" onClick={onOpenReconciliation} className="si-btn-secondary text-xs">Mở Đối soát</button></div>}
    {items.length ? items.map((item, index) => <article key={item.id ?? index} className="si-card p-4"><div className="flex gap-3"><span className="text-sm text-ink-muted">{index + 1}.</span><p className="min-w-0 break-words text-sm font-medium">{valueOrDash(item.productName)}</p></div><dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-3 text-sm"><div><dt className="text-xs text-ink-muted">Số lượng</dt><dd>{invoiceQuantity(item.quantity)} {item.unit || ''}</dd></div><div><dt className="text-xs text-ink-muted">Đơn giá</dt><dd className="si-num break-words">{money(item.unitPrice)}</dd></div><div><dt className="text-xs text-ink-muted">Thuế suất</dt><dd>{rate(item.taxRate)}</dd></div><div><dt className="text-xs text-ink-muted">Tiền thuế</dt><dd className="si-num break-words">{money(item.taxAmount)}</dd></div><div><dt className="text-xs text-ink-muted">Thành tiền</dt><dd className="si-num break-words font-semibold">{money(item.amount)}</dd></div></dl></article>) : <EmptyState title="Chưa có dữ liệu hàng hóa" />}
  </div>

  const totals = itemDraftTotals(drafts)
  return <div className="space-y-3">
    {drafts.map((draft, index) => {
      const hints = itemDraftHints(draft)
      return <article key={draft.key} className="si-card p-4">
        <div className="flex items-center justify-between gap-3"><p className="si-label">Dòng {index + 1}</p><button type="button" aria-label={`Xóa dòng ${index + 1}`} onClick={() => setDrafts(drafts.filter(row => row.key !== draft.key))} className="si-btn-ghost p-1 text-failed"><Trash2 size={16} /></button></div>
        <div className="mt-3 grid grid-cols-2 gap-3">
          <label className="si-field-label col-span-2 block">Tên hàng hóa / dịch vụ<input value={draft.productName} onChange={event => update(draft.key, 'productName', event.target.value)} className="si-input mt-1" /></label>
          <label className="si-field-label block">ĐVT<input value={draft.unit} onChange={event => update(draft.key, 'unit', event.target.value)} className="si-input mt-1" /></label>
          {numberFields.map(({ field, label }) => <label key={field} className="si-field-label block">{label}<input type="text" inputMode="decimal" value={draft[field]} onChange={event => update(draft.key, field, event.target.value)} className="si-input mt-1" /></label>)}
        </div>
        {hints.map(hint => <p key={hint} className="mt-2 text-xs text-review">{hint}</p>)}
      </article>
    })}
    {!drafts.length && <EmptyState title="Bảng hàng hóa đang trống" description="Lưu lúc này sẽ xóa toàn bộ dòng hàng của hóa đơn." />}
    <button type="button" onClick={() => setDrafts([...drafts, emptyItemDraft()])} className="si-btn-secondary"><Plus size={16} className="mr-1 inline" />Thêm dòng</button>
    <section className="si-card p-4 text-sm"><p>Tổng thành tiền các dòng: <span className="si-num font-semibold">{money(totals.amount)}</span> · Tổng trước thuế trên hóa đơn: <span className="si-num">{money(invoice.subtotal)}</span></p><p className="mt-1">Tổng tiền thuế các dòng: <span className="si-num font-semibold">{money(totals.taxAmount)}</span> · Tiền thuế trên hóa đơn: <span className="si-num">{money(invoice.vatAmount)}</span></p>{(totalDiffers(totals.amount, invoice.subtotal) || totalDiffers(totals.taxAmount, invoice.vatAmount)) && <p className="mt-2 text-xs text-review">Tổng các dòng đang khác tổng trên hóa đơn. Hệ thống không tự sửa các tổng này.</p>}</section>
    {error && <p className="rounded-control border border-failed/25 bg-failed-surface p-3 text-sm text-failed">{error}</p>}
    <div className="flex justify-end gap-2"><button type="button" disabled={isSaving} onClick={cancel} className="si-btn-secondary">Hủy</button><button type="button" disabled={isSaving || !dirty} onClick={() => void save()} className="si-btn-primary">{isSaving ? 'Đang lưu...' : 'Lưu hàng hóa'}</button></div>
  </div>
}

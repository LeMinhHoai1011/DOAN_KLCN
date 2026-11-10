import { useEffect, useState } from 'react'
import { Crosshair, MapPinOff } from 'lucide-react'
import type { ExtractedFieldResponse } from '../../services/documentService'
import { bestLocation, fieldDisplay, fieldGroup, isManualLocation } from './ocrPreviewUtils'
import { regionValueForField, type PageRect } from './regionSelect'

/** Rectangle the accountant just drew for the field being edited, with the OCR text inside it. */
export type FieldRegionPick = { fieldId: number; rect: PageRect; text: string; loading: boolean; error: string }

const percent = (value: number | null | undefined) =>
  typeof value === 'number' ? `${Math.round(value * (value <= 1 ? 100 : 1))}%` : null

/**
 * AI fields of a document. Clicking a card selects it (the preview scrolls to and highlights its location)
 * and, when it can be corrected, opens the editor in the card. Closing the editor clears the selection.
 */
export default function ExtractedFieldPanel({ fields, documentType, selectedId, hoveredId, canCorrect, onSelect, onHover, onCorrect,
  pageCount, onClose, onPickRegion, regionFieldId, regionPick, onSaveLocation }: {
  fields: ExtractedFieldResponse[]; documentType?: string | null; selectedId?: number; hoveredId?: number
  canCorrect: boolean
  onSelect: (field: ExtractedFieldResponse) => void; onHover: (field: ExtractedFieldResponse | null) => void
  onCorrect: (fieldId: number, fieldValue: string, region?: { region: PageRect; extractedText: string }) => Promise<void>
  /** Pages of the document, to reject locations on pages that do not exist. */
  pageCount?: number
  /** Editor closed: the page clears the selection and highlight. */
  onClose?: () => void
  /** Starts drawing a rectangle on the preview for this field (only offered when the preview has an OCR layout). */
  onPickRegion?: (field: ExtractedFieldResponse) => void
  /** Field currently in drawing mode. */
  regionFieldId?: number | null
  regionPick?: FieldRegionPick | null
  /** Saves only the drawn location (value unchanged). */
  onSaveLocation?: (fieldId: number, rect: PageRect, extractedText: string, value: string) => Promise<void>
}) {
  const [editingFieldId, setEditingFieldId] = useState<number | null>(null)
  const [draftValue, setDraftValue] = useState('')
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState('')
  const isVat = documentType?.toUpperCase().replace(/[- ]/g, '_') === 'VAT_INVOICE'
  const grouped = fields.reduce<Map<string, ExtractedFieldResponse[]>>((result, field) => {
    const group = fieldGroup(field.fieldName, isVat)
    result.set(group, [...(result.get(group) ?? []), field]); return result
  }, new Map())
  const order = isVat
    ? ['Thông tin hóa đơn', 'Người bán', 'Người mua', 'Thanh toán', 'Thông tin hóa đơn điện tử', 'Thông tin bổ sung']
    : ['Thông tin chính', 'Thông tin bổ sung']

  // The page cleared the selection (another card, reload, ...): close a stale editor.
  useEffect(() => {
    if (editingFieldId != null && selectedId !== editingFieldId) { setEditingFieldId(null); setSaveError('') }
  }, [editingFieldId, selectedId])

  const close = () => { setEditingFieldId(null); setSaveError(''); onClose?.() }
  const open = (field: ExtractedFieldResponse, editable: boolean) => {
    if (editingFieldId === field.id) return
    if (selectedId === field.id && !editable) { onClose?.(); return }
    onSelect(field)
    if (editable) { setEditingFieldId(field.id); setDraftValue(field.fieldValue ?? ''); setSaveError('') }
  }

  return <aside className="min-h-0 overflow-y-auto border-hairline border-t bg-surface lg:border-l lg:border-t-0">
    <div className="sticky top-0 z-10 border-hairline bg-surface/95 border-b px-5 py-4 backdrop-blur">
      <h3 className="si-label">Dữ liệu trích xuất</h3>
      <p className="mt-1.5 text-sm leading-6 text-ink-muted">Bấm vào một trường để xem vị trí trên chứng từ; đúng thì đóng, sai thì sửa ngay trong thẻ.</p>
    </div>
    <div className="space-y-5 p-4">
      {fields.length === 0 && <p className="rounded-card bg-canvas p-4 text-[18px] text-ink-muted">Chưa có dữ liệu trích xuất.</p>}
      {order.map(group => (grouped.get(group)?.length ?? 0) > 0 && <section key={group}>
        <h4 className="si-label mb-2 px-1">{group}</h4>
        <div className="space-y-2">
          {grouped.get(group)?.map(field => {
            const location = bestLocation(field, pageCount)
            const editable = canCorrect && field.source?.toUpperCase() === 'AI'
            const editing = editingFieldId === field.id
            const active = selectedId === field.id || hoveredId === field.id
            const { label, unresolved } = fieldDisplay(field)
            const empty = !field.fieldValue?.trim()
            const pick = editing && regionPick?.fieldId === field.id ? regionPick : null
            const changed = draftValue.trim() !== (field.fieldValue ?? '').trim()
            const drawing = regionFieldId === field.id
            return <article key={field.id}
              onMouseEnter={() => onHover(field)} onMouseLeave={() => onHover(null)}
              className={`rounded-card border px-4 py-3 transition ${active ? 'border-review bg-review-surface' : 'border-hairline/60 hover:border-hairline-strong hover:bg-subtle'}`}>
              <button type="button" onClick={() => open(field, editable)} onFocus={() => onHover(field)} onBlur={() => onHover(null)}
                aria-expanded={editable ? editing : undefined}
                className="flex w-full items-start gap-3 text-left">
                <span className="min-w-0 flex-1">
                  <span className="text-2xs block font-semibold text-ink-soft" title={unresolved ? `Mã trường: ${field.fieldName}` : field.fieldName}>
                    {label}{unresolved && <span className="ml-1 font-normal text-ink-muted">(chưa khôi phục được tên gốc)</span>}
                  </span>
                  {!editing && <span className={`mt-1 block text-sm leading-6 break-words whitespace-pre-wrap ${empty ? 'italic text-ink-muted' : 'text-ink font-semibold'}`}>
                    {empty ? 'Trống' : field.fieldValue}
                  </span>}
                </span>
                {location ? <Crosshair size={22} className="text-brand-700 mt-2 shrink-0" aria-label="Có vị trí trên chứng từ" />
                  : <MapPinOff size={20} className="text-ink-muted mt-2 shrink-0" aria-label="Chưa xác định được vị trí" />}
              </button>
              <div className="mt-1.5 flex flex-wrap gap-1.5 text-2xs font-semibold">
                {field.manuallyCorrected
                  ? <span className="rounded-control bg-review-surface px-1.5 py-0.5 text-review">Đã sửa tay</span>
                  : field.source?.toUpperCase() === 'AI' && <span className="rounded-control bg-canvas px-1.5 py-0.5 text-ink-soft">AI gốc</span>}
                {location && <span className="rounded-control bg-canvas px-1.5 py-0.5 text-ink-soft">
                  {isManualLocation(location) ? 'Vị trí chọn tay' : `Vị trí AI${percent(location.matchConfidence) ? ` · khớp ${percent(location.matchConfidence)}` : ''}`}
                </span>}
              </div>
              {field.manuallyCorrected && field.originalAiValue != null && field.originalAiValue !== field.fieldValue &&
                <p className="mt-1 text-2xs text-ink-muted break-words">Giá trị AI: {field.originalAiValue || '(trống)'}</p>}
              {editing && <div className="mt-3 space-y-2" onKeyDown={event => { if (event.key === 'Escape' && !drawing) close() }}>
                <textarea autoFocus rows={Math.min(4, Math.max(1, Math.ceil(draftValue.length / 48)))} value={draftValue}
                  onChange={event => setDraftValue(event.target.value)}
                  aria-label={`Giá trị cho ${label}`} placeholder="Nhập giá trị đúng" className="si-input resize-y" />
                <p className="text-2xs text-ink-muted">
                  {pick ? 'Đang tô sáng vùng vừa chọn (chưa lưu).' : location ? (isManualLocation(location) ? 'Đang tô sáng vị trí đã chọn tay.' : 'Đang tô sáng vị trí AI tìm thấy. Nếu sai chỗ, hãy chọn lại vùng.')
                    : 'Chưa có vị trí trên chứng từ. Có thể sửa trực tiếp hoặc chọn vùng để gán vị trí.'}
                </p>
                {pick && <div className="rounded-control border border-brand-200 bg-brand-50/60 p-2 text-xs">
                  <p className="font-semibold text-ink-soft">Vùng mới{pick.loading ? ' (đang đọc chữ…)' : ''}</p>
                  <p className="mt-1 whitespace-pre-wrap break-words text-ink">{pick.text || 'Không có chữ OCR trong vùng này.'}</p>
                  {pick.error && <p className="mt-1 text-review">{pick.error}</p>}
                  {pick.text && <button type="button" className="si-link mt-1"
                    onClick={() => setDraftValue(regionValueForField(field.fieldName, pick.text))}>Dùng chữ này</button>}
                </div>}
                <div className="flex flex-wrap items-center gap-2">
                  <button type="button" disabled={saving || !draftValue.trim() || (!changed && !pick)}
                    onClick={async () => {
                      setSaving(true); setSaveError('')
                      try {
                        if (changed) await onCorrect(field.id, draftValue.trim(), pick ? { region: pick.rect, extractedText: pick.text } : undefined)
                        else if (pick && onSaveLocation) await onSaveLocation(field.id, pick.rect, pick.text, draftValue.trim())
                        close()
                      } catch (error) {
                        setSaveError(error instanceof Error ? error.message : 'Không thể lưu thay đổi.')
                      } finally {
                        setSaving(false)
                      }
                    }}
                    className="si-btn-primary text-sm">
                    {saving ? 'Đang lưu...' : changed ? 'Lưu' : 'Lưu vị trí'}
                  </button>
                  <button type="button" disabled={saving} onClick={close} className="si-btn-secondary text-sm">Đóng</button>
                  {onPickRegion && <button type="button" disabled={saving} onClick={() => onPickRegion(field)} aria-pressed={drawing}
                    className="si-link">{drawing ? 'Đang chọn vùng… (Esc để hủy)' : location || pick ? 'Chọn lại vùng' : 'Chọn vùng'}</button>}
                </div>
                {!draftValue.trim() && <p className="text-2xs text-review">Giá trị không được để trống.</p>}
                {saveError && <p role="alert" className="text-failed text-sm font-semibold">{saveError}</p>}
              </div>}
            </article>
          })}
        </div>
      </section>)}
    </div>
  </aside>
}

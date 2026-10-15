import { useState } from 'react'
import { Crosshair, MapPinOff } from 'lucide-react'
import type { ExtractedFieldResponse } from '../../services/documentService'
import { bestLocation, fieldGroup, fieldLabel } from './ocrPreviewUtils'

const invoiceCoreFields = new Set([
  'invoiceNumber', 'invoiceSeries', 'invoiceDate', 'sellerName', 'sellerTaxCode', 'sellerAddress',
  'sellerPhone', 'buyerName', 'buyerTaxCode', 'buyerAddress', 'subtotal', 'vatAmount', 'taxAmount',
  'totalAmount', 'paymentMethod', 'amountInWords', 'taxAuthorityCode', 'signDate', 'items',
])

export default function ExtractedFieldPanel({ fields, documentType, selectedId, hoveredId, canCorrect, onSelect, onHover, onCorrect }: {
  fields: ExtractedFieldResponse[]; documentType?: string | null; selectedId?: number; hoveredId?: number
  canCorrect: boolean
  onSelect: (field: ExtractedFieldResponse) => void; onHover: (field: ExtractedFieldResponse | null) => void
  onCorrect: (fieldId: number, fieldValue: string) => Promise<void>
}) {
  const [editingFieldId, setEditingFieldId] = useState<number | null>(null)
  const [draftValue, setDraftValue] = useState('')
  const [savingFieldId, setSavingFieldId] = useState<number | null>(null)
  const [saveError, setSaveError] = useState('')
  const isVat = documentType?.toUpperCase().replace(/[- ]/g, '_') === 'VAT_INVOICE'
  const visible = fields.filter(field => field.fieldValue?.trim() && !invoiceCoreFields.has(field.fieldName))
  const grouped = visible.reduce<Map<string, ExtractedFieldResponse[]>>((result, field) => {
    const group = fieldGroup(field.fieldName, isVat)
    result.set(group, [...(result.get(group) ?? []), field]); return result
  }, new Map())
  const order = isVat
    ? ['Thông tin hóa đơn', 'Người bán', 'Người mua', 'Thanh toán', 'Thông tin hóa đơn điện tử', 'Thông tin bổ sung']
    : ['Thông tin chính', 'Thông tin bổ sung']

  return <aside className="min-h-0 overflow-y-auto border-hairline border-t bg-surface lg:border-l lg:border-t-0">
    <div className="sticky top-0 z-10 border-hairline bg-surface/95 border-b px-5 py-4 backdrop-blur">
      <h3 className="si-label">Dữ liệu trích xuất</h3>
      <p className="mt-1.5 text-[17px] leading-[26px] text-ink-muted">Chọn một trường để xem vị trí trên chứng từ.</p>
    </div>
    <div className="space-y-5 p-4">
      {visible.length === 0 && <p className="rounded-card bg-canvas p-4 text-[18px] text-ink-muted">Chưa có dữ liệu trích xuất.</p>}
      {order.map(group => (grouped.get(group)?.length ?? 0) > 0 && <section key={group}>
        <h4 className="si-label mb-2 px-1">{group}</h4>
        <div className="space-y-2">
          {grouped.get(group)?.map(field => {
            const location = bestLocation(field)
            const active = selectedId === field.id || hoveredId === field.id
            const editing = editingFieldId === field.id
            const fieldCanBeCorrected = canCorrect && field.source?.toUpperCase() === 'AI'
            return <article key={field.id}
              onMouseEnter={() => onHover(field)} onMouseLeave={() => onHover(null)}
              className={`rounded-card border px-4 py-3 transition ${active ? 'border-review bg-review-surface' : 'border-hairline/60 hover:border-hairline-strong hover:bg-subtle'}`}>
              <button type="button" onClick={() => onSelect(field)} onFocus={() => onHover(field)} onBlur={() => onHover(null)}
                className="flex w-full items-start gap-3 text-left">
                <span className="min-w-0 flex-1">
                  <span className="text-2xs block font-semibold text-ink-soft">{fieldLabel(field.fieldName)}</span>
                  {!editing && <span className="text-ink mt-1 block text-[19px] leading-[28px] font-semibold break-words whitespace-pre-wrap">{field.fieldValue}</span>}
                  {!location && <span className="text-ink-muted mt-1 text-2xs block">Chưa xác định được vị trí</span>}
                </span>
                {location ? <Crosshair size={22} className="text-brand-700 mt-2 shrink-0" aria-label="Xem vị trí trên chứng từ" />
                  : <MapPinOff size={20} className="text-ink-muted mt-2 shrink-0" aria-label="Chưa xác định được vị trí" />}
              </button>
              {field.manuallyCorrected && <p className="text-review mt-1.5 text-2xs font-semibold">Đã chỉnh sửa thủ công</p>}
              {editing ? <div className="mt-3 space-y-2">
                <input autoFocus value={draftValue} onChange={event => setDraftValue(event.target.value)}
                  aria-label={`Giá trị mới cho ${fieldLabel(field.fieldName)}`}
                  className="si-input" />
                <div className="flex gap-2">
                  <button type="button" disabled={savingFieldId === field.id || !draftValue.trim()}
                    onClick={async () => {
                      setSavingFieldId(field.id); setSaveError('')
                      try {
                        await onCorrect(field.id, draftValue)
                        setEditingFieldId(null)
                      } catch (error) {
                        setSaveError(error instanceof Error ? error.message : 'Không thể lưu thay đổi.')
                      } finally {
                        setSavingFieldId(null)
                      }
                    }}
                    className="si-btn-primary text-[17px]">
                    {savingFieldId === field.id ? 'Đang lưu...' : 'Lưu'}
                  </button>
                  <button type="button" disabled={savingFieldId === field.id}
                    onClick={() => { setEditingFieldId(null); setSaveError('') }}
                    className="si-btn-secondary text-[17px]">
                    Hủy
                  </button>
                </div>
                {saveError && <p role="alert" className="text-failed text-[17px] font-semibold">{saveError}</p>}
              </div> : fieldCanBeCorrected && <button type="button"
                onClick={() => { onSelect(field); setDraftValue(field.fieldValue ?? ''); setEditingFieldId(field.id); setSaveError('') }}
                className="si-link mt-2">
                Chỉnh sửa thủ công
              </button>}
            </article>
          })}
        </div>
      </section>)}
    </div>
  </aside>
}

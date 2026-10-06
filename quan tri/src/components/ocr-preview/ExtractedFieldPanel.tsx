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

  return <aside className="min-h-0 overflow-y-auto border-t border-slate-200 bg-white lg:border-l lg:border-t-0">
    <div className="sticky top-0 z-10 border-b border-slate-100 bg-white/95 px-4 py-3 backdrop-blur">
      <h3 className="text-sm font-semibold uppercase tracking-wider text-slate-700">Dữ liệu trích xuất</h3>
      <p className="mt-1 text-xs text-slate-500">Chọn một trường để xem vị trí trên chứng từ.</p>
    </div>
    <div className="space-y-5 p-3 sm:p-4">
      {visible.length === 0 && <p className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">Chưa có dữ liệu trích xuất.</p>}
      {order.map(group => (grouped.get(group)?.length ?? 0) > 0 && <section key={group}>
        <h4 className="mb-2 px-1 text-xs font-semibold uppercase tracking-wide text-slate-400">{group}</h4>
        <div className="space-y-1.5">
          {grouped.get(group)?.map(field => {
            const location = bestLocation(field)
            const active = selectedId === field.id || hoveredId === field.id
            const editing = editingFieldId === field.id
            const fieldCanBeCorrected = canCorrect && field.source?.toUpperCase() === 'AI'
            return <article key={field.id}
              onMouseEnter={() => onHover(field)} onMouseLeave={() => onHover(null)}
              className={`rounded-xl border px-3 py-2.5 transition ${active ? 'border-amber-300 bg-amber-50' : 'border-transparent hover:border-slate-200 hover:bg-slate-50'}`}>
              <button type="button" onClick={() => onSelect(field)} onFocus={() => onHover(field)} onBlur={() => onHover(null)}
                className="flex w-full items-start gap-3 text-left">
                <span className="min-w-0 flex-1">
                  <span className="block text-xs font-medium text-slate-500">{fieldLabel(field.fieldName)}</span>
                  {!editing && <span className="mt-0.5 block whitespace-pre-wrap break-words text-sm font-medium text-slate-800">{field.fieldValue}</span>}
                  {!location && <span className="mt-1 block text-[11px] text-slate-400">Chưa xác định được vị trí</span>}
                </span>
                {location ? <Crosshair size={17} className="mt-2 shrink-0 text-blue-600" aria-label="Xem vị trí trên chứng từ" />
                  : <MapPinOff size={16} className="mt-2 shrink-0 text-slate-300" aria-label="Chưa xác định được vị trí" />}
              </button>
              {field.manuallyCorrected && <p className="mt-1 text-[11px] font-medium text-amber-700">Đã chỉnh sửa thủ công</p>}
              {editing ? <div className="mt-2 space-y-2">
                <input autoFocus value={draftValue} onChange={event => setDraftValue(event.target.value)}
                  aria-label={`Giá trị mới cho ${fieldLabel(field.fieldName)}`}
                  className="w-full rounded-lg border border-slate-300 bg-white px-2.5 py-2 text-sm text-slate-800 focus:border-blue-500 focus:outline-none" />
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
                    className="rounded-md bg-blue-600 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-60">
                    {savingFieldId === field.id ? 'Đang lưu...' : 'Lưu'}
                  </button>
                  <button type="button" disabled={savingFieldId === field.id}
                    onClick={() => { setEditingFieldId(null); setSaveError('') }}
                    className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 disabled:opacity-60">
                    Hủy
                  </button>
                </div>
                {saveError && <p role="alert" className="text-xs text-red-600">{saveError}</p>}
              </div> : fieldCanBeCorrected && <button type="button"
                onClick={() => { onSelect(field); setDraftValue(field.fieldValue ?? ''); setEditingFieldId(field.id); setSaveError('') }}
                className="mt-2 text-xs font-medium text-blue-700 hover:text-blue-900">
                Chỉnh sửa thủ công
              </button>}
            </article>
          })}
        </div>
      </section>)}
    </div>
  </aside>
}

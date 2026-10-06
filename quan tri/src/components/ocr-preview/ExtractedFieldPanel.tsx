import { Crosshair, MapPinOff } from 'lucide-react'
import type { ExtractedFieldResponse } from '../../services/documentService'
import { bestLocation, fieldGroup, fieldLabel } from './ocrPreviewUtils'

export default function ExtractedFieldPanel({ fields, documentType, selectedId, hoveredId, onSelect, onHover }: {
  fields: ExtractedFieldResponse[]; documentType?: string | null; selectedId?: number; hoveredId?: number
  onSelect: (field: ExtractedFieldResponse) => void; onHover: (field: ExtractedFieldResponse | null) => void
}) {
  const isVat = documentType?.toUpperCase().replace(/[- ]/g, '_') === 'VAT_INVOICE'
  const visible = fields.filter(field => field.fieldValue?.trim())
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
            return <button key={field.id} type="button" onClick={() => onSelect(field)}
              onMouseEnter={() => onHover(field)} onMouseLeave={() => onHover(null)} onFocus={() => onHover(field)} onBlur={() => onHover(null)}
              className={`flex w-full items-start gap-3 rounded-xl border px-3 py-2.5 text-left transition ${active ? 'border-amber-300 bg-amber-50' : 'border-transparent hover:border-slate-200 hover:bg-slate-50'}`}>
              <span className="min-w-0 flex-1">
                <span className="block text-xs font-medium text-slate-500">{fieldLabel(field.fieldName)}</span>
                <span className="mt-0.5 block whitespace-pre-wrap break-words text-sm font-medium text-slate-800">{field.fieldValue}</span>
                {!location && <span className="mt-1 block text-[11px] text-slate-400">Chưa xác định được vị trí</span>}
              </span>
              {location ? <Crosshair size={17} className="mt-2 shrink-0 text-blue-600" aria-label="Xem vị trí trên chứng từ" />
                : <MapPinOff size={16} className="mt-2 shrink-0 text-slate-300" aria-label="Chưa xác định được vị trí" />}
            </button>
          })}
        </div>
      </section>)}
    </div>
  </aside>
}

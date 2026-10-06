import { ChevronLeft, ChevronRight, Maximize2, Minus, Plus, RotateCcw } from 'lucide-react'

export default function OcrPreviewToolbar({ page, pageCount, zoom, onPage, onZoom, onFitWidth, onFitPage, onReset }: {
  page: number; pageCount: number; zoom: number
  onPage: (page: number) => void; onZoom: (zoom: number) => void
  onFitWidth: () => void; onFitPage: () => void; onReset: () => void
}) {
  const iconClass = 'rounded-md p-1.5 text-slate-600 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40'
  return <header className="flex min-h-14 flex-wrap items-center justify-between gap-2 border-b border-slate-200 bg-white px-3 py-2 sm:px-4">
    <h2 className="font-semibold text-slate-800">Xem chứng từ</h2>
    <div className="flex flex-wrap items-center justify-end gap-1">
      <button type="button" className={iconClass} disabled={page <= 1} onClick={() => onPage(page - 1)} title="Trang trước" aria-label="Trang trước"><ChevronLeft size={18} /></button>
      <span className="min-w-14 text-center text-xs font-medium text-slate-600">{page} / {Math.max(1, pageCount)}</span>
      <button type="button" className={iconClass} disabled={page >= pageCount} onClick={() => onPage(page + 1)} title="Trang sau" aria-label="Trang sau"><ChevronRight size={18} /></button>
      <span className="mx-1 h-5 w-px bg-slate-200" />
      <button type="button" className={iconClass} onClick={() => onZoom(zoom - .25)} disabled={zoom <= .5} title="Thu nhỏ" aria-label="Thu nhỏ"><Minus size={18} /></button>
      <span className="min-w-12 text-center text-xs font-medium text-slate-600">{Math.round(zoom * 100)}%</span>
      <button type="button" className={iconClass} onClick={() => onZoom(zoom + .25)} disabled={zoom >= 3} title="Phóng to" aria-label="Phóng to"><Plus size={18} /></button>
      <button type="button" className="ml-1 rounded-md border border-slate-200 px-2 py-1.5 text-xs text-slate-600 hover:bg-slate-50" onClick={onFitWidth} title="Vừa chiều rộng" aria-label="Vừa chiều rộng">Vừa chiều rộng</button>
      <button type="button" className={iconClass} onClick={onFitPage} title="Vừa trang" aria-label="Vừa trang"><Maximize2 size={17} /></button>
      <button type="button" className={iconClass} onClick={onReset} title="Đặt lại khung xem" aria-label="Đặt lại khung xem"><RotateCcw size={17} /></button>
    </div>
  </header>
}


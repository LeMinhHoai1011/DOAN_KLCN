import { ChevronLeft, ChevronRight, Maximize2, Minus, Plus, RotateCcw } from 'lucide-react'

export default function OcrPreviewToolbar({ page, pageCount, zoom, onPage, onZoom, onFitWidth, onFitPage, onReset }: {
  page: number; pageCount: number; zoom: number
  onPage: (page: number) => void; onZoom: (zoom: number) => void
  onFitWidth: () => void; onFitPage: () => void; onReset: () => void
}) {
  const iconClass = 'si-btn-ghost p-2.5'
  return <header className="flex border-hairline bg-surface min-h-16 flex-wrap items-center justify-between gap-3 border-b px-4 py-3 sm:px-5">
    <h2 className="si-section-title">Xem chứng từ</h2>
    <div className="flex flex-wrap items-center justify-end gap-1.5">
      <button type="button" className={iconClass} disabled={page <= 1} onClick={() => onPage(page - 1)} title="Trang trước" aria-label="Trang trước"><ChevronLeft size={24} /></button>
      <span className="min-w-20 si-num text-center text-[18px] font-semibold text-ink-soft">{page} / {Math.max(1, pageCount)}</span>
      <button type="button" className={iconClass} disabled={page >= pageCount} onClick={() => onPage(page + 1)} title="Trang sau" aria-label="Trang sau"><ChevronRight size={24} /></button>
      <span className="bg-hairline-strong mx-1.5 h-7 w-px" />
      <button type="button" className={iconClass} onClick={() => onZoom(zoom - .25)} disabled={zoom <= .5} title="Thu nhỏ" aria-label="Thu nhỏ"><Minus size={24} /></button>
      <span className="min-w-20 si-num text-center text-[18px] font-semibold text-ink-soft">{Math.round(zoom * 100)}%</span>
      <button type="button" className={iconClass} onClick={() => onZoom(zoom + .25)} disabled={zoom >= 3} title="Phóng to" aria-label="Phóng to"><Plus size={24} /></button>
      <button type="button" className="ml-1.5 si-btn-secondary text-[17px]" onClick={onFitWidth} title="Vừa chiều rộng" aria-label="Vừa chiều rộng">Vừa chiều rộng</button>
      <button type="button" className={iconClass} onClick={onFitPage} title="Vừa trang" aria-label="Vừa trang"><Maximize2 size={22} /></button>
      <button type="button" className={iconClass} onClick={onReset} title="Đặt lại khung xem" aria-label="Đặt lại khung xem"><RotateCcw size={22} /></button>
    </div>
  </header>
}


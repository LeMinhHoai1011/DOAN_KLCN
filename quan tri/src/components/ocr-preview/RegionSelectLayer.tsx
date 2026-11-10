import { useEffect, useRef, useState } from 'react'
import type { PointerEvent as ReactPointerEvent } from 'react'
import { scaleBoundingBox } from './ocrPreviewUtils'
import { displayToPageRect, isUsableRegion, normalizeDragRect, type DisplayRect, type PageRect, type Point } from './regionSelect'

/**
 * Transparent layer over the rendered page: the accountant drags a rectangle, which is converted
 * back to original page pixels (any zoom / fit mode) before it leaves this component.
 */
export default function RegionSelectLayer({ page, pageWidth, pageHeight, renderedWidth, renderedHeight, selected, onSelect, onCancel }: {
  page: number; pageWidth: number; pageHeight: number; renderedWidth: number; renderedHeight: number
  selected?: PageRect | null
  onSelect: (rect: PageRect) => void; onCancel: () => void
}) {
  const layerRef = useRef<HTMLDivElement>(null)
  const [start, setStart] = useState<Point | null>(null)
  const [draft, setDraft] = useState<DisplayRect | null>(null)
  const [tooSmall, setTooSmall] = useState(false)

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') { setStart(null); setDraft(null); onCancel() } }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onCancel])

  const point = (event: ReactPointerEvent<HTMLDivElement>): Point => {
    const bounds = layerRef.current!.getBoundingClientRect()
    // Bounds already include CSS zoom/scroll; scale to the layout size used by the page renderer.
    const scaleX = renderedWidth / Math.max(1, bounds.width), scaleY = renderedHeight / Math.max(1, bounds.height)
    return { x: (event.clientX - bounds.left) * scaleX, y: (event.clientY - bounds.top) * scaleY }
  }

  const shown = draft ?? (selected && selected.page === page
    ? scaleBoundingBox(selected, pageWidth, pageHeight, renderedWidth, renderedHeight) : null)

  return <div ref={layerRef} role="application" aria-label="Kéo để chọn vùng trên chứng từ"
    className="absolute inset-0 z-10 cursor-crosshair touch-none select-none"
    onPointerDown={event => {
      if (event.button !== 0) return
      event.preventDefault(); event.currentTarget.setPointerCapture(event.pointerId)
      const origin = point(event); setStart(origin); setTooSmall(false)
      setDraft({ left: origin.x, top: origin.y, width: 0, height: 0 })
    }}
    onPointerMove={event => { if (start) setDraft(normalizeDragRect(start, point(event), renderedWidth, renderedHeight)) }}
    onPointerUp={event => {
      if (!start) return
      const display = normalizeDragRect(start, point(event), renderedWidth, renderedHeight)
      setStart(null); setDraft(null)
      const rect = displayToPageRect(display, page, pageWidth, pageHeight, renderedWidth, renderedHeight)
      if (isUsableRegion(rect)) onSelect(rect); else setTooSmall(true)
    }}
    onPointerCancel={() => { setStart(null); setDraft(null) }}>
    <div className="absolute inset-0 bg-ink/5" />
    {shown && <span className="absolute rounded-sm border-2 border-brand-600 bg-brand-500/15 shadow-[0_0_0_9999px_rgba(15,23,42,.18)]" style={shown} />}
    {tooSmall && <span className="absolute left-2 top-2 rounded-control bg-review-surface px-2 py-1 text-xs font-semibold text-review">Vùng quá nhỏ, hãy kéo lại.</span>}
  </div>
}

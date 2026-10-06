import { memo, useEffect, useRef } from 'react'
import { scaleBoundingBox } from './ocrPreviewUtils'

export type OverlayBox = {
  key: string; text: string; confidence?: number
  x: number; y: number; width: number; height: number; pageWidth: number; pageHeight: number
  active?: boolean
}

function OcrOverlayLayer({ boxes, renderedWidth, renderedHeight, focusKey }: {
  boxes: OverlayBox[]; renderedWidth: number; renderedHeight: number; focusKey?: string
}) {
  const focusRef = useRef<HTMLSpanElement>(null)
  useEffect(() => {
    if (focusKey) focusRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'center' })
  }, [focusKey, renderedWidth, renderedHeight])

  return <div className="pointer-events-none absolute inset-0" aria-hidden="true">
    {boxes.map((box) => {
      const scaled = scaleBoundingBox(box, box.pageWidth, box.pageHeight, renderedWidth, renderedHeight)
      const confidence = typeof box.confidence === 'number' ? ` · Độ khớp ${Math.round(box.confidence * (box.confidence <= 1 ? 100 : 1))}%` : ''
      return <span key={box.key} ref={box.key === focusKey ? focusRef : undefined} title={`${box.text}${confidence}`}
        className={box.active ? 'absolute rounded-sm border-2 border-amber-500 bg-amber-300/30 shadow-[0_0_0_2px_rgba(255,255,255,.8)]' : 'absolute rounded-sm border border-cyan-400/80 bg-cyan-300/15'}
        style={scaled} />
    })}
  </div>
}

export default memo(OcrOverlayLayer)


import { useEffect, useMemo, useRef, useState } from 'react'
import { GlobalWorkerOptions, getDocument } from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import documentService, { type ExtractedFieldResponse, type OCRResultResponse } from '../services/documentService'

GlobalWorkerOptions.workerSrc = workerUrl

type Word = { text: string; x: number; y: number; width: number; height: number }
type Page = { pageNumber?: number; page?: number; imageWidth?: number; imageHeight?: number; width?: number; height?: number; words: Word[] }
type DisplayBox = Word & { text: string }

const key = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-zA-Z0-9]/g, '').toLowerCase()

const locate = (page: Page, value: string) => {
  const target = key(value)
  if (!target) return []
  for (let start = 0; start < page.words.length; start += 1) {
    let combined = ''
    for (let end = start; end < Math.min(page.words.length, start + 12); end += 1) {
      combined += key(page.words[end].text)
      if (combined === target) return page.words.slice(start, end + 1)
      if (!target.startsWith(combined)) break
    }
  }
  return []
}

function PdfPagePreview({ url, pageNumber, boxes }: { url: string; pageNumber: number; boxes: DisplayBox[] }) {
  const hostRef = useRef<HTMLDivElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [hostWidth, setHostWidth] = useState(800)
  const [zoom, setZoom] = useState(1)

  useEffect(() => {
    if (!hostRef.current) return
    const observer = new ResizeObserver(entries => setHostWidth(Math.max(320, entries[0].contentRect.width)))
    observer.observe(hostRef.current)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    let cancelled = false
    const loadingTask = getDocument({ url })
    void (async () => {
      const pdf = await loadingTask.promise
      const pdfPage = await pdf.getPage(Math.min(Math.max(1, pageNumber), pdf.numPages))
      const natural = pdfPage.getViewport({ scale: 1 })
      const viewport = pdfPage.getViewport({ scale: (hostWidth / natural.width) * zoom })
      const canvas = canvasRef.current
      if (!canvas || cancelled) return
      canvas.width = Math.floor(viewport.width)
      canvas.height = Math.floor(viewport.height)
      const context = canvas.getContext('2d')
      if (context) await pdfPage.render({ canvas, canvasContext: context, viewport }).promise
    })().catch(() => undefined)
    return () => { cancelled = true; void loadingTask.destroy() }
  }, [url, pageNumber, hostWidth, zoom])

  return <div className="w-full">
    <div className="mb-2 flex justify-center gap-2">
      <button type="button" onClick={() => setZoom(value => Math.max(.5, value - .25))} className="rounded bg-slate-700 px-2 py-1 text-xs text-white">−</button>
      <span className="px-2 py-1 text-xs text-slate-200">Trang {pageNumber} · {Math.round(zoom * 100)}%</span>
      <button type="button" onClick={() => setZoom(value => Math.min(3, value + .25))} className="rounded bg-slate-700 px-2 py-1 text-xs text-white">+</button>
    </div>
    <div ref={hostRef} className="w-full overflow-auto">
      <div className="relative mx-auto w-fit">
        <canvas ref={canvasRef} className="block max-w-none bg-white" />
        {boxes.map((box, index) => <span key={index} title={box.text} className="pointer-events-none absolute border-2 border-amber-400 bg-amber-300/25"
          style={{ left: `${box.x}%`, top: `${box.y}%`, width: `${box.width}%`, height: `${box.height}%` }} />)}
      </div>
    </div>
  </div>
}

export default function OcrDocumentPreview({ documentId, fileType, ocr, fields }: {
  documentId: number; fileType: string; ocr: OCRResultResponse | null; fields: ExtractedFieldResponse[]
}) {
  const [url, setUrl] = useState('')
  const [selected, setSelected] = useState<ExtractedFieldResponse | null>(null)
  const [activePage, setActivePage] = useState(1)
  useEffect(() => {
    let active = true
    let objectUrl = ''
    void documentService.downloadDocument(documentId, true).then(value => { objectUrl = value; if (active) setUrl(value) })
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [documentId])
  const pages = useMemo<Page[]>(() => {
    if (!ocr?.layoutJson) return []
    try { const parsed = JSON.parse(ocr.layoutJson); return Array.isArray(parsed) ? parsed : parsed.pages || [] } catch { return [] }
  }, [ocr?.layoutJson])
  useEffect(() => {
    const selectedPage = selected?.locations?.[0]?.page
    if (selectedPage) setActivePage(selectedPage)
  }, [selected])
  const page = pages.find(item => (item.page || item.pageNumber || 1) === activePage) || pages[0]
  const boxes = selected && page ? (selected.locations?.filter(location => location.page === (page.page || page.pageNumber || 1))
    .map(location => ({ ...location, text: selected.fieldValue || '' })) || locate(page, selected.fieldValue || '')) : []
  const normalized = (word: Word, axis: 'x' | 'y' | 'width' | 'height') => {
    const value = word[axis]
    if (value <= 1) return value * 100
    const total = axis === 'x' || axis === 'width' ? (page?.imageWidth || page?.width || 1) : (page?.imageHeight || page?.height || 1)
    return value / total * 100
  }
  const displayBoxes = boxes.map(box => ({ ...box, x: normalized(box, 'x'), y: normalized(box, 'y'),
    width: normalized(box, 'width'), height: normalized(box, 'height') }))

  return <div className="flex h-full min-h-[420px] flex-col gap-3">
    <div className="flex flex-wrap gap-2">
      {fields.filter(field => field.fieldValue).map(field => <button key={field.id} type="button"
        onMouseEnter={() => setSelected(field)} onFocus={() => setSelected(field)} onClick={() => setSelected(field)}
        className={`rounded-full px-3 py-1 text-xs ${selected?.id === field.id ? 'bg-amber-400 text-slate-900' : 'bg-slate-700 text-white'}`}>
        {field.fieldName}
      </button>)}
    </div>
    <div className="flex flex-1 items-center justify-center overflow-auto">
      {url && fileType.startsWith('image/') ? <div className="relative inline-block max-w-full">
        <img src={url} alt="Bản xem trước chứng từ" className="block h-auto max-h-[72vh] max-w-full" />
        {displayBoxes.map((box, index) => <span key={index} title={box.text} className="pointer-events-none absolute border-2 border-amber-400 bg-amber-300/25"
          style={{ left: `${box.x}%`, top: `${box.y}%`, width: `${box.width}%`, height: `${box.height}%` }} />)}
      </div> : url && fileType === 'application/pdf' ? <PdfPagePreview url={url} pageNumber={activePage} boxes={displayBoxes} />
        : url ? <iframe title="Bản xem trước chứng từ" src={url} className="h-[70vh] w-full bg-white" />
          : <span className="text-sm text-slate-300">Đang tải bản xem trước...</span>}
    </div>
    {selected && <p className="text-center text-xs text-slate-300">{boxes.length ? `Đang đánh dấu ${selected.fieldName}` : 'Không tìm thấy tọa độ OCR phù hợp; hệ thống không tự tạo tọa độ.'}</p>}
  </div>
}

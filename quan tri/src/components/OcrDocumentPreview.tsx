import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { GlobalWorkerOptions, getDocument } from 'pdfjs-dist'
import type { PDFDocumentProxy, PDFPageProxy, RenderTask } from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import documentService, { type ExtractedFieldResponse, type OCRResultResponse } from '../services/documentService'
import { PreviewLoadError } from '../services/previewBlob'
import ExtractedFieldPanel from './ocr-preview/ExtractedFieldPanel'
import OcrOverlayLayer, { type OverlayBox } from './ocr-preview/OcrOverlayLayer'
import OcrPreviewToolbar from './ocr-preview/OcrPreviewToolbar'
import { bestLocation, pageHeightOf, pageNumberOf, pageWidthOf, parseOcrLayout, type OverlayMode } from './ocr-preview/ocrPreviewUtils'

GlobalWorkerOptions.workerSrc = workerUrl

const clampZoom = (value: number) => Math.min(3, Math.max(.5, value))

const logPdfFailure = (code: 'PDF_LOAD_FAILED' | 'PDF_PAGE_LOAD_FAILED' | 'PDF_RENDER_FAILED', error: unknown) => {
  if (import.meta.env.DEV) console.error(`[document-preview] ${code}`, error)
}

export function PdfSurface({ url, pageNumber, width, onMetadata, onError }: {
  url: string; pageNumber: number; width: number
  onMetadata: (width: number, height: number, pages: number) => void; onError: () => void
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
	const [pdfDocument, setPdfDocument] = useState<PDFDocumentProxy | null>(null)
	const [pdfPage, setPdfPage] = useState<PDFPageProxy | null>(null)

	// Document lifecycle: the loading task and parsed PDF remain stable for this Blob URL.
  useEffect(() => {
    let cancelled = false
	setPdfDocument(null); setPdfPage(null)
	const loadingTask = getDocument({ url })
	void loadingTask.promise.then(document => {
	  if (cancelled) return
	  setPdfDocument(document)
	}).catch(error => { if (!cancelled) { logPdfFailure('PDF_LOAD_FAILED', error); onError() } })
	return () => { cancelled = true; void loadingTask.destroy() }
  }, [onError, url])

	// Page lifecycle: page navigation changes the proxy and its natural dimensions,
	// but zoom/fit/resize do not reload or reparse the PDF document.
	useEffect(() => {
	if (!pdfDocument) return
	let cancelled = false
	setPdfPage(null)
	const selected = Math.min(Math.max(1, pageNumber), pdfDocument.numPages)
	void pdfDocument.getPage(selected).then(page => {
	  if (cancelled) return
	  const natural = page.getViewport({ scale: 1 })
	  onMetadata(natural.width, natural.height, pdfDocument.numPages)
	  setPdfPage(page)
	}).catch(error => { if (!cancelled) { logPdfFailure('PDF_PAGE_LOAD_FAILED', error); onError() } })
	return () => { cancelled = true }
  }, [onError, onMetadata, pageNumber, pdfDocument])

	// Render lifecycle: only the page proxy or display width changes. An obsolete
	// canvas render is cancelled without destroying the cached document/page.
	useEffect(() => {
	if (!pdfPage || width <= 1) return
	const canvas = canvasRef.current
	if (!canvas) return
	const natural = pdfPage.getViewport({ scale: 1 })
	const viewport = pdfPage.getViewport({ scale: width / natural.width })
	const context = canvas.getContext('2d')
	if (!context) { const error = new Error('Canvas 2D context is unavailable'); logPdfFailure('PDF_RENDER_FAILED', error); onError(); return }
	const ratio = window.devicePixelRatio || 1
	canvas.width = Math.max(1, Math.floor(viewport.width * ratio)); canvas.height = Math.max(1, Math.floor(viewport.height * ratio))
	canvas.style.width = `${viewport.width}px`; canvas.style.height = `${viewport.height}px`
	let renderTask: RenderTask | null = pdfPage.render({ canvas, canvasContext: context, viewport,
	  transform: ratio === 1 ? undefined : [ratio, 0, 0, ratio, 0, 0] })
	void renderTask.promise.catch(error => {
	  if (error instanceof Error && error.name === 'RenderingCancelledException') return
	  logPdfFailure('PDF_RENDER_FAILED', error); onError()
	})
	return () => { renderTask?.cancel(); renderTask = null }
  }, [onError, pdfPage, width])

  return <canvas ref={canvasRef} className="block bg-white shadow-elev2" />
}

export default function OcrDocumentPreview({ documentId, fileType, ocr, fields, documentType, canCorrectFields, onCorrectField }: {
  documentId: number; fileType: string; ocr: OCRResultResponse | null
  fields: ExtractedFieldResponse[]; documentType?: string | null
  canCorrectFields: boolean; onCorrectField: (fieldId: number, fieldValue: string) => Promise<void>
}) {
  const [url, setUrl] = useState('')
	const [previewContentType, setPreviewContentType] = useState('')
  const [loadError, setLoadError] = useState(false)
  const [selected, setSelected] = useState<ExtractedFieldResponse | null>(null)
  const [hovered, setHovered] = useState<ExtractedFieldResponse | null>(null)
  const [activePage, setActivePage] = useState(1)
  const [pageCount, setPageCount] = useState(1)
  const [zoom, setZoom] = useState(1)
  const [fitMode, setFitMode] = useState<'width' | 'page'>('width')
  const [overlayVisible, setOverlayVisible] = useState(true)
  const [overlayMode, setOverlayMode] = useState<OverlayMode>('field')
  const [sourceSize, setSourceSize] = useState({ width: 0, height: 0 })
  const [viewportSize, setViewportSize] = useState({ width: 800, height: 600 })
  const viewportRef = useRef<HTMLDivElement>(null)

  const pages = useMemo(() => parseOcrLayout(ocr?.layoutJson), [ocr?.layoutJson])
  const layoutPage = pages.find(page => pageNumberOf(page) === activePage)

  useEffect(() => {
	let active = true; let objectUrl = ''
	setUrl(''); setPreviewContentType(''); setLoadError(false)
	void documentService.loadDocumentPreview(documentId)
	  .then(preview => {
		objectUrl = URL.createObjectURL(preview.blob)
		if (!active) { URL.revokeObjectURL(objectUrl); objectUrl = ''; return }
		setPreviewContentType(preview.contentType); setUrl(objectUrl)
	  })
	  .catch(error => {
		if (!active) return
		setLoadError(true)
		if (import.meta.env.DEV) console.error('[document-preview]', error instanceof PreviewLoadError
		  ? { code: error.code, status: error.status, message: error.message } : error)
	  })
	return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [documentId])

	useEffect(() => {
	if (!import.meta.env.DEV || !previewContentType || !fileType) return
	const metadataType = fileType.split(';')[0].trim().toLowerCase()
	if (metadataType !== previewContentType) console.warn('[document-preview] MIME metadata mismatch', {
	  documentId, documentFileType: metadataType, responseContentType: previewContentType,
	})
  }, [documentId, fileType, previewContentType])

  useEffect(() => {
    const host = viewportRef.current
    if (!host) return
    const update = () => setViewportSize({ width: Math.max(320, host.clientWidth), height: Math.max(320, host.clientHeight) })
    update(); const observer = new ResizeObserver(update); observer.observe(host)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    setPageCount(current => Math.max(current, pages.length, ...pages.map(pageNumberOf), 1))
	if (pages.length > 0 && !pages.some(page => pageNumberOf(page) === activePage)) setActivePage(pageNumberOf(pages[0]))
  }, [activePage, pages])

  const updatePdfMetadata = useCallback((width: number, height: number, count: number) => {
    setSourceSize(current => current.width === width && current.height === height ? current : { width, height })
    setPageCount(count)
  }, [])
	const markLoadError = useCallback(() => {
	setLoadError(true)
	if (import.meta.env.DEV) console.error('[document-preview]', { code: 'PREVIEW_IMAGE_DECODE_ERROR', contentType: previewContentType })
  }, [previewContentType])

  const baseScale = useMemo(() => {
    if (!sourceSize.width || !sourceSize.height) return 1
    const widthScale = Math.max(.05, (viewportSize.width - 32) / sourceSize.width)
    const pageScale = Math.min(widthScale, Math.max(.05, (viewportSize.height - 32) / sourceSize.height))
    return fitMode === 'width' ? widthScale : pageScale
  }, [fitMode, sourceSize, viewportSize])
  const renderedWidth = Math.max(1, sourceSize.width * baseScale * zoom)
  const renderedHeight = Math.max(1, sourceSize.height * baseScale * zoom)

  const focusField = selected ?? hovered
  const fieldLocation = bestLocation(focusField)
  useEffect(() => {
    if (selected && fieldLocation) setActivePage(fieldLocation.page)
  }, [fieldLocation, selected])

  const boxes = useMemo<OverlayBox[]>(() => {
	if (!overlayVisible) return []
    if (overlayMode === 'field') {
      if (!focusField || !fieldLocation || fieldLocation.page !== activePage) return []
      return [{ key: `field-${focusField.id}-${fieldLocation.page}`, text: `${focusField.fieldName}: ${focusField.fieldValue ?? ''}`,
        confidence: fieldLocation.matchConfidence, ...fieldLocation, active: Boolean(selected) }]
    }
	if (!layoutPage) return []
	const pageWidth = pageWidthOf(layoutPage), pageHeight = pageHeightOf(layoutPage)
    const elements = overlayMode === 'word' ? layoutPage.words : overlayMode === 'line' ? layoutPage.lines : layoutPage.blocks
    return (elements ?? []).map((element, index) => ({ key: element.id ?? `${overlayMode}-${index}`, text: element.text,
      confidence: element.confidence, x: element.x, y: element.y, width: element.width, height: element.height,
      pageWidth, pageHeight, active: false }))
  }, [activePage, fieldLocation, focusField, layoutPage, overlayMode, overlayVisible, selected])

  const selectField = useCallback((field: ExtractedFieldResponse) => {
    setSelected(current => current?.id === field.id ? null : field)
    const location = bestLocation(field)
    if (location) setActivePage(location.page)
  }, [])
  const resetView = useCallback(() => {
    setFitMode('width'); setZoom(1); viewportRef.current?.scrollTo({ top: 0, left: 0, behavior: 'smooth' })
  }, [])

  return <section className="si-card flex h-full min-h-[620px] flex-col overflow-hidden">
    <OcrPreviewToolbar page={activePage} pageCount={pageCount} zoom={zoom} onPage={setActivePage}
      onZoom={value => setZoom(clampZoom(value))} onFitWidth={() => { setFitMode('width'); setZoom(1) }}
      onFitPage={() => { setFitMode('page'); setZoom(1) }} onReset={resetView} />
    <div className="grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-[minmax(0,2fr)_minmax(320px,1fr)]">
      <div ref={viewportRef} className="border-hairline bg-subtle relative min-h-[420px] overflow-auto border-r p-5"
        onWheel={event => { if (!event.ctrlKey) return; event.preventDefault(); setZoom(value => clampZoom(value + (event.deltaY < 0 ? .1 : -.1))) }}>
        {loadError && <div className="bg-subtle/95 absolute inset-0 z-20 grid place-items-center"><p className="rounded-control bg-failed-surface px-5 py-4 text-[18px] font-semibold text-failed">Không thể tải bản xem trước chứng từ.</p></div>}
        {!url && !loadError && <div className="absolute inset-0 grid place-items-center"><span className="animate-pulse text-[18px] font-medium text-ink-soft">Đang tải bản xem trước...</span></div>}
        {url && !loadError && <div className="mx-auto w-fit" style={{ minHeight: renderedHeight }}>
          <div className="relative" style={{ width: renderedWidth, height: renderedHeight }}>
			{previewContentType.startsWith('image/') ? <img src={url} alt="Bản xem trước chứng từ" draggable={false}
              onLoad={event => setSourceSize({ width: event.currentTarget.naturalWidth, height: event.currentTarget.naturalHeight })}
              onError={markLoadError} className="block bg-white object-fill shadow-elev2" style={{ width: renderedWidth, height: renderedHeight }} />
			  : previewContentType === 'application/pdf' ? <PdfSurface url={url} pageNumber={activePage} width={renderedWidth}
                onMetadata={updatePdfMetadata} onError={markLoadError} />
                : <iframe title="Bản xem trước chứng từ" src={url} className="h-full w-full bg-white" />}
            {overlayVisible && <OcrOverlayLayer boxes={boxes} renderedWidth={renderedWidth} renderedHeight={renderedHeight}
              focusKey={selected && fieldLocation ? `field-${selected.id}-${fieldLocation.page}` : undefined} />}
          </div>
        </div>}
      </div>
      <ExtractedFieldPanel fields={fields} documentType={documentType} selectedId={selected?.id} hoveredId={hovered?.id}
        canCorrect={canCorrectFields} onSelect={selectField} onHover={setHovered} onCorrect={onCorrectField} />
    </div>
    <footer className="flex flex-wrap items-center justify-between gap-4 border-hairline border-t bg-surface px-5 py-3">
      <label className="flex cursor-pointer items-center gap-2.5 text-[18px] font-semibold text-ink-soft">
        <input type="checkbox" checked={overlayVisible} onChange={event => setOverlayVisible(event.target.checked)} className="h-5 w-5 rounded border-hairline-strong" />
        Hiện vùng nhận dạng
      </label>
      <label className="flex items-center gap-2.5 text-[18px] text-ink-soft">Overlay
        <select value={overlayMode} onChange={event => setOverlayMode(event.target.value as OverlayMode)}
          className="si-select w-auto" aria-label="Chế độ overlay">
          <option value="field">Trường dữ liệu</option><option value="line">Dòng OCR</option><option value="word">Từ OCR</option><option value="block">Khối OCR (kiểm tra)</option>
        </select>
      </label>
      {!fields.some(field => (field.locations?.length ?? 0) > 0) && <span className="text-[17px] text-ink-muted">Chưa có dữ liệu vị trí để đánh dấu.</span>}
    </footer>
  </section>
}

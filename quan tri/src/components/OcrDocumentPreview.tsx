import { useEffect, useMemo, useState } from 'react'
import documentService, { type ExtractedFieldResponse, type OCRResultResponse } from '../services/documentService'

type Word = { text: string; x: number; y: number; width: number; height: number }
type Page = { pageNumber?: number; page?: number; imageWidth?: number; imageHeight?: number; width?: number; height?: number; words: Word[] }

const key = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-zA-Z0-9]/g, '').toLowerCase()

const locate = (page: Page, value: string) => {
  const target = key(value)
  if (!target) return []
  const matches: Word[] = []
  for (let start = 0; start < page.words.length; start += 1) {
    let combined = ''
    for (let end = start; end < Math.min(page.words.length, start + 12); end += 1) {
      combined += key(page.words[end].text)
      if (combined === target) { matches.push(...page.words.slice(start, end + 1)); return matches }
      if (!target.startsWith(combined)) break
    }
  }
  return matches
}

export default function OcrDocumentPreview({ documentId, fileType, ocr, fields }: {
  documentId: number; fileType: string; ocr: OCRResultResponse | null; fields: ExtractedFieldResponse[]
}) {
  const [url, setUrl] = useState('')
  const [selected, setSelected] = useState<ExtractedFieldResponse | null>(null)
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
  const page = pages[0]
  const boxes = selected && page ? (selected.locations?.filter(location => location.page === (page.page || page.pageNumber || 1))
    .map(location => ({ ...location, text: selected.fieldValue || '' })) || locate(page, selected.fieldValue || '')) : []
  const normalized = (word: Word, axis: 'x' | 'y' | 'width' | 'height') => {
    const value = word[axis]
    if (value <= 1) return value * 100
    const total = axis === 'x' || axis === 'width' ? (page.imageWidth || page.width || 1) : (page.imageHeight || page.height || 1)
    return value / total * 100
  }
  return <div className="flex h-full min-h-[420px] flex-col gap-3">
    <div className="flex flex-wrap gap-2">
      {fields.filter(field => field.fieldValue).map(field => <button key={field.id} type="button" onClick={() => setSelected(field)}
        className={`rounded-full px-3 py-1 text-xs ${selected?.id === field.id ? 'bg-amber-400 text-slate-900' : 'bg-slate-700 text-white'}`}>
        {field.fieldName}
      </button>)}
    </div>
    <div className="flex flex-1 items-center justify-center overflow-auto">
      {url && fileType.startsWith('image/') ? <div className="relative inline-block max-w-full">
        <img src={url} alt="Bản xem trước chứng từ" className="block h-auto max-h-[72vh] max-w-full" />
        {boxes.map((box, index) => <span key={index} title={box.text} className="pointer-events-none absolute border-2 border-amber-400 bg-amber-300/25"
          style={{ left: `${normalized(box, 'x')}%`, top: `${normalized(box, 'y')}%`, width: `${normalized(box, 'width')}%`, height: `${normalized(box, 'height')}%` }} />)}
      </div> : url ? <iframe title="Bản xem trước chứng từ" src={url} className="h-[70vh] w-full bg-white" />
        : <span className="text-sm text-slate-300">Đang tải bản xem trước...</span>}
    </div>
    {selected && <p className="text-center text-xs text-slate-300">{boxes.length ? `Đang đánh dấu ${selected.fieldName}` : 'Không tìm thấy tọa độ OCR phù hợp; hệ thống không tự tạo tọa độ.'}</p>}
  </div>
}

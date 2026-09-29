import { useEffect, useMemo, useState } from 'react'
import { Download, Eye } from 'lucide-react'
import documentService, { type DocumentListItem } from '../../services/documentService'
import ContentCard from '../../components/ui/ContentCard'
import EmptyState from '../../components/ui/EmptyState'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'

const size = (n: number) => n < 1024 ? `${n} B` : n < 1048576 ? `${(n / 1024).toFixed(1)} KB` : `${(n / 1048576).toFixed(1)} MB`

export default function Storage() {
  const [items, setItems] = useState<DocumentListItem[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [type, setType] = useState('ALL')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')

  useEffect(() => {
    setLoading(true)
    setError('')
    void documentService.getDocumentPage({
      size: 1000,
      sort: 'createdAt,desc',
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
    })
      .then(page => {
        setItems(page.content)
        setTotal(page.totalElements)
      })
      .catch(() => setError('Khong the tai kho luu tru.'))
      .finally(() => setLoading(false))
  }, [dateFrom, dateTo])

  const visible = useMemo(() => items.filter(item =>
    (type === 'ALL' || (type === 'PDF' ? item.fileType === 'application/pdf' : item.fileType.startsWith('image/')))
    && item.fileName.toLowerCase().includes(search.toLowerCase()),
  ), [items, type, search])
  const bytes = items.reduce((sum, item) => sum + item.fileSize, 0)

  const open = async (id: number, preview: boolean) => {
    try {
      const url = await documentService.downloadDocument(id, preview)
      window.open(url, '_blank', 'noopener')
    } catch {
      setError('Khong the mo file.')
    }
  }

  return <div className="space-y-6">
    <PageHeader title="Kho luu tru" description="Danh sach chung tu trong pham vi duoc cap quyen." />
    <div className="grid gap-4 sm:grid-cols-3">
      <ContentCard className="p-4">Tong chung tu: <b>{total}</b></ContentCard>
      <ContentCard className="p-4">Dung luong danh sach hien tai: <b>{size(bytes)}</b></ContentCard>
      <ContentCard className="p-4">PDF: <b>{items.filter(item => item.fileType === 'application/pdf').length}</b> · Anh: <b>{items.filter(item => item.fileType.startsWith('image/')).length}</b></ContentCard>
    </div>
    <ContentCard>
      <div className="flex flex-wrap gap-3 border-b p-4">
        <input value={search} onChange={event => setSearch(event.target.value)} placeholder="Tim ten file" className="rounded border px-3 py-2" />
        <select value={type} onChange={event => setType(event.target.value)} className="rounded border px-3 py-2">
          <option value="ALL">Tat ca file</option>
          <option value="PDF">PDF</option>
          <option value="IMAGE">Anh</option>
        </select>
        <label className="flex items-center gap-2 text-sm">Tu ngay
          <input type="date" value={dateFrom} onChange={event => setDateFrom(event.target.value)} className="rounded border px-3 py-2" />
        </label>
        <label className="flex items-center gap-2 text-sm">Den ngay
          <input type="date" value={dateTo} onChange={event => setDateTo(event.target.value)} className="rounded border px-3 py-2" />
        </label>
      </div>
      {loading ? <LoadingState /> : error ? <ErrorState message={error} /> : visible.length === 0 ? <EmptyState title="Chua co file" /> : <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead><tr className="bg-slate-50 text-left"><th className="p-3">File</th><th>Loai</th><th>Ngay</th><th>Xu ly</th><th /></tr></thead>
          <tbody>{visible.map(item => <tr key={item.id} className="border-t">
            <td className="p-3">{item.fileName}</td><td>{item.fileType}</td><td>{item.date}</td><td>{item.displayStatus}</td>
            <td className="flex gap-2 p-2"><button onClick={() => void open(item.id, true)} title="Preview"><Eye size={18} /></button><button onClick={() => void open(item.id, false)} title="Download"><Download size={18} /></button></td>
          </tr>)}</tbody>
        </table>
      </div>}
    </ContentCard>
  </div>
}

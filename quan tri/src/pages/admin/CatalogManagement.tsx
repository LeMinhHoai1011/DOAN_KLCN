import { useEffect, useState } from 'react'
import { Tags } from 'lucide-react'
import documentService, { type DocumentType } from '../../services/documentService'
import financialTransactionService, { type AccountingCategory } from '../../services/financialTransactionService'
import ContentCard from '../../components/ui/ContentCard'
import EmptyState from '../../components/ui/EmptyState'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'

const CatalogManagement = () => {
  const [documentTypes, setDocumentTypes] = useState<DocumentType[]>([])
  const [categories, setCategories] = useState<AccountingCategory[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const [types, categoryData] = await Promise.all([documentService.getDocumentTypes(), financialTransactionService.getCategories()])
      setDocumentTypes(types)
      setCategories(categoryData)
    } catch {
      setError('Không thể tải danh mục từ hệ thống.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])

  return <div className="space-y-6">
    <PageHeader title="Danh mục chứng từ" description="Danh sách đang sử dụng bởi các API hiện có. Chức năng quản trị thay đổi danh mục chưa được kết nối." />
    {loading ? <LoadingState label="Đang tải danh mục..." /> : error ? <ErrorState message={error} onRetry={() => void load()} /> : <div className="grid gap-6 lg:grid-cols-2">
      <ContentCard>
        <div className="border-b border-slate-200 bg-slate-50 p-5"><h2 className="font-semibold text-slate-800">Loại chứng từ</h2><p className="mt-1 text-sm text-slate-500">Nguồn: GET `/api/v1/documents/types`</p></div>
        {documentTypes.length === 0 ? <EmptyState title="Chưa có loại chứng từ" /> : <ul className="divide-y divide-slate-100">{documentTypes.map((item) => <li key={item.id} className="p-4"><div className="flex items-center gap-3"><Tags size={18} className="text-blue-500" /><div><p className="font-medium text-slate-800">{item.name}</p><p className="text-xs font-medium text-slate-500">{item.code}</p>{item.description && <p className="mt-1 text-sm text-slate-500">{item.description}</p>}</div></div></li>)}</ul>}
      </ContentCard>
      <ContentCard>
        <div className="border-b border-slate-200 bg-slate-50 p-5"><h2 className="font-semibold text-slate-800">Nhóm chi phí kế toán</h2><p className="mt-1 text-sm text-slate-500">Nguồn: GET `/api/v1/accounting-categories`</p></div>
        {categories.length === 0 ? <EmptyState title="Chưa có nhóm chi phí" /> : <ul className="divide-y divide-slate-100">{categories.map((item) => <li key={item.id} className="p-4"><p className="font-medium text-slate-800">{item.categoryName}</p><p className="text-xs font-medium text-slate-500">{item.categoryCode}</p>{item.description && <p className="mt-1 text-sm text-slate-500">{item.description}</p>}</li>)}</ul>}
      </ContentCard>
    </div>}
  </div>
}

export default CatalogManagement

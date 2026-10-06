import { useState } from 'react'
import classificationService, { type ClassificationResponse } from '../../services/classificationService'
import ContentCard from '../../components/ui/ContentCard'
import ErrorState from '../../components/ui/ErrorState'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import { formatConfidence } from '../../utils/confidence'
import documentService, { type DocumentResponse } from '../../services/documentService'

const ClassificationWorkspace = () => {
  const [documentId, setDocumentId] = useState('')
  const [item, setItem] = useState<ClassificationResponse | null>(null)
  const [category, setCategory] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(false)
  const [documentStatus, setDocumentStatus] = useState<DocumentResponse['status'] | null>(null)
  const load = async () => { const id = Number(documentId); if (!Number.isInteger(id) || id < 1) { setError('Nhập mã chứng từ hợp lệ.'); return }; setLoading(true); setError(''); setEditing(false); try { const [data, document] = await Promise.all([classificationService.getClassification(id), documentService.getDocumentById(id)]); setItem(data); setCategory(data.category || ''); setDocumentStatus(document.status) } catch { setItem(null); setDocumentStatus(null); setError('Chưa có hoặc không thể tải phân loại cho chứng từ này.') } finally { setLoading(false) } }
  const save = async (action: 'save' | 'approve' | 'review') => { if (!item) return; setLoading(true); setError(''); try { const updated = action === 'approve' ? await classificationService.approveClassification(item.documentId) : action === 'review' ? await classificationService.reviewClassification(item.documentId) : await classificationService.correctClassification(item.documentId, { category }); setItem(updated); setCategory(updated.category || category); if (action === 'save') setEditing(false) } catch { setError('Không thể cập nhật phân loại.') } finally { setLoading(false) } }
  const canCorrect = documentStatus === 'PROCESSED' || documentStatus === 'NEED_REVIEW'
  return <div className="space-y-6"><PageHeader title="Phân loại chứng từ" description="Tra cứu và điều chỉnh kết quả classification bằng API hiện có." /><ContentCard className="p-5"><div className="flex flex-col gap-3 sm:flex-row"><input value={documentId} onChange={(e) => setDocumentId(e.target.value)} type="number" min="1" placeholder="Mã chứng từ" className="rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-blue-500" /><button onClick={() => void load()} className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white">Tải kết quả</button></div></ContentCard>{loading && <LoadingState />}{error && <ErrorState message={error} />}{item && !loading && <ContentCard className="p-5"><div className="grid gap-4 md:grid-cols-2"><div><p className="text-sm text-slate-500">Chứng từ #{item.documentId}</p><p className="mt-1 text-lg font-semibold text-slate-800">{item.category || 'Chưa phân loại'}</p><p className="mt-1 text-sm text-slate-500">Độ tin cậy phân loại: {formatConfidence(item.confidence)} · Status: {item.status}</p>{!item.aiGenerated && <p className="mt-1 text-xs font-medium text-amber-700">Đã chỉnh sửa thủ công</p>}</div><label className="grid gap-1 text-sm font-medium text-slate-700">Danh mục<input value={category} onChange={(e) => setCategory(e.target.value)} disabled={!editing} className="rounded-lg border border-slate-300 px-3 py-2 disabled:bg-slate-50" /></label></div><div className="mt-5 flex flex-wrap gap-2">{canCorrect && (!editing ? <button onClick={() => setEditing(true)} className="rounded-lg bg-blue-600 px-4 py-2 text-sm text-white">Chỉnh sửa thủ công</button> : <><button onClick={() => void save('save')} className="rounded-lg bg-blue-600 px-4 py-2 text-sm text-white">Lưu thay đổi</button><button onClick={() => { setCategory(item.category || ''); setEditing(false) }} className="rounded-lg border border-slate-300 px-4 py-2 text-sm text-slate-700">Hủy</button></>)}<button onClick={() => void save('review')} className="rounded-lg border border-amber-300 px-4 py-2 text-sm text-amber-700">Đánh dấu cần xem</button><button onClick={() => void save('approve')} className="rounded-lg border border-emerald-300 px-4 py-2 text-sm text-emerald-700">Chấp nhận</button></div></ContentCard>}</div>
}
export default ClassificationWorkspace

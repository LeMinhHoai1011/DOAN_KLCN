import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Eye, Filter, Trash2 } from 'lucide-react';
import documentService from '../../services/documentService';
import type { DocumentListItem, DocumentStatus, DocumentType } from '../../services/documentService';
import { getEffectiveRole } from '../../services/authService';
import StatusBadge from '../../components/StatusBadge';
import PageHeader from '../../components/ui/PageHeader';
import ErrorState from '../../components/ui/ErrorState';
import EmptyState from '../../components/ui/EmptyState';
import LoadingState from '../../components/ui/LoadingState';
import FilterBar from '../../components/ui/FilterBar';

const PROCESSING_STATUSES: DocumentStatus[] = ['UPLOADED', 'PROCESSING', 'PROCESSED', 'NEED_REVIEW', 'COMPLETED', 'FAILED'];
const REVIEW_STATUSES = ['PENDING', 'APPROVED', 'REJECTED', 'CORRECTED'] as const;
const processingLabels: Record<DocumentStatus, string> = { UPLOADED: 'Đã tải lên', PROCESSING: 'Đang xử lý', PROCESSED: 'Đã xử lý', NEED_REVIEW: 'Cần kiểm tra', COMPLETED: 'Hoàn thành', FAILED: 'Lỗi xử lý' };
const reviewLabels: Record<(typeof REVIEW_STATUSES)[number], string> = { PENDING: 'Chờ kiểm tra', APPROVED: 'Đã duyệt', REJECTED: 'Từ chối', CORRECTED: 'Đã điều chỉnh' };

const AccountantDocuments = () => {
  const navigate = useNavigate();
  const role = getEffectiveRole();
  const isEmployeeView = role === 'EMPLOYEE' || role === 'USER';
  const basePath = role === 'ADMIN' ? '/admin' : isEmployeeView ? '/employee' : '/accountant';
  const [documents, setDocuments] = useState<DocumentListItem[]>([]);
  const [documentTypes, setDocumentTypes] = useState<DocumentType[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [showFilters, setShowFilters] = useState(false);
  const [search, setSearch] = useState('');
  const [processingStatus, setProcessingStatus] = useState<DocumentStatus | 'ALL'>('ALL');
  const [reviewStatus, setReviewStatus] = useState<(typeof REVIEW_STATUSES)[number] | 'ALL'>('ALL');
  const [typeId, setTypeId] = useState('ALL');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [sort, setSort] = useState('createdAt,desc');
  const [pageNumber, setPageNumber] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  useEffect(() => { let mounted = true; void documentService.getDocumentTypes().then((types) => { if (mounted) setDocumentTypes(types); }).catch(() => undefined); return () => { mounted = false; }; }, []);
  useEffect(() => {
    let mounted = true;
    setIsLoading(true);
    void documentService.getDocumentPage({ page: pageNumber, size: 10, sort, search: search.trim() || undefined, processingStatus: processingStatus === 'ALL' ? undefined : processingStatus, reviewStatus: reviewStatus === 'ALL' ? undefined : reviewStatus, typeId: typeId === 'ALL' ? undefined : Number(typeId), dateFrom: dateFrom || undefined, dateTo: dateTo || undefined })
      .then((page) => { if (mounted) { setDocuments(page.content); setTotalPages(page.totalPages); setTotalElements(page.totalElements); setError(''); } })
      .catch(() => { if (mounted) setError('Không thể tải danh sách chứng từ'); })
      .finally(() => { if (mounted) setIsLoading(false); });
    return () => { mounted = false; };
  }, [dateFrom, dateTo, pageNumber, processingStatus, reviewStatus, search, sort, typeId]);

  const firstPage = () => setPageNumber(1);
  const resetFilters = () => { setSearch(''); setProcessingStatus('ALL'); setReviewStatus('ALL'); setTypeId('ALL'); setDateFrom(''); setDateTo(''); setSort('createdAt,desc'); firstPage(); };
  const updateSearch = (value: string) => { setSearch(value); firstPage(); };

  return <div className="space-y-6">
    <PageHeader title={isEmployeeView ? 'Chứng từ của tôi' : 'Quản lý chứng từ'} description={isEmployeeView ? 'Theo dõi các chứng từ do bạn tải lên.' : 'Tra cứu các chứng từ đã được số hóa theo phạm vi được cấp quyền.'} actions={<div className="flex gap-2"><button type="button" aria-expanded={showFilters} onClick={() => setShowFilters((open) => !open)} className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-2 text-slate-700 hover:bg-slate-50"><Filter size={18} />Bộ lọc</button>{role !== 'USER' && <button onClick={() => navigate(`${basePath}/upload`)} className="rounded-lg bg-blue-600 px-4 py-2 text-white shadow-sm hover:bg-blue-700">+ Upload chứng từ</button>}</div>} />
    <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="flex items-center justify-between gap-4 border-b border-slate-200 bg-slate-50/50 p-4"><input type="search" placeholder="Tìm theo tên file..." value={search} onChange={(event) => updateSearch(event.target.value)} className="w-full max-w-md rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500" /><span className="whitespace-nowrap text-sm text-slate-500">{isLoading ? 'Đang tải...' : `Tổng số ${totalElements}`}</span></div>
      {showFilters && <div className="border-b border-slate-200 p-4"><FilterBar actions={<button type="button" onClick={resetFilters} className="rounded-lg px-3 py-2 text-sm text-blue-700 hover:bg-blue-50">Xóa bộ lọc</button>}>
        <label className="grid gap-1 text-xs font-medium text-slate-500"><span>Xử lý</span><select value={processingStatus} onChange={(event) => { setProcessingStatus(event.target.value as DocumentStatus | 'ALL'); firstPage(); }} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700"><option value="ALL">Tất cả</option>{PROCESSING_STATUSES.map((status) => <option key={status} value={status}>{processingLabels[status]}</option>)}</select></label>
        <label className="grid gap-1 text-xs font-medium text-slate-500"><span>Kiểm tra</span><select value={reviewStatus} onChange={(event) => { setReviewStatus(event.target.value as typeof reviewStatus); firstPage(); }} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700"><option value="ALL">Tất cả</option>{REVIEW_STATUSES.map((status) => <option key={status} value={status}>{reviewLabels[status]}</option>)}</select></label>
        <label className="grid gap-1 text-xs font-medium text-slate-500"><span>Loại chứng từ</span><select value={typeId} onChange={(event) => { setTypeId(event.target.value); firstPage(); }} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700"><option value="ALL">Tất cả</option>{documentTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label>
        <label className="grid gap-1 text-xs font-medium text-slate-500"><span>Từ ngày</span><input type="date" value={dateFrom} onChange={(event) => { setDateFrom(event.target.value); firstPage(); }} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700" /></label>
        <label className="grid gap-1 text-xs font-medium text-slate-500"><span>Đến ngày</span><input type="date" value={dateTo} onChange={(event) => { setDateTo(event.target.value); firstPage(); }} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700" /></label>
        <label className="grid gap-1 text-xs font-medium text-slate-500"><span>Sắp xếp</span><select value={sort} onChange={(event) => { setSort(event.target.value); firstPage(); }} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700"><option value="createdAt,desc">Mới nhất</option><option value="createdAt,asc">Cũ nhất</option><option value="originalFileName,asc">Tên A–Z</option><option value="originalFileName,desc">Tên Z–A</option></select></label>
      </FilterBar></div>}
      {error ? <div className="p-4"><ErrorState message={error} /></div> : isLoading ? <LoadingState label="Đang tải danh sách chứng từ..." /> : <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr className="border-b border-slate-200 bg-slate-50 text-slate-500"><th className="p-3 font-medium">Mã</th><th className="p-3 font-medium">Tên chứng từ</th><th className="p-3 font-medium">Loại file</th><th className="p-3 font-medium">Ngày</th><th className="p-3 text-right font-medium">Số tiền</th><th className="p-3 font-medium">AI Confidence</th><th className="p-3 font-medium">Trạng thái</th><th className="p-3 text-center font-medium">Thao tác</th></tr></thead><tbody className="divide-y divide-slate-100">{documents.length === 0 ? <tr><td colSpan={8}><EmptyState title="Chưa có chứng từ" description="Thay đổi điều kiện lọc hoặc tải lên chứng từ mới." /></td></tr> : documents.map((doc) => <tr key={doc.id} className="group hover:bg-slate-50"><td className="p-3 font-medium text-blue-600">{doc.id}</td><td className="p-3">{doc.fileName}</td><td className="p-3">{doc.fileType === 'application/pdf' ? 'PDF' : doc.fileType.startsWith('image/') ? doc.fileType.slice(6).toUpperCase() : doc.fileType || '-'}</td><td className="p-3">{doc.date}</td><td className="p-3 text-right">{doc.amount === undefined ? '-' : `${doc.amount.toLocaleString('vi-VN')} đ`}</td><td className="p-3">{doc.aiConfidence === undefined ? '-' : `${doc.aiConfidence}%`}</td><td className="p-3"><StatusBadge status={doc.status} reviewStatus={doc.reviewStatus} /></td><td className="p-3"><div className="flex justify-center gap-2"><button onClick={() => navigate(`${basePath}/documents/${doc.id}`)} className="rounded p-1.5 text-slate-400 hover:bg-blue-50 hover:text-blue-600" title="Xem chi tiết"><Eye size={18} /></button>{!isEmployeeView && <button className="rounded p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-600" title="Xóa"><Trash2 size={18} /></button>}</div></td></tr>)}</tbody></table></div>}
      <div className="flex items-center justify-between border-t border-slate-200 bg-slate-50/50 p-4 text-sm"><button disabled={isLoading || pageNumber <= 1} onClick={() => setPageNumber((page) => page - 1)} className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-slate-600 disabled:opacity-50">Trước</button><span>{pageNumber}/{Math.max(1, totalPages)}</span><button disabled={isLoading || totalPages === 0 || pageNumber >= totalPages} onClick={() => setPageNumber((page) => page + 1)} className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-slate-600 disabled:opacity-50">Tiếp</button></div>
    </div>
  </div>;
};

export default AccountantDocuments;

import {useDateFilter} from '../../hooks/useDateFilter'
import { dateRangeError } from '../../utils/dateTime'
import { formatDate } from '../../utils/dateTime'
import DateInput from '../../components/ui/DateInput'
import { canRetryProcessing } from '../../utils/pipelinePresentation';
import type { PipelineHistory } from "../../services/documentService";
﻿import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import documentService from '../../services/documentService';
import type { DocumentListItem, DocumentStatus, DocumentType } from '../../services/documentService';
import dashboardService, { type DashboardStatistics } from '../../services/dashboardService';
import exportService from '../../services/exportService';
import { getEffectiveRole } from '../../services/authService';
import StatCard from '../../components/StatCard';
import StatusBadge from '../../components/StatusBadge';
import { AlertTriangle, CheckCircle2, Download, Eye, FileText, RotateCcw, Upload, X, XCircle } from 'lucide-react';
import PageHeader from '../../components/ui/PageHeader';
import ErrorState from '../../components/ui/ErrorState';
import EmptyState from '../../components/ui/EmptyState';
import LoadingState from '../../components/ui/LoadingState';
import FilterBar from '../../components/ui/FilterBar';
import ContentCard from '../../components/ui/ContentCard';

const PROCESSING_STATUSES: DocumentStatus[] = ['UPLOADED', 'PROCESSING', 'PROCESSED', 'NEED_REVIEW', 'COMPLETED', 'FAILED'];
const REVIEW_STATUSES = ['PENDING', 'APPROVED', 'REJECTED', 'CORRECTED'] as const;
const PAGE_SIZE = 10;
const processingLabels: Record<DocumentStatus, string> = { UPLOADED: 'Đã tải lên', PROCESSING: 'Đang xử lý', PROCESSED: 'Đã xử lý', NEED_REVIEW: 'Cần kiểm tra', COMPLETED: 'Hoàn thành', FAILED: 'Lỗi xử lý' };
const reviewLabels: Record<(typeof REVIEW_STATUSES)[number], string> = { PENDING: 'Chờ kiểm tra', APPROVED: 'Đã duyệt', REJECTED: 'Từ chối', CORRECTED: 'Đã điều chỉnh' };
const PAGE_SIZE_OPTIONS = [10, 25, 50];
const fileTypeLabel = (fileType: string) => (fileType === 'application/pdf' ? 'PDF' : fileType?.startsWith('image/') ? fileType.slice(6).toUpperCase() : fileType || '—');

const AccountantDocuments = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const role = getEffectiveRole();
  const isEmployeeView = role === 'EMPLOYEE' || role === 'USER';
  const basePath = role === 'ADMIN' ? '/admin' : isEmployeeView ? '/employee' : '/accountant';
  /** `/accountant/review` — menu "Xử lý & kiểm tra" của Stitch: mở sẵn hàng đợi chứng từ cần kiểm tra. */
  const isReviewWorkspace = location.pathname.endsWith('/review');
  const [processingHistory, setProcessingHistory] = useState<Record<number, PipelineHistory>>({});
  const [retryingIds, setRetryingIds] = useState<number[]>([]);
  const [reloadVersion, setReloadVersion] = useState(0);
  const [documents, setDocuments] = useState<DocumentListItem[]>([]);
  const [documentTypes, setDocumentTypes] = useState<DocumentType[]>([]);
  const [statistics, setStatistics] = useState<DashboardStatistics | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [processingStatus, setProcessingStatus] = useState<DocumentStatus | 'ALL'>(isReviewWorkspace ? 'NEED_REVIEW' : 'ALL');
  const [reviewStatus, setReviewStatus] = useState<(typeof REVIEW_STATUSES)[number] | 'ALL'>('ALL');
  const [typeId, setTypeId] = useState('ALL');
  const [dateFrom, setDateFrom] = useDateFilter('AccountantDocuments.from');
  const [dateTo, setDateTo] = useDateFilter('AccountantDocuments.to');
  const [sort, setSort] = useState('createdAt,desc');
  const [pageNumber, setPageNumber] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [exporting, setExporting] = useState(false);

  // The Topbar search / review-bell forwards its intent through router state.
  const incoming = (location.state ?? null) as { documentSearch?: string; processingStatus?: DocumentStatus } | null;
  useEffect(() => {
    if (!incoming) return;
    if (typeof incoming.documentSearch === 'string') setSearch(incoming.documentSearch);
    if (incoming.processingStatus) setProcessingStatus(incoming.processingStatus);
    setPageNumber(1);
    navigate(location.pathname, { replace: true, state: null });
  }, [incoming, location.pathname, navigate]);

  useEffect(() => { let mounted = true; void documentService.getDocumentTypes().then((types) => { if (mounted) setDocumentTypes(types); }).catch(() => undefined); return () => { mounted = false; }; }, []);
  useEffect(() => { let mounted = true; void dashboardService.getDashboardStatistics().then((summary) => { if (mounted) setStatistics(summary); }).catch(() => undefined); return () => { mounted = false; }; }, []);
  useEffect(() => {
    let mounted = true;
    setIsLoading(true);
    void documentService.getDocumentPage({ page: pageNumber, size: pageSize, sort, search: search.trim() || undefined, processingStatus: processingStatus === 'ALL' ? undefined : processingStatus, reviewStatus: reviewStatus === 'ALL' ? undefined : reviewStatus, typeId: typeId === 'ALL' ? undefined : Number(typeId), dateFrom: dateFrom || undefined, dateTo: dateTo || undefined })
      .then(async (page) => {
        if (!mounted) return;
        setDocuments(page.content); setTotalPages(page.totalPages); setTotalElements(page.totalElements); setError(''); setProcessingHistory({});
        const histories = await Promise.allSettled(page.content.filter(doc => doc.status === 'FAILED').map(async doc => ({ id: doc.id, history: await documentService.getProcessingHistory(doc.id) })));
        if (mounted) setProcessingHistory(Object.fromEntries(histories.flatMap(result => result.status === 'fulfilled' ? [[result.value.id, result.value.history]] : [])));
      })
      .catch(() => { if (mounted) setError('Không thể tải danh sách chứng từ'); })
      .finally(() => { if (mounted) setIsLoading(false); });
    return () => { mounted = false; };
  }, [dateFrom, dateTo, pageNumber, pageSize, processingStatus, reviewStatus, search, sort, typeId, reloadVersion]);

  const exportExcel = async () => {
    const rangeError=dateRangeError(dateFrom,dateTo);if(rangeError){setError(rangeError);return;}
    setExporting(true);
    try { await exportService.exportInvoices({ search: search.trim() || undefined, fromDate: dateFrom || undefined, toDate: dateTo || undefined }); }
    catch { setError('Không thể xuất danh sách chứng từ. Vui lòng thử lại.'); }
    finally { setExporting(false); }
  };

  const firstPage = () => setPageNumber(1);
  const resetFilters = () => { setSearch(''); setProcessingStatus('ALL'); setReviewStatus('ALL'); setTypeId('ALL'); setDateFrom(''); setDateTo(''); setSort('createdAt,desc'); firstPage(); };
  const updateSearch = (value: string) => { setSearch(value); firstPage(); };

  const activeFilters = useMemo(() => {
    const chips: { key: string; label: string; clear: () => void }[] = [];
    if (search.trim()) chips.push({ key: 'search', label: `Từ khóa: ${search.trim()}`, clear: () => { setSearch(''); firstPage(); } });
    if (processingStatus !== 'ALL') chips.push({ key: 'processing', label: `Xử lý: ${processingLabels[processingStatus]}`, clear: () => { setProcessingStatus('ALL'); firstPage(); } });
    if (reviewStatus !== 'ALL') chips.push({ key: 'review', label: `Kiểm tra: ${reviewLabels[reviewStatus]}`, clear: () => { setReviewStatus('ALL'); firstPage(); } });
    if (typeId !== 'ALL') chips.push({ key: 'type', label: `Loại: ${documentTypes.find((type) => String(type.id) === typeId)?.name ?? typeId}`, clear: () => { setTypeId('ALL'); firstPage(); } });
    if (dateFrom) chips.push({ key: 'from', label: `Từ ${formatDate(dateFrom)}`, clear: () => { setDateFrom(''); firstPage(); } });
    if (dateTo) chips.push({ key: 'to', label: `Đến ${formatDate(dateTo)}`, clear: () => { setDateTo(''); firstPage(); } });
    return chips;
  }, [dateFrom, dateTo, documentTypes, processingStatus, reviewStatus, search, typeId]);

  const rangeFrom = totalElements === 0 ? 0 : (pageNumber - 1) * pageSize + 1;
  const rangeTo = Math.min(pageNumber * pageSize, totalElements);
  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        eyebrow={isEmployeeView ? 'Nhân viên' : role === 'ADMIN' ? 'Quản trị' : 'Kế toán'}
        title={isReviewWorkspace ? 'Xử lý & kiểm tra chứng từ' : isEmployeeView ? 'Chứng từ của tôi' : 'Quản lý chứng từ & hóa đơn'}
        description={isReviewWorkspace
          ? 'Hàng đợi chứng từ cần kế toán kiểm tra lại trước khi ghi sổ.'
          : isEmployeeView ? 'Theo dõi các chứng từ do bạn tải lên.' : 'Tra cứu, kiểm soát tính hợp lệ OCR và điều phối quy trình duyệt chứng từ kế toán nội bộ.'}
        actions={
          <>
            <button type="button" disabled={exporting} onClick={() => void exportExcel()} className="si-btn-secondary">
              <Download size={20} />{exporting ? 'Đang xuất...' : 'Xuất Excel'}
            </button>
            {role !== 'USER' && (
              <button type="button" onClick={() => navigate(`${basePath}/upload`)} className="si-btn-primary">
                <Upload size={20} />Tải lên chứng từ
              </button>
            )}
          </>
        }
      />

      {statistics && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard title="Tổng chứng từ" value={statistics.totalDocuments} unit="chứng từ" icon={FileText} type="primary" />
          <StatCard title="Hợp lệ & đã duyệt" value={statistics.totalClassified} unit="chứng từ" icon={CheckCircle2} type="success" hint="Sẵn sàng kết chuyển hạch toán" />
          <StatCard title="Cần kiểm tra lại" value={statistics.totalReviewRequired} unit="chứng từ" icon={AlertTriangle} type="warning" hint="Độ khớp AI thấp hoặc lệch số học" />
          <StatCard title="Lỗi / đã từ chối" value={statistics.totalFailed} unit="chứng từ" icon={XCircle} type="error" hint="Hỏng tệp hoặc sai mẫu quy định" />
        </div>
      )}

      <ContentCard>
        <div className="flex flex-col gap-3 border-b border-hairline p-4 sm:flex-row sm:items-center sm:justify-between">
          <input type="search" placeholder="Lọc theo tên tệp, số hóa đơn..." value={search} onChange={(event) => updateSearch(event.target.value)} className="si-input sm:max-w-md" aria-label="Tìm kiếm chứng từ theo tên tệp" />
          <span className="si-num shrink-0 text-[17px] text-ink-soft">{isLoading ? 'Đang tải...' : `${totalElements.toLocaleString('vi-VN')} chứng từ`}</span>
        </div>

        <div className="border-b border-hairline p-4">
          <FilterBar actions={<button type="button" onClick={resetFilters} className="si-btn-ghost"><RotateCcw size={20} />Đặt lại</button>}>
              <label className="grid gap-1"><span className="si-field-label">Xử lý</span><select value={processingStatus} onChange={(event) => { setProcessingStatus(event.target.value as DocumentStatus | 'ALL'); firstPage(); }} className="si-select"><option value="ALL">Tất cả</option>{PROCESSING_STATUSES.map((status) => <option key={status} value={status}>{processingLabels[status]}</option>)}</select></label>
              <label className="grid gap-1"><span className="si-field-label">Kiểm tra</span><select value={reviewStatus} onChange={(event) => { setReviewStatus(event.target.value as typeof reviewStatus); firstPage(); }} className="si-select"><option value="ALL">Tất cả</option>{REVIEW_STATUSES.map((status) => <option key={status} value={status}>{reviewLabels[status]}</option>)}</select></label>
              <label className="grid gap-1"><span className="si-field-label">Loại chứng từ</span><select value={typeId} onChange={(event) => { setTypeId(event.target.value); firstPage(); }} className="si-select"><option value="ALL">Tất cả</option>{documentTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}</select></label>
              <label className="grid gap-1"><span className="si-field-label">Từ ngày</span><DateInput  value={dateFrom} onChange={(event) => { setDateFrom(event.target.value); firstPage(); }} className="si-input" /></label>
              <label className="grid gap-1"><span className="si-field-label">Đến ngày</span><DateInput  value={dateTo} onChange={(event) => { setDateTo(event.target.value); firstPage(); }} className="si-input" /></label>
              <label className="grid gap-1"><span className="si-field-label">Sắp xếp</span><select value={sort} onChange={(event) => { setSort(event.target.value); firstPage(); }} className="si-select"><option value="createdAt,desc">Mới nhất</option><option value="createdAt,asc">Cũ nhất</option><option value="originalFileName,asc">Tên A–Z</option><option value="originalFileName,desc">Tên Z–A</option></select></label>
            </FilterBar>

            {activeFilters.length > 0 && (
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <span className="text-2xs text-ink-muted">Đang lọc:</span>
                {activeFilters.map((chip) => (
                  <span key={chip.key} className="si-chip border-hairline bg-subtle text-ink-soft">
                    {chip.label}
                    <button type="button" onClick={chip.clear} title="Bỏ điều kiện này" className="text-ink-muted hover:text-ink"><X size={20} /></button>
                  </span>
                ))}
                <button type="button" onClick={resetFilters} className="ml-1 text-2xs text-ink-soft underline hover:text-ink">Đặt lại</button>
              </div>
            )}
          </div>
        {error ? (
          <div className="p-4"><ErrorState message={error} /></div>
        ) : isLoading ? (
          <LoadingState label="Đang tải danh sách chứng từ..." />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="si-table">
                <thead>
                  <tr>
                    <th>Mã chứng từ &amp; Tệp</th>
                    <th>Loại CT</th>
                    <th>Ngày nhận</th>
                    <th className="text-right">Tổng tiền (VNĐ)</th>
                    <th>Khớp AI</th>
                    <th>Trạng thái</th>
                    <th className="text-center">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {documents.length === 0 ? (
                    <tr><td colSpan={7}><EmptyState title="Chưa có chứng từ" description="Thay đổi điều kiện lọc hoặc tải lên chứng từ mới." /></td></tr>
                  ) : documents.map((doc) => (
                    <tr key={doc.id} onClick={() => navigate(`${basePath}/documents/${doc.id}`)} className="cursor-pointer">
                      <td>
                        <div className="flex flex-col">
                          <span className="si-num text-[17px] font-semibold text-brand-700">#{doc.id}</span>
                          <span className="max-w-56 truncate text-2xs text-ink-muted" title={doc.fileName}>{doc.fileName}</span>
                        </div>
                      </td>
                      <td>
                        <div className="flex flex-col">
                          <span className="text-ink-soft">{doc.documentTypeName}</span>
                          <span className="text-2xs text-ink-muted">{fileTypeLabel(doc.fileType)}</span>
                        </div>
                      </td>
                      <td className="si-num text-[17px] text-ink-soft">{doc.date}</td>
                      <td className="si-num text-right text-[17px] font-medium">{doc.amount == null ? '—' : `${Number(doc.amount).toLocaleString('vi-VN')} ₫`}</td>
                      <td className="si-num text-[17px] text-ink-soft">{doc.aiConfidence == null ? '—' : `${doc.aiConfidence}%`}</td>
                      <td><StatusBadge status={doc.status} reviewStatus={doc.reviewStatus} />
                        {doc.status === 'FAILED' && <p className="mt-2 max-w-60 text-xs text-review">{processingHistory[doc.id]?.attempts[0]?.failure?.userMessage || 'Chưa tải được nguyên nhân lỗi; mở chi tiết để kiểm tra.'}</p>}
                      </td>
                      <td className="text-center">
                        <button type="button" onClick={(event) => { event.stopPropagation(); navigate(`${basePath}/documents/${doc.id}`); }} title="Xem chi tiết" aria-label={`Xem chi tiết chứng từ ${doc.id}`} className="text-ink-muted hover:bg-subtle hover:text-brand-600 inline-flex items-center justify-center rounded p-1.5 transition-colors">
                          <Eye size={22} />
                        </button>
                        {canRetryProcessing(processingHistory[doc.id], doc.status, doc.reviewStatus) && <button className="si-btn-secondary ml-2" disabled={retryingIds.includes(doc.id)} onClick={event => {
                          event.stopPropagation();
                          if (retryingIds.includes(doc.id)) return;
                          setRetryingIds(ids => [...ids, doc.id]);
                          void documentService.retryProcessing(doc.id).catch(() => setError('Xử lý lại chưa hoàn tất. Vui lòng xem lịch sử lỗi.'))
                            .finally(() => { setRetryingIds(ids => ids.filter(id => id !== doc.id)); setReloadVersion(version => version + 1); });
                        }}>{retryingIds.includes(doc.id) ? 'Đang xử lý...' : 'Thử xử lý lại'}</button>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="flex flex-col items-center justify-between gap-3 border-t border-hairline px-4 py-3 sm:flex-row">
              <span className="si-num text-[17px] text-ink-soft">
                {totalElements === 0 ? 'Không có bản ghi' : `Hiển thị ${rangeFrom.toLocaleString('vi-VN')}–${rangeTo.toLocaleString('vi-VN')} trong số ${totalElements.toLocaleString('vi-VN')} chứng từ`}
              </span>
              <div className="flex flex-wrap items-center gap-3">
                <label className="flex items-center gap-2 text-[17px] text-ink-soft">
                  Mỗi trang
                  <select value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPageNumber(1); }} className="si-select w-auto" aria-label="Số dòng mỗi trang">
                    {PAGE_SIZE_OPTIONS.map((option) => <option key={option} value={option}>{option}</option>)}
                  </select>
                </label>
                <div className="flex items-center gap-2">
                  <button type="button" disabled={isLoading || pageNumber <= 1} onClick={() => setPageNumber((page) => page - 1)} className="si-btn-secondary">Trước</button>
                  <span className="si-num px-1 text-[17px] text-ink-soft">{pageNumber}/{Math.max(1, totalPages)}</span>
                  <button type="button" disabled={isLoading || totalPages === 0 || pageNumber >= totalPages} onClick={() => setPageNumber((page) => page + 1)} className="si-btn-secondary">Tiếp</button>
                </div>
              </div>
            </div>
          </>
        )}
      </ContentCard>
    </div>
  );
};

export default AccountantDocuments;

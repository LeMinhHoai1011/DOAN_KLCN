import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowRight, CheckCircle2, FileType, Info, Loader2, Upload, UploadCloud, X, XCircle } from 'lucide-react';
import documentService from '../../services/documentService';
import type { DocumentUploadResponse } from '../../services/documentService';
import { getCurrentUser, getEffectiveRole, getErrorMessage } from '../../services/authService';
import PageHeader from '../../components/ui/PageHeader';
import ContentCard from '../../components/ui/ContentCard';
import ErrorState from '../../components/ui/ErrorState';

type UploadState = 'idle' | 'uploading' | 'completed' | 'error';

/** Must stay in sync with the `accept` attribute of the file input. */
const ACCEPTED_TYPES = '.pdf,.jpg,.jpeg,.png';
const MAX_FILE_SIZE = 10 * 1024 * 1024;

const formatFileSize = (size: number) => {
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
};

const AccountantUpload = () => {
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [uploadState, setUploadState] = useState<UploadState>('idle');
  const [uploadResult, setUploadResult] = useState<DocumentUploadResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const isEmployeeView = getEffectiveRole(getCurrentUser()) === 'EMPLOYEE';

  const handleFileSelect = (selectedFile: File | undefined) => {
    if (!selectedFile) return;

    setUploadResult(null);
    setErrorMessage('');

    if (selectedFile.size > MAX_FILE_SIZE) {
      setFile(null);
      setUploadState('idle');
      setErrorMessage('Kích thước file không được vượt quá 10MB');
      return;
    }

    setFile(selectedFile);
    setUploadState('idle');
  };

  const handleUpload = async () => {
    if (!file) return;

    setUploadState('uploading');
    setErrorMessage('');

    try {
      const response = await documentService.uploadDocument(file);
      setUploadResult(response);
      setUploadState(response.success ? 'completed' : 'error');
      if (response.error) setErrorMessage(`${response.error.code}: ${response.error.message}`);
    } catch (error: unknown) {
      setUploadState('error');
      setErrorMessage(getErrorMessage(error, 'Upload hoặc xử lý AI thất bại. Vui lòng thử lại.'));
    }
  };

  const handleCancel = () => {
    setFile(null);
    setUploadResult(null);
    setUploadState('idle');
    setErrorMessage('');
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  // Determine base path based on current location to navigate correctly back
  const getBasePath = () => {
    const role = getEffectiveRole(getCurrentUser());
    if (role === 'ADMIN') return '/admin';
    if (role === 'ACCOUNTANT') return '/accountant';
    return '/employee';
  };

  const openFileDialog = () => fileInputRef.current?.click();

  const stateLabel = uploadState === 'uploading'
    ? 'Đang tải lên và phân loại tự động...'
    : uploadState === 'completed'
      ? 'Tải lên và xử lý hoàn tất'
      : uploadState === 'error'
        ? 'Tải lên thất bại'
        : 'Sẵn sàng tải lên';

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        eyebrow={isEmployeeView ? 'Nghiệp vụ / Chứng từ cá nhân' : 'Nghiệp vụ / Xử lý hóa đơn điện tử'}
        title="Tải lên & Số hóa Chứng từ"
        description="Hệ thống tự động tiếp nhận, bóc tách OCR và phân loại chứng từ theo kỳ kế toán nội bộ."
        actions={<span className="si-chip border-hairline text-ink-soft bg-surface"><Info size={20} />PDF · JPG · PNG · tối đa 10 MB</span>}
      />

      <ContentCard className="p-6">
            {!file ? (
              <div
                role="button"
                tabIndex={0}
                aria-label="Chọn tệp chứng từ để tải lên"
                onClick={openFileDialog}
                onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openFileDialog(); } }}
                onDragOver={(event) => event.preventDefault()}
                onDrop={(event) => { event.preventDefault(); handleFileSelect(event.dataTransfer.files[0]); }}
                className="hover:border-brand-600/60 hover:bg-subtle/40 border-hairline-strong flex cursor-pointer flex-col items-center rounded-panel border border-dashed px-6 py-12 text-center transition-colors"
              >
                <span className="border-hairline bg-subtle text-ink-soft flex h-11 w-11 items-center justify-center rounded-md border">
                  <UploadCloud size={22} />
                </span>
                <h3 className="font-sans mt-4 text-[20px] font-bold tracking-tight text-ink">Kéo thả tệp hóa đơn vào đây hoặc duyệt từ máy tính</h3>
                <p className="mt-1.5 max-w-md text-[18px] text-ink-muted">Hỗ trợ định dạng PDF, JPG, PNG. Dung lượng tối đa 10 MB trên mỗi tệp dữ liệu.</p>
                <button type="button" onClick={(event) => { event.stopPropagation(); openFileDialog(); }} className="si-btn-primary mt-5">
                  <Upload size={20} />Chọn tệp từ máy
                </button>
                <input ref={fileInputRef} type="file" accept={ACCEPTED_TYPES} className="hidden" onChange={(event) => handleFileSelect(event.target.files?.[0])} />
              </div>
            ) : (
              <div className="flex flex-col gap-4">
                <div className="border-hairline bg-subtle/60 flex items-center justify-between gap-3 rounded-panel border p-4">
                  <div className="flex min-w-0 items-center gap-3">
                    <span className="border-hairline bg-surface text-ink-soft flex h-9 w-9 shrink-0 items-center justify-center rounded-md border">
                      <FileType size={22} />
                    </span>
                    <div className="flex min-w-0 flex-col">
                      <span className="truncate text-[18px] font-semibold text-ink" title={file.name}>{file.name}</span>
                      <span className="si-num text-2xs text-ink-muted">{formatFileSize(file.size)}</span>
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {uploadState === 'completed' && <CheckCircle2 size={20} className="text-parsed" />}
                    {uploadState === 'error' && <XCircle size={20} className="text-failed" />}
                    {uploadState !== 'uploading' && (
                      <button type="button" onClick={handleCancel} title="Bỏ tệp đã chọn" aria-label="Bỏ tệp đã chọn" className="text-ink-muted hover:bg-subtle hover:text-ink rounded p-1.5 transition-colors">
                        <X size={20} />
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )}
          </ContentCard>

          <ContentCard>
            <div className="flex items-center justify-between gap-3 border-b border-hairline px-5 py-3.5">
              <h2 className="si-section-title">Hàng đợi xử lý chứng từ</h2>
              <span className="si-num text-[17px] text-ink-soft">{file ? '1 tệp' : '0 tệp'}</span>
            </div>
            <div className="overflow-x-auto">
              <table className="si-table">
                <thead>
                  <tr>
                    <th className="w-16">Thứ tự #</th>
                    <th>Tên tệp &amp; Dung lượng</th>
                    <th>Trạng thái xử lý AI</th>
                    <th className="text-center">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {!file ? (
                    <tr><td colSpan={4} className="py-10 text-center text-ink-muted">Chưa có tệp nào trong phiên tải.</td></tr>
                  ) : (
                    <tr>
                      <td className="si-num text-[17px] text-ink-soft">1</td>
                      <td>
                        <div className="flex flex-col">
                          <span className="max-w-72 truncate text-[18px] font-medium text-ink" title={file.name}>{file.name}</span>
                          <span className="si-num text-2xs text-ink-muted">{formatFileSize(file.size)}</span>
                        </div>
                      </td>
                      <td>
                        <div className="flex flex-col gap-1">
                          <span className="inline-flex items-center gap-2 text-[17px] text-ink-soft">
                            {uploadState === 'uploading' && <Loader2 size={20} className="text-extracting shrink-0 animate-spin" />}
                            {uploadState === 'completed' && <CheckCircle2 size={20} className="text-parsed shrink-0" />}
                            {uploadState === 'error' && <XCircle size={20} className="text-failed shrink-0" />}
                            {uploadState === 'idle' && <UploadCloud size={20} className="text-ink-muted shrink-0" />}
                            {stateLabel}
                          </span>
                          {uploadResult?.success && (
                            <>
                              {uploadResult.documentTypeName && (
                                <span className="text-2xs text-ink-muted">Loại chứng từ AI đề xuất: {uploadResult.documentTypeName}</span>
                              )}
                              <span className="si-num text-2xs text-ink-muted">
                                Độ tin cậy phân loại: {uploadResult.documentTypeConfidence == null ? '—' : new Intl.NumberFormat('vi-VN', { style: 'percent', maximumFractionDigits: 1 }).format(uploadResult.documentTypeConfidence)}
                              </span>
                            </>
                          )}
                          {uploadResult?.message && <span className="text-2xs text-ink-muted">{uploadResult.message}</span>}
                        </div>
                      </td>
                      <td className="text-center">
                        <div className="inline-flex items-center gap-2">
                          {uploadResult ? (
                            <button type="button" onClick={() => navigate(`${getBasePath()}/documents/${uploadResult.documentId}`)} className="si-btn-secondary !px-2.5 !py-1 text-2xs">Xem chi tiết</button>
                          ) : (
                            <button type="button" disabled={uploadState === 'uploading'} onClick={() => void handleUpload()} className="si-btn-primary !px-2.5 !py-1 text-2xs">
                              <Upload size={20} />Tải lên
                            </button>
                          )}
                          <button type="button" onClick={handleCancel} className="si-btn-ghost !px-2.5 !py-1 text-2xs">Bỏ qua</button>
                        </div>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <div className="flex flex-col items-center justify-between gap-3 border-t border-hairline px-5 py-3 sm:flex-row">
              <span className="text-2xs text-ink-muted">Chứng từ sau khi tải lên sẽ xuất hiện trong danh sách chứng từ kèm trạng thái xử lý.</span>
              <div className="flex flex-wrap items-center gap-2">
                <button type="button" onClick={() => { handleCancel(); openFileDialog(); }} className="si-btn-secondary"><Upload size={20} />Tiếp tục tải thêm</button>
                <button type="button" onClick={() => navigate(`${getBasePath()}/documents`)} className="si-btn-primary">Chuyển sang Danh sách chứng từ<ArrowRight size={20} /></button>
              </div>
            </div>
          </ContentCard>

          {errorMessage && <ErrorState message={errorMessage} />}
    </div>
  );
};

export default AccountantUpload;

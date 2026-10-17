import type { PipelineHistory } from '../services/documentService';

export const documentPipelinePaths = (id: number) => {
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error('Mã chứng từ không hợp lệ');
  return {
    assessment: `/api/v1/documents/${id}/assessment`,
    history: `/api/v1/documents/${id}/processing-history`,
    retry: `/api/v1/documents/${id}/retry`,
  };
};

export const canRetryProcessing = (history: PipelineHistory | null | undefined, status: string, reviewStatus: string): boolean => {
  const latest = history?.attempts[0];
  return status === 'FAILED' && reviewStatus !== 'APPROVED' && history?.retryAllowed === true
    && latest?.status === 'FAILED' && latest.failure?.retryable === true && latest.attemptNumber < history.maxAttempts;
};

const STAGE_LABELS: Record<string, string> = {
  FILE_VALIDATION: 'Kiểm tra tệp', UPLOAD: 'Tải lên', CLAIM: 'Tiếp nhận xử lý',
  TEXT_EXTRACTION: 'Đọc văn bản PDF', STORAGE: 'Truy cập kho tệp', OCR: 'Nhận dạng văn bản', AI_EXTRACTION: 'AI trích xuất',
  VALIDATION: 'Kiểm tra dữ liệu', PERSISTENCE: 'Lưu kết quả', DATABASE: 'Cơ sở dữ liệu',
};
export const pipelineStageLabel = (stage: string): string => STAGE_LABELS[stage] || stage;

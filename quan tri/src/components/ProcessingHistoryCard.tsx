import { formatDateTime } from '../utils/dateTime'
import { canRetryProcessing, pipelineStageLabel } from '../utils/pipelinePresentation';
import type { PipelineHistory } from '../services/documentService';

export default function ProcessingHistoryCard({ history, error, retrying, onRetry, documentStatus, reviewStatus }: {
  history: PipelineHistory | null; error: string; retrying: boolean; onRetry: () => void; documentStatus: string; reviewStatus: string;
}) {
  const latest = history?.attempts[0];
  return <section className="si-card p-4">
    <div className="flex items-center justify-between gap-3"><h3 className="si-section-title">Lịch sử xử lý OCR/AI</h3>
      {canRetryProcessing(history, documentStatus, reviewStatus) && <button className="si-btn-secondary" disabled={retrying} onClick={onRetry}>{retrying ? 'Đang xử lý...' : 'Thử xử lý lại'}</button>}
    </div>
    {error && <p className="mt-2 text-sm text-review" role="alert">{error}</p>}
    {history && <p className="mt-3 text-sm text-ink-muted">Tổng {history.attemptCount} lần · Thành công {history.succeeded} · Thất bại {history.failed} · Cần kiểm tra {history.needsReview} · Đang chạy {history.running} · Tối đa {history.maxAttempts} lần cho một chuỗi retry</p>}
    {history && Object.keys(history.failuresByCode).length > 0 && <p className="mt-2 text-xs text-ink-muted">Phân loại nguyên nhân: {Object.entries(history.failuresByCode).map(([code, count]) => code + ' × ' + count).join(' · ')}</p>}
    {!latest && !error && <p className="mt-3 text-sm text-ink-muted">Chưa có lịch sử xử lý được ghi nhận theo cơ chế mới.</p>}
    {latest && <p className="mt-2 text-sm">Kết quả gần nhất: {latest.status === 'SUCCESS' ? 'Xử lý hoàn tất, xem đánh giá validation' : latest.status === 'FAILED' ? 'Xử lý thất bại' : latest.status === 'NEED_REVIEW' ? 'Hoàn tất, cần kiểm tra dữ liệu' : 'Đang xử lý'}</p>}
    <div className="mt-3 space-y-3">{history?.attempts.map((attempt) => <div key={`${attempt.jobId}-${attempt.attemptNumber}`} className="rounded-control border border-hairline p-3 text-sm">
      <p className="font-medium">Lượt #{attempt.jobId} · Lần {attempt.attemptNumber} · {attempt.status}</p>
      <p className="mt-1 text-xs text-ink-muted">Bắt đầu: {formatDateTime(attempt.startedAt)}{attempt.completedAt && ` · Kết thúc: ${formatDateTime(attempt.completedAt)}`}</p>
      {attempt.failure && <div className="mt-2 text-review"><p>{attempt.failure.userMessage}</p><p className="mt-1 text-xs">{attempt.failure.errorCode} · Bước: {pipelineStageLabel(attempt.failure.failedStage)} · {formatDateTime(attempt.failure.timestamp)} · Lần thử {attempt.failure.attemptCount}</p></div>}
    </div>)}</div>
  </section>;
}

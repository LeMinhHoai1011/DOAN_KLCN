import { AlertCircle } from 'lucide-react';

interface ErrorStateProps { message: string; onRetry?: () => void; }
const ErrorState = ({ message, onRetry }: ErrorStateProps) => (
  <div className="rounded-card border-failed bg-failed-surface/60 flex min-h-40 flex-col items-center justify-center gap-4 border p-8 text-center" role="alert">
    <AlertCircle className="text-failed" size={36} aria-hidden="true" />
    <p className="text-[19px] leading-[30px] font-semibold text-failed">{message}</p>
    {onRetry && <button type="button" onClick={onRetry} className="si-btn-secondary !text-failed">Thử lại</button>}
  </div>
);
export default ErrorState;

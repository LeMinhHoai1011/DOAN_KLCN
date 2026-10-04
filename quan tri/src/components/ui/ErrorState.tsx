import { AlertCircle } from 'lucide-react';

interface ErrorStateProps { message: string; onRetry?: () => void; }
const ErrorState = ({ message, onRetry }: ErrorStateProps) => (
  <div className="flex min-h-32 flex-col items-center justify-center gap-3 rounded-2xl border border-red-200 bg-red-50 p-6 text-center" role="alert">
    <AlertCircle className="text-red-500" size={26} aria-hidden="true" />
    <p className="text-sm font-medium text-red-700">{message}</p>
    {onRetry && <button type="button" onClick={onRetry} className="rounded-lg bg-white px-3 py-2 text-sm font-medium text-red-700 shadow-sm ring-1 ring-red-200 hover:bg-red-100">Thử lại</button>}
  </div>
);
export default ErrorState;

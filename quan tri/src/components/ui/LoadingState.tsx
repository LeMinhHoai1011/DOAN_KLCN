interface LoadingStateProps { label?: string; }
const LoadingState = ({ label = 'Đang tải dữ liệu...' }: LoadingStateProps) => (
  <div className="flex min-h-32 items-center justify-center gap-3 rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500" role="status">
    <span className="h-5 w-5 animate-spin rounded-full border-2 border-blue-200 border-t-blue-600" aria-hidden="true" />
    {label}
  </div>
);
export default LoadingState;

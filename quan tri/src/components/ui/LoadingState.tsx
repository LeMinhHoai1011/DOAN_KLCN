interface LoadingStateProps { label?: string; }
const LoadingState = ({ label = 'Đang tải dữ liệu...' }: LoadingStateProps) => (
  <div className="rounded-card border-hairline bg-surface flex min-h-40 items-center justify-center gap-4 border p-8 text-[19px] font-medium text-ink-soft" role="status">
    <span className="border-hairline border-t-brand-600 h-7 w-7 animate-spin rounded-full border-[3px]" aria-hidden="true" />
    {label}
  </div>
);
export default LoadingState;

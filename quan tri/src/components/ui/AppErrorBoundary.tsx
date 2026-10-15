import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props { children: ReactNode }
interface State { hasError: boolean }

export default class AppErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(): State { return { hasError: true } }

  componentDidCatch(error: Error, info: ErrorInfo) {
    if (import.meta.env.DEV) console.error('Ứng dụng gặp lỗi khi hiển thị', error, info)
  }

  render() {
    if (!this.state.hasError) return this.props.children
    return <main className="flex min-h-screen items-center justify-center bg-canvas p-8"><div className="w-full max-w-2xl rounded-panel border-failed/40 bg-surface border p-10 text-center shadow-elev1"><h1 className="font-sans text-[28px] leading-[38px] font-bold tracking-tight text-ink">Không thể hiển thị trang</h1><p className="mt-3 text-[19px] leading-[30px] text-ink-soft">Dữ liệu hoặc phiên bản ứng dụng chưa đồng bộ. Hãy tải lại trang sau khi backend đã được khởi động lại.</p><button type="button" onClick={() => window.location.reload()} className="si-btn-primary mt-6 text-[18px]">Tải lại trang</button></div></main>
  }
}

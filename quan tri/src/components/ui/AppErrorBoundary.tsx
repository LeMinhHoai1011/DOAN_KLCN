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
    return <main className="flex min-h-screen items-center justify-center bg-slate-50 p-6"><div className="w-full max-w-lg rounded-2xl border border-red-200 bg-white p-8 text-center shadow-sm"><h1 className="text-xl font-semibold text-slate-800">Không thể hiển thị trang</h1><p className="mt-2 text-sm text-slate-600">Dữ liệu hoặc phiên bản ứng dụng chưa đồng bộ. Hãy tải lại trang sau khi backend đã được khởi động lại.</p><button type="button" onClick={() => window.location.reload()} className="mt-5 rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">Tải lại trang</button></div></main>
  }
}

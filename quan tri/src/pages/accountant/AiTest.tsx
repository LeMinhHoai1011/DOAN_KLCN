import { useRef, useState } from 'react'
import { BrainCircuit, FileImage, Loader2, Send, XCircle } from 'lucide-react'
import aiService from '../../services/aiService'
import type { AiProviderResponse } from '../../services/aiService'
import { getErrorMessage } from '../../services/authService'

const MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024

const formatDuration = (durationMs: number) => `${(durationMs / 1000).toFixed(1)} giây`

const AiTest = () => {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [prompt, setPrompt] = useState('')
  const [result, setResult] = useState<AiProviderResponse | null>(null)
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  const selectFile = (selectedFile: File | undefined) => {
    setError('')
    setResult(null)

    if (!selectedFile) return
    if (!selectedFile.type.startsWith('image/')) {
      setFile(null)
      setError('Chỉ hỗ trợ tệp ảnh để kiểm thử AI.')
      return
    }
    if (selectedFile.size > MAX_IMAGE_SIZE_BYTES) {
      setFile(null)
      setError('Ảnh không được vượt quá 10 MB.')
      return
    }
    setFile(selectedFile)
  }

  const runTest = async () => {
    if (!file) {
      setError('Hãy chọn một ảnh hóa đơn trước khi gửi.')
      return
    }

    setIsLoading(true)
    setError('')
    setResult(null)
    try {
      setResult(await aiService.testImage(file, prompt))
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Không thể gọi dịch vụ AI. Vui lòng thử lại.'))
    } finally {
      setIsLoading(false)
    }
  }

  const clear = () => {
    setFile(null)
    setPrompt('')
    setResult(null)
    setError('')
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Kiểm thử OCR & AI</h1>
        <p className="text-slate-500 mt-1">Gửi một ảnh hóa đơn trực tiếp đến AI provider đang được cấu hình.</p>
      </div>

      <section className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 space-y-5">
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-2">Ảnh hóa đơn</label>
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={isLoading}
            className="w-full border-2 border-dashed border-blue-200 rounded-xl p-8 text-center hover:bg-blue-50 disabled:opacity-60 transition-colors"
          >
            <FileImage className="mx-auto text-blue-500 mb-3" size={34} />
            <span className="block font-medium text-slate-700">{file ? file.name : 'Chọn ảnh JPG, PNG, WEBP...'}</span>
            <span className="block text-sm text-slate-500 mt-1">Tối đa 10 MB</span>
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(event) => selectFile(event.target.files?.[0])}
          />
        </div>

        <div>
          <label htmlFor="ai-prompt" className="block text-sm font-medium text-slate-700 mb-2">Prompt (không bắt buộc)</label>
          <textarea
            id="ai-prompt"
            value={prompt}
            onChange={(event) => setPrompt(event.target.value)}
            disabled={isLoading}
            rows={3}
            placeholder="Để trống để dùng prompt kiểm thử mặc định."
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 disabled:bg-slate-50"
          />
        </div>

        <div className="flex justify-end gap-3">
          <button type="button" onClick={clear} disabled={isLoading} className="px-4 py-2 border border-slate-300 rounded-lg text-slate-700 hover:bg-slate-50 disabled:opacity-60">
            Xóa
          </button>
          <button type="button" onClick={runTest} disabled={isLoading || !file} className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-60 flex items-center gap-2">
            {isLoading ? <Loader2 size={18} className="animate-spin" /> : <Send size={18} />}
            {isLoading ? 'AI đang xử lý...' : 'Gửi kiểm thử'}
          </button>
        </div>
      </section>

      {error && (
        <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 flex gap-2">
          <XCircle size={20} className="shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {result && (
        <section className="bg-white rounded-2xl border border-emerald-200 shadow-sm overflow-hidden">
          <div className="p-5 bg-emerald-50 border-b border-emerald-100 flex items-center gap-3">
            <BrainCircuit className="text-emerald-600" size={24} />
            <div>
              <h2 className="font-semibold text-emerald-900">Kết quả AI</h2>
              <p className="text-sm text-emerald-700">{result.provider} · {result.model} · {formatDuration(result.durationMs)}</p>
            </div>
          </div>
          <div className="p-5 space-y-4">
            <div>
              <h3 className="text-sm font-semibold text-slate-700 mb-2">Nội dung phản hồi</h3>
              <pre className="whitespace-pre-wrap break-words rounded-lg bg-slate-50 p-4 text-sm text-slate-800 font-sans">{result.content}</pre>
            </div>
            <details>
              <summary className="cursor-pointer text-sm font-medium text-slate-600">Xem raw response</summary>
              <pre className="mt-3 max-h-80 overflow-auto whitespace-pre-wrap break-words rounded-lg bg-slate-900 p-4 text-xs text-slate-100">{result.rawResponse}</pre>
            </details>
          </div>
        </section>
      )}
    </div>
  )
}

export default AiTest

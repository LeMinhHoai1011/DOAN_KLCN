import { useEffect, useState } from 'react'
import { CheckCircle2, Loader2, XCircle } from 'lucide-react'
import clsx from 'clsx'
import type { ProcessingProgress } from '../services/documentService'
import { formatSeconds, secondsByStep, stepLabel, stepSeconds } from '../utils/processingProgressPresentation'

interface Props {
  /** File transfer percentage; null once the file is on the server. */
  uploadPercent: number | null
  progress: ProcessingProgress | null
  finished: boolean
  failed: boolean
}

/** Upload bar (real bytes) followed by the real pipeline steps reported by the backend. No estimated percentages. */
export default function ProcessingProgressPanel({ uploadPercent, progress, finished, failed }: Props) {
  const [now, setNow] = useState(() => Date.now())
  const running = !finished && (uploadPercent !== null || progress?.state === 'RUNNING' || progress == null)
  useEffect(() => {
    if (!running) return
    const timer = window.setInterval(() => setNow(Date.now()), 500)
    return () => window.clearInterval(timer)
  }, [running])

  const steps = progress?.steps ?? []
  const totals = finished ? secondsByStep(steps, now) : {}
  const uploaded = uploadPercent === null

  return (
    <div className="flex w-full max-w-2xl flex-col gap-3" aria-live="polite">
      <div className="flex flex-col gap-1">
        <div className="flex items-center justify-between text-2xs text-ink-muted">
          <span>{uploaded ? 'Đã tải file lên máy chủ' : uploadPercent >= 100 ? 'Đang lưu file trên máy chủ…' : 'Đang tải file lên'}</span>
          <span className="si-num shrink-0">{uploaded ? '100%' : `${uploadPercent}%`}</span>
        </div>
        <div className="h-1.5 w-full overflow-hidden rounded bg-hairline" role="progressbar" aria-label="Tiến độ tải file" aria-valuemin={0} aria-valuemax={100} aria-valuenow={uploaded ? 100 : uploadPercent}>
          <div className={clsx('h-1.5 rounded transition-all', uploaded ? 'bg-parsed' : 'bg-brand-600')} style={{ width: `${uploaded ? 100 : uploadPercent}%` }} />
        </div>
      </div>

      {uploaded && (
        <ol className="flex flex-col gap-1">
          {steps.length === 0 && !finished && (
            <li className="inline-flex items-center gap-2 text-2xs text-ink-muted"><Loader2 size={14} className="animate-spin" />Đang chờ bắt đầu xử lý…</li>
          )}
          {steps.map((step, index) => {
            const open = step.endedAt == null && !finished
            const stepFailed = failed && index === steps.length - 1
            return (
              <li key={`${step.code}-${step.startedAt}`} className="flex items-start gap-2 text-2xs">
                {stepFailed ? <XCircle size={14} className="mt-0.5 shrink-0 text-failed" />
                  : open ? <Loader2 size={14} className="mt-0.5 shrink-0 animate-spin text-extracting" />
                    : <CheckCircle2 size={14} className="mt-0.5 shrink-0 text-parsed" />}
                <span className="flex min-w-0 flex-col">
                  <span className={clsx('font-medium', open ? 'text-ink' : 'text-ink-soft')}>
                    {stepLabel(step.code)} <span className="si-num font-normal text-ink-muted">· {formatSeconds(stepSeconds(step, now))}</span>
                  </span>
                  {step.detail && <span className="text-ink-muted">{step.detail}</span>}
                </span>
              </li>
            )
          })}
        </ol>
      )}
      {uploaded && finished && !failed && Object.keys(totals).length > 0 && (
        <div className="flex flex-col gap-1.5 border-t border-hairline pt-2">
          <span className="text-2xs font-medium text-ink-soft">Tổng thời gian theo bước</span>
          <div className="flex flex-wrap gap-1.5">
            {Object.entries(totals).map(([code, seconds]) => (
              <span key={code} className="si-num rounded-control border border-hairline bg-subtle px-2 py-0.5 text-2xs text-ink-soft">{stepLabel(code)} · {formatSeconds(seconds)}</span>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

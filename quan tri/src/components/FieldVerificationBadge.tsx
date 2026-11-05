import { useState } from 'react'
import { AlertTriangle, CheckCircle2, CircleHelp, Crosshair, Pencil, XCircle } from 'lucide-react'
import clsx from 'clsx'
import type { FieldVerificationView } from '../services/fieldVerificationService'
import { ruleOutcomeLabel, verificationStatusLabel } from '../utils/fieldVerificationPresentation'

const tone = {
  VERIFIED: 'border-parsed/30 bg-parsed/10 text-parsed',
  WARNING: 'border-review/40 bg-review-surface text-review',
  UNVERIFIED: 'border-hairline-strong bg-subtle text-ink-soft',
  MISSING: 'border-failed/30 bg-failed/10 text-failed',
} as const
const icon = { VERIFIED: CheckCircle2, WARNING: AlertTriangle, UNVERIFIED: CircleHelp, MISSING: XCircle } as const

/** Status chip next to a field; opens the rules and evidence behind it, with actions to check the image or edit. */
export default function FieldVerificationBadge({ view, onLocate, onEdit }: {
  view: FieldVerificationView | undefined
  onLocate?: (view: FieldVerificationView) => void
  onEdit?: (view: FieldVerificationView) => void
}) {
  const [open, setOpen] = useState(false)
  if (!view) return null
  const Icon = icon[view.status]
  const canLocate = Boolean(view.evidence?.location) && onLocate
  return <span className="relative inline-flex">
    <button type="button" onClick={event => { event.preventDefault(); event.stopPropagation(); setOpen(value => !value) }}
      aria-expanded={open} title={`${verificationStatusLabel[view.status]}${view.score == null ? '' : ` · điểm heuristic ${view.score}`}`}
      className={clsx('inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[13px] font-semibold leading-5', tone[view.status])}>
      <Icon size={13} className="shrink-0" />{verificationStatusLabel[view.status]}
      {view.score != null && <span className="si-num font-normal opacity-80">· {view.score}</span>}
    </button>
    {open && <span role="dialog" onClick={event => event.stopPropagation()}
      className="absolute left-0 top-full z-30 mt-1 block w-[min(22rem,80vw)] rounded-card border border-hairline bg-surface p-3 text-left text-[13px] leading-5 text-ink shadow-elev1">
      <span className="block font-semibold">{verificationStatusLabel[view.status]}{view.valueOrigin === 'MANUAL' ? ' · giá trị do người nhập' : ' · giá trị do AI trích'}</span>
      {view.reasons.length > 0 && <span className="mt-1 block text-review">{view.reasons.join(' · ')}</span>}
      {view.evidence?.matchedText && <span className="mt-1 block text-ink-soft">Bằng chứng OCR: “{view.evidence.matchedText}”</span>}
      <span className="mt-2 block space-y-0.5">
        {view.rules.map((rule, index) => <span key={`${rule.ruleId}-${index}`} className="flex gap-1.5">
          <span className={clsx('shrink-0 font-semibold', rule.outcome === 'PASS' ? 'text-parsed' : rule.outcome === 'FAIL' ? 'text-review' : 'text-ink-muted')}>{ruleOutcomeLabel[rule.outcome]}</span>
          <span className="text-ink-soft">{rule.message}</span>
        </span>)}
      </span>
      {view.score != null && <span className="mt-2 block text-ink-muted">Điểm {view.score}/100 là heuristic chưa hiệu chuẩn, không phải xác suất đúng.</span>}
      <span className="mt-2 flex flex-wrap gap-2">
        {canLocate && <button type="button" onClick={() => { onLocate?.(view); setOpen(false) }} className="si-btn-secondary !px-2 !py-1 text-[13px]"><Crosshair size={14} />Xem trên ảnh gốc</button>}
        {!canLocate && view.status !== 'VERIFIED' && <span className="text-ink-muted">Không định vị được trên OCR — hãy dò trực tiếp ảnh gốc bên trái.</span>}
        {onEdit && view.status !== 'VERIFIED' && <button type="button" onClick={() => { onEdit(view); setOpen(false) }} className="si-btn-secondary !px-2 !py-1 text-[13px]"><Pencil size={14} />Sửa giá trị</button>}
      </span>
    </span>}
  </span>
}

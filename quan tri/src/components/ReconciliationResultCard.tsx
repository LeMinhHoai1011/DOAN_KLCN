import type { ReactNode } from 'react'
import type { Reconciliation } from '../services/reconciliationService'
import type { InvoiceResponse } from '../services/invoiceService'
import { reconciliationCalculation, reconciliationCommentLabel, reconciliationDifferenceCalculation, reconciliationMoney, reconciliationRoundingNote, reconciliationRuleDescription, reconciliationRuleLabel, reconciliationStatusClass, reconciliationStatusLabel } from '../utils/reconciliationPresentation'

export default function ReconciliationResultCard({ item, invoice, children }: { item: Reconciliation; invoice?: InvoiceResponse | null; children?: ReactNode }) {
  const note = reconciliationCommentLabel(item.comment)
  const calculation = reconciliationCalculation(item, invoice)
  const difference = reconciliationDifferenceCalculation(item)
  const roundingNote = reconciliationRoundingNote(item)
  const money = (value: number | null) => value == null ? '—' : `${reconciliationMoney(value)} đ`
  return <section className="si-card p-4">
    <div className="flex items-center justify-between gap-3">
      <p className="si-section-title">{reconciliationRuleLabel(item.type)}</p>
      <span className={reconciliationStatusClass(item.status)}>{reconciliationStatusLabel(item.status)}</span>
    </div>
    <p className="mt-2 text-sm text-ink-muted">{reconciliationRuleDescription(item.type)}</p>
    <div className="mt-3 grid grid-cols-3 gap-2 text-sm">
      <div><p className="text-xs text-ink-muted">Giá trị hóa đơn</p><p className="si-num font-medium">{money(item.expectedAmount)}</p></div>
      <div><p className="text-xs text-ink-muted">Giá trị tính lại</p><p className="si-num font-medium">{money(item.actualAmount)}</p></div>
      <div><p className="text-xs text-ink-muted">Chênh lệch</p><p className="si-num font-semibold">{money(item.differenceAmount)}</p></div>
    </div>
    {(calculation || difference) && <dl className="mt-3 space-y-2 rounded-control bg-subtle p-3 text-sm">
      {calculation && <div><dt className="text-xs text-ink-muted">Cách tính lại</dt><dd className="si-num break-words">{calculation}</dd></div>}
      {difference && <div><dt className="text-xs text-ink-muted">Chênh lệch = tính lại − hóa đơn</dt><dd className="si-num break-words">{difference}</dd></div>}
    </dl>}
    {roundingNote && <p className="mt-3 text-xs text-ink-muted">{roundingNote}</p>}
    {note && <p className="mt-3 text-xs text-ink-muted">{note}</p>}
    {children}
  </section>
}

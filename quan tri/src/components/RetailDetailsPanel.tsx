import { useEffect, useState } from 'react'
import invoiceService, { type RetailInvoiceDetailsView } from '../services/invoiceService'
import { getErrorMessage } from '../services/authService'
import {
  adjustmentLabels, adjustmentSummary, fromRetailDraft, lineBasisLabels, retailMoney, retailServerErrors, toRetailDraft,
  treatmentLabels, type AdjustmentKey, type LineBasis, type RetailDraft, type Treatment,
} from '../utils/retailPresentation'

/**
 * Pricing evidence of a retail/POS bill (discounts, tax/fees treatment, cash given/change), read and corrected
 * through /api/v1/invoices/{id}/retail-details. It never changes the bill's own amounts or items.
 */
export default function RetailDetailsPanel({ invoiceId, rowCount, canEdit, refreshKey, onSaved }: {
  invoiceId: number; rowCount: number; canEdit: boolean
  /** Changes when the invoice header/items were saved, so "stale" is re-read. */
  refreshKey?: string
  onSaved?: () => void
}) {
  const [view, setView] = useState<RetailInvoiceDetailsView | null>(null)
  const [loadError, setLoadError] = useState('')
  const [draft, setDraft] = useState<RetailDraft | null>(null)
  const [errors, setErrors] = useState<string[]>([])
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let active = true
    setLoadError('')
    invoiceService.getRetailDetails(invoiceId).then(result => { if (active) setView(result) })
      .catch(error => { if (active) setLoadError(getErrorMessage(error, 'Không tải được thông tin bán lẻ.')) })
    return () => { active = false }
  }, [invoiceId, refreshKey])

  const details = view?.details ?? null
  const save = async () => {
    if (!draft) return
    const { details: request, errors: problems } = fromRetailDraft(draft, rowCount)
    setErrors(problems)
    if (!request) return
    setSaving(true)
    try {
      setView(await invoiceService.updateRetailDetails(invoiceId, request))
      setDraft(null); onSaved?.()
    } catch (error) {
      setErrors(retailServerErrors(getErrorMessage(error, 'Không thể lưu thông tin bán lẻ.')))
    } finally {
      setSaving(false)
    }
  }
  const setAdjustment = (key: AdjustmentKey, change: Partial<RetailDraft[AdjustmentKey]>) =>
    setDraft(current => current && { ...current, [key]: { ...current[key], ...change } })

  return <section className="rounded-card border-hairline border p-4" aria-label="Thông tin hóa đơn bán lẻ">
    <div className="flex flex-wrap items-start justify-between gap-2">
      <div>
        <h4 className="si-label text-brand-700">Căn cứ tính tiền bán lẻ</h4>
        <p className="mt-1 text-xs text-ink-muted">Thông tin in trên phiếu, dùng để đối soát tổng tiền. Không phải xác nhận đã thanh toán.</p>
      </div>
      {canEdit && !draft && <button type="button" className="si-btn-secondary text-sm" onClick={() => { setDraft(toRetailDraft(details)); setErrors([]) }}>
        {details ? 'Sửa' : 'Nhập thông tin'}</button>}
    </div>
    {loadError && <p className="mt-3 text-sm text-review">{loadError}</p>}
    {view?.stale && <p className="mt-3 rounded-control bg-review-surface px-3 py-2 text-sm text-review">
      Tổng tiền hoặc hàng hóa đã thay đổi sau khi nhập căn cứ này. Hãy kiểm tra và lưu lại.</p>}

    {!draft && view && <dl className="mt-3 grid gap-x-4 gap-y-2 text-sm sm:grid-cols-2">
      {!details && <p className="text-ink-muted sm:col-span-2">Chưa có thông tin bán lẻ. {canEdit ? 'Bấm "Nhập thông tin" để bổ sung.' : ''}</p>}
      {details && <>
        <div><dt className="text-xs text-ink-muted">Giờ giao dịch</dt><dd>{details.transactionTime || '—'}</dd></div>
        <div><dt className="text-xs text-ink-muted">Thành tiền dòng</dt><dd>{lineBasisLabels[details.lineAmountBasis ?? 'UNKNOWN']}</dd></div>
        <div className="sm:col-span-2"><dt className="text-xs text-ink-muted">Giảm giá từng dòng</dt><dd>
          {details.lineDiscounts == null ? 'Chưa kiểm tra' : details.lineDiscounts.length === 0 ? 'Không có'
            : details.lineDiscounts.map(item => `Dòng ${item.rowNumber}: ${retailMoney(item.amount)}`).join(' · ')}</dd></div>
        {(Object.keys(adjustmentLabels) as AdjustmentKey[]).map(key => <div key={key}>
          <dt className="text-xs text-ink-muted">{adjustmentLabels[key]}</dt><dd>{adjustmentSummary(details[key])}</dd></div>)}
        <div><dt className="text-xs text-ink-muted">Tiền khách đưa</dt><dd>{retailMoney(details.tenderedAmount)}</dd></div>
        <div><dt className="text-xs text-ink-muted">Tiền thối lại</dt><dd>{retailMoney(details.changeAmount)}</dd></div>
      </>}
    </dl>}

    {draft && <div className="mt-3 space-y-3 text-sm">
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-xs font-semibold text-ink-soft">Giờ giao dịch (nếu có in)
          <input className="si-input mt-1" placeholder="HH:mm" value={draft.transactionTime}
            onChange={event => setDraft({ ...draft, transactionTime: event.target.value })} /></label>
        <label className="block text-xs font-semibold text-ink-soft">Thành tiền dòng là
          <select className="si-select mt-1" value={draft.lineAmountBasis}
            onChange={event => setDraft({ ...draft, lineAmountBasis: event.target.value as LineBasis })}>
            {(Object.keys(lineBasisLabels) as LineBasis[]).map(key => <option key={key} value={key}>{lineBasisLabels[key]}</option>)}
          </select></label>
      </div>
      <fieldset className="rounded-control border-hairline border p-3">
        <legend className="px-1 text-xs font-semibold text-ink-soft">Giảm giá từng dòng</legend>
        <label className="flex items-center gap-2 text-xs"><input type="checkbox" checked={draft.lineDiscountsChecked}
          onChange={event => setDraft({ ...draft, lineDiscountsChecked: event.target.checked })} />
          Đã kiểm tra (dòng không liệt kê là không có giảm giá)</label>
        {draft.lineDiscountsChecked && <div className="mt-2 space-y-2">
          {draft.lineDiscounts.map((line, index) => <div key={index} className="flex items-center gap-2">
            <select aria-label="Dòng hàng" className="si-select w-24" value={line.rowNumber}
              onChange={event => setDraft({ ...draft, lineDiscounts: draft.lineDiscounts.map((item, i) => i === index ? { ...item, rowNumber: event.target.value } : item) })}>
              <option value="">Dòng</option>
              {Array.from({ length: rowCount }, (_, i) => <option key={i + 1} value={i + 1}>Dòng {i + 1}</option>)}
            </select>
            <input aria-label="Số tiền giảm" className="si-input" inputMode="decimal" placeholder="Số tiền giảm" value={line.amount}
              onChange={event => setDraft({ ...draft, lineDiscounts: draft.lineDiscounts.map((item, i) => i === index ? { ...item, amount: event.target.value } : item) })} />
            <button type="button" className="si-link shrink-0" onClick={() => setDraft({ ...draft, lineDiscounts: draft.lineDiscounts.filter((_, i) => i !== index) })}>Xóa</button>
          </div>)}
          <button type="button" className="si-link" disabled={rowCount === 0}
            onClick={() => setDraft({ ...draft, lineDiscounts: [...draft.lineDiscounts, { rowNumber: '', amount: '' }] })}>
            + Thêm dòng có giảm giá</button>
          {rowCount === 0 && <p className="text-xs text-ink-muted">Chứng từ chưa có dòng hàng hóa.</p>}
        </div>}
      </fieldset>
      {(Object.keys(adjustmentLabels) as AdjustmentKey[]).map(key => <div key={key} className="grid gap-2 sm:grid-cols-2">
        <label className="block text-xs font-semibold text-ink-soft">{adjustmentLabels[key]}
          <select className="si-select mt-1" value={draft[key].treatment}
            onChange={event => setAdjustment(key, { treatment: event.target.value as Treatment })}>
            {(Object.keys(treatmentLabels) as Treatment[]).map(value => <option key={value} value={value}>{treatmentLabels[value]}</option>)}
          </select></label>
        {draft[key].treatment !== 'NOT_PRESENT' && <label className="block text-xs font-semibold text-ink-soft">Số tiền
          <input className="si-input mt-1" inputMode="decimal" value={draft[key].amount}
            placeholder={draft[key].treatment === 'SEPARATE' ? 'Bắt buộc' : 'Nếu có in'}
            onChange={event => setAdjustment(key, { amount: event.target.value })} /></label>}
      </div>)}
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-xs font-semibold text-ink-soft">Tiền khách đưa (nếu có in)
          <input className="si-input mt-1" inputMode="decimal" value={draft.tenderedAmount}
            onChange={event => setDraft({ ...draft, tenderedAmount: event.target.value })} /></label>
        <label className="block text-xs font-semibold text-ink-soft">Tiền thối lại (nếu có in)
          <input className="si-input mt-1" inputMode="decimal" value={draft.changeAmount}
            onChange={event => setDraft({ ...draft, changeAmount: event.target.value })} /></label>
      </div>
      <p className="text-xs text-ink-muted">Số tiền: dấu chấm ngăn nghìn, dấu phẩy phần lẻ.</p>
      {errors.length > 0 && <ul role="alert" className="list-disc space-y-1 pl-5 text-sm text-failed">{errors.map(error => <li key={error}>{error}</li>)}</ul>}
      <div className="flex gap-2">
        <button type="button" className="si-btn-primary text-sm" disabled={saving} onClick={() => void save()}>{saving ? 'Đang lưu...' : 'Lưu thông tin bán lẻ'}</button>
        <button type="button" className="si-btn-secondary text-sm" disabled={saving} onClick={() => { setDraft(null); setErrors([]) }}>Hủy</button>
      </div>
    </div>}
  </section>
}

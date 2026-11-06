import { useState } from 'react'
import type { Reconciliation } from '../services/reconciliationService'
import { getErrorMessage } from '../services/authService'
import { reconciliationDecision, withoutErrorCode } from '../utils/reconciliationPresentation'

export default function ReconciliationDecision({ item, onDecide }: {
  item: Reconciliation; onDecide: (status: 'ACCEPTED' | 'NEED_REVIEW', comment?: string) => Promise<void>
}) {
  const decision = reconciliationDecision(item.status)
  const [isOpen, setIsOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [isPending, setIsPending] = useState(false)
  const [error, setError] = useState('')

  if (!decision) return null
  const submit = async (status: 'ACCEPTED' | 'NEED_REVIEW', comment?: string) => {
    setIsPending(true); setError('')
    try { await onDecide(status, comment) }
    catch (failure) { setError(withoutErrorCode(getErrorMessage(failure, 'Không thể lưu quyết định đối soát. Vui lòng thử lại.'))) }
    finally { setIsPending(false) }
  }

  return <div className="mt-3 border-t border-hairline pt-3">
    {decision === 'REOPEN'
      ? <button type="button" disabled={isPending} onClick={() => void submit('NEED_REVIEW')} className="si-btn-secondary !px-2 !py-1 text-xs">Bỏ chấp nhận</button>
      : isOpen
        ? <div className="flex flex-wrap gap-2"><input aria-label="Lý do chấp nhận" value={reason} onChange={event => setReason(event.target.value)} placeholder="Nhập lý do chấp nhận" className="si-input min-w-48 flex-1" /><button type="button" disabled={isPending || !reason.trim()} onClick={() => void submit('ACCEPTED', reason.trim())} className="si-btn-primary">{isPending ? 'Đang lưu...' : 'Xác nhận'}</button><button type="button" disabled={isPending} onClick={() => { setIsOpen(false); setError('') }} className="si-btn-secondary">Hủy</button></div>
        : <button type="button" onClick={() => setIsOpen(true)} className="si-btn-secondary !px-2 !py-1 text-xs">Chấp nhận kèm lý do</button>}
    {error && <p className="mt-2 text-xs text-failed">{error}</p>}
  </div>
}

import { formatDateTime } from '../utils/dateTime'
import type { DocumentBusinessState } from '../services/documentService';
import { statePresentation } from '../utils/businessStatePresentation';
const actionLabel = (action: string) => action.replace(/^(SUBMIT|RESUBMIT|START_REVIEW|APPROVE|REJECT|REQUEST_INFO)/, value => ({SUBMIT:"Gửi xử lý",RESUBMIT:"Gửi lại",START_REVIEW:"Bắt đầu duyệt",APPROVE:"Phê duyệt",REJECT:"Từ chối",REQUEST_INFO:"Yêu cầu bổ sung"}[value] || value));
export default function BusinessStateCard({state}: {state: DocumentBusinessState | null}) {
 if (!state) return <section className="si-card p-4 text-ink-muted">Chưa tải được trạng thái nghiệp vụ.</section>;
 const groups = [['processing','OCR/AI'],['classification','Phân loại'],['reconciliation','Đối soát'],['review','Kiểm duyệt'],['payment','Thanh toán']] as const;
 return <section className="si-card p-4"><h3 className="si-section-title">Trạng thái nghiệp vụ</h3><div className="mt-3 grid gap-3 sm:grid-cols-2">{groups.map(([key,label]) => {const value=statePresentation(key,state[key]); return <div key={key}><p className="text-xs text-ink-muted">{label}</p><p className={value.tone}>{value.label}</p></div>;})}</div>
 {!state.payment && <p className="mt-3 text-xs text-ink-muted">{state.paymentReason}</p>}
 <details className="mt-3 text-sm"><summary>Lịch sử kiểm duyệt ({state.history.length})</summary>{state.history.map(event => <p className="mt-2" key={event.id}>{event.at ? formatDateTime(event.at) : 'Chưa có thời điểm'} · {event.actorId ? 'Người thực hiện #' + event.actorId : 'Chưa xác định người thực hiện'} · {actionLabel(event.action)}</p>)}</details></section>;
}

import { formatDateTime } from '../utils/dateTime'
import {useEffect,useState} from 'react';
import service,{type PaymentSummary,type PaymentHistoryEvent} from '../services/financialTransactionService';
import {getErrorMessage} from '../services/authService';
import PaymentSummaryCard from './PaymentSummaryCard';
const snapshot=(raw:string):PaymentSummary|null=>{try{return JSON.parse(raw).summary||null}catch{return null}};
export default function DocumentPaymentPanel({documentId,companyId,canRun}:{documentId:number;companyId:number|null;canRun:boolean}){
 const [summary,setSummary]=useState<PaymentSummary|null>(null),[history,setHistory]=useState<PaymentHistoryEvent[]>([]),[error,setError]=useState(''),[running,setRunning]=useState(false);
 const params={documentId,companyId:companyId??undefined};
 useEffect(()=>{let active=true;setSummary(null);setError('');Promise.all([service.summary({documentId,companyId:companyId??undefined}),service.reconciliationHistory({documentId,companyId:companyId??undefined})]).then(([s,h])=>{if(active){setSummary(s);setHistory(h)}}).catch(e=>{if(active)setError(getErrorMessage(e,'Chưa tải được trạng thái thanh toán.'))});return()=>{active=false}},[documentId,companyId]);
 const run=async()=>{setRunning(true);setError('');try{setSummary(await service.reconcile(params));setHistory(await service.reconciliationHistory(params))}catch(e){setError(getErrorMessage(e,'Không chạy được đối soát thanh toán.'))}finally{setRunning(false)}};
 return <section className="si-card space-y-3 p-4"><div className="flex justify-between gap-3"><h3 className="si-section-title">Thanh toán và đối soát thu/chi</h3>{canRun&&<button type="button" className="si-btn-secondary" disabled={running} onClick={()=>void run()}>{running?'Đang đối soát...':'Chạy đối soát thanh toán'}</button>}</div>{error&&<p className="text-failed">{error}</p>}{summary&&<PaymentSummaryCard summary={summary}/>}<details><summary>Lịch sử đối soát thanh toán ({history.length})</summary>{history.map(h=><article className="mt-2 text-sm" key={h.id}><p>Lần #{h.id} · {formatDateTime(h.at)} · Người thực hiện #{h.actorId??'—'}</p>{snapshot(h.newValue)&&<PaymentSummaryCard summary={snapshot(h.newValue)!}/>}</article>)}</details></section>
}

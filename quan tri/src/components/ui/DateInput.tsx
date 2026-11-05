import { useEffect, useRef, useState, type ChangeEvent, type InputHTMLAttributes } from 'react'
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react'
import { formatDate, isIsoDate, parseDisplayDate } from '../../utils/dateTime'
import { monthGrid, shiftMonth, todayInVietnam, weekdayLabels } from '../../utils/calendarMonth'

/** Valid input emits ISO. Invalid drafts remain visible and are rejected by range/API guards. */
export function DateTextInput({value,onChange,...props}:InputHTMLAttributes<HTMLInputElement>){
 const raw=String(value??'')
 return <input {...props} ref={input=>{input?.setCustomValidity(raw&&!isIsoDate(raw)?'Ngày không hợp lệ. Nhập theo dd/MM/yyyy.':'')}} type="text" inputMode="numeric" placeholder="dd/MM/yyyy" maxLength={10} pattern="[0-9]{2}/[0-9]{2}/[0-9]{4}" value={isIsoDate(raw)?formatDate(raw):raw}
 onChange={event=>{const input=event.currentTarget,next=parseDisplayDate(input.value);input.setCustomValidity(next&&!isIsoDate(next)?'Ngày không hợp lệ. Nhập theo dd/MM/yyyy.':'');onChange?.({...event,target:{...input,value:next},currentTarget:input})}} />
}

/** Typed dd/MM/yyyy input plus a Vietnamese month calendar (weeks start Monday). Never uses type="date",
 *  so the display format does not depend on the browser locale. */
export default function DateInput({value,onChange,disabled,className,...props}:InputHTMLAttributes<HTMLInputElement>){
 const raw=String(value??'')
 const [open,setOpen]=useState(false)
 const initial=(isIsoDate(raw)?raw:todayInVietnam()).split('-').map(Number)
 const [view,setView]=useState({year:initial[0],month:initial[1]})
 const host=useRef<HTMLDivElement>(null)
 useEffect(()=>{
  if(!open)return
  const close=(event:MouseEvent)=>{if(!host.current?.contains(event.target as Node))setOpen(false)}
  const escape=(event:KeyboardEvent)=>{if(event.key==='Escape')setOpen(false)}
  globalThis.document.addEventListener('mousedown',close);globalThis.document.addEventListener('keydown',escape)
  return()=>{globalThis.document.removeEventListener('mousedown',close);globalThis.document.removeEventListener('keydown',escape)}
 },[open])
 const toggle=()=>{
  if(!open){const [year,month]=(isIsoDate(raw)?raw:todayInVietnam()).split('-').map(Number);setView({year,month})}
  setOpen(current=>!current)
 }
 const pick=(iso:string)=>{
  onChange?.({target:{value:iso},currentTarget:{value:iso}} as unknown as ChangeEvent<HTMLInputElement>)
  setOpen(false)
 }
 const today=todayInVietnam()
 return <div ref={host} className="relative">
  <DateTextInput {...props} value={value} onChange={onChange} disabled={disabled} className={`${className??''} pr-10`} />
  <button type="button" disabled={disabled} onClick={toggle} aria-label="Chọn ngày trên lịch" aria-expanded={open}
   className="absolute right-1.5 top-1/2 -translate-y-1/2 rounded p-1.5 text-ink-muted hover:bg-subtle hover:text-ink disabled:opacity-40">
   <CalendarDays size={18}/>
  </button>
  {open&&<div role="dialog" aria-label="Lịch chọn ngày" className="absolute left-0 top-full z-40 mt-1 w-72 rounded-card border border-hairline bg-surface p-3 shadow-elev1">
   <div className="mb-2 flex items-center justify-between">
    <button type="button" aria-label="Tháng trước" onClick={()=>setView(shiftMonth(view.year,view.month,-1))} className="rounded p-1 hover:bg-subtle"><ChevronLeft size={18}/></button>
    <span className="text-sm font-semibold">Tháng {view.month}/{view.year}</span>
    <button type="button" aria-label="Tháng sau" onClick={()=>setView(shiftMonth(view.year,view.month,1))} className="rounded p-1 hover:bg-subtle"><ChevronRight size={18}/></button>
   </div>
   <div className="grid grid-cols-7 gap-0.5 text-center text-xs">
    {weekdayLabels.map(label=><span key={label} className="py-1 font-semibold text-ink-muted">{label}</span>)}
    {monthGrid(view.year,view.month).map(cell=>{
     const selected=cell.iso===raw
     return <button key={cell.iso} type="button" onClick={()=>pick(cell.iso)} aria-label={formatDate(cell.iso)} aria-pressed={selected}
      className={`rounded py-1.5 text-sm ${selected?'bg-brand-600 font-semibold text-white':cell.iso===today?'border border-brand-600 text-brand-700':cell.inMonth?'text-ink hover:bg-subtle':'text-ink-muted/60 hover:bg-subtle'}`}>{cell.day}</button>
    })}
   </div>
   <div className="mt-2 flex justify-between text-xs">
    <button type="button" onClick={()=>pick(today)} className="text-brand-700 hover:underline">Hôm nay</button>
    {raw&&<button type="button" onClick={()=>pick('')} className="text-ink-muted hover:underline">Xóa ngày</button>}
   </div>
  </div>}
 </div>
}

import type { InputHTMLAttributes } from 'react'
import { formatDate, isIsoDate, parseDisplayDate } from '../../utils/dateTime'
/** Valid input emits ISO. Invalid drafts remain visible and are rejected by range/API guards. */
export default function DateInput({value,onChange,...props}:InputHTMLAttributes<HTMLInputElement>){
 const raw=String(value??'')
 return <input {...props} ref={input=>{input?.setCustomValidity(raw&&!isIsoDate(raw)?'Ngày không hợp lệ. Nhập theo dd/MM/yyyy.':'')}} type="text" inputMode="numeric" placeholder="dd/MM/yyyy" maxLength={10} pattern="[0-9]{2}/[0-9]{2}/[0-9]{4}" value={isIsoDate(raw)?formatDate(raw):raw}
 onChange={event=>{const input=event.currentTarget,next=parseDisplayDate(input.value);input.setCustomValidity(next&&!isIsoDate(next)?'Ngày không hợp lệ. Nhập theo dd/MM/yyyy.':'');onChange?.({...event,target:{...input,value:next},currentTarget:input})}} />
}

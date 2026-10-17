import {useEffect,useState} from 'react'
import {isIsoDate} from '../utils/dateTime'
/** Retain calendar filters across navigation without recomputing them from today's date. */
export function useDateFilter(key:string){
 const storageKey='smartinvoice.date.'+key
 const [value,setValue]=useState(()=>{try{const old=sessionStorage.getItem(storageKey)||'';return isIsoDate(old)?old:''}catch{return ''}})
 useEffect(()=>{if(value&&!isIsoDate(value))return;try{sessionStorage.setItem(storageKey,value)}catch{/* Storage may be disabled. */}},[storageKey,value])
 return [value,setValue] as const
}

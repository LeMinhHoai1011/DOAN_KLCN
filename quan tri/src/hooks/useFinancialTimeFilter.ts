import {useEffect,useState} from 'react'
import {useSearchParams} from 'react-router-dom'
import {getCurrentUser} from '../services/authService'
import {defaultFinancialFilter,financialFilterError,hasTimeFilter,readTimeFilter,selectedInterval,selectedTimeRange,writeTimeFilter} from '../utils/financialTimeFilter'
import type {FinancialTimeFilter} from '../utils/financialTimeFilter'
export function useFinancialTimeFilter(){
 const [params,setParams]=useSearchParams(),user=getCurrentUser()
 const key='smartinvoice.financial-time.'+(user?.id??'anonymous')
 const [fallback]=useState(()=>{try{return readTimeFilter(new URLSearchParams(sessionStorage.getItem(key)||''),defaultFinancialFilter())}catch{return defaultFinancialFilter()}})
 const filter=readTimeFilter(params,fallback),serialized=writeTimeFilter(new URLSearchParams(),filter).toString()
 useEffect(()=>{try{sessionStorage.setItem(key,serialized)}catch{/* URL remains the source of truth. */}
 if(!hasTimeFilter(params))setParams(previous=>writeTimeFilter(previous,filter),{replace:true})
 },[key,serialized,params,setParams])
 const update=(next:FinancialTimeFilter)=>setParams(previous=>writeTimeFilter(previous,next))
 const companyId=Number(params.get('companyId'))||user?.companyId||undefined
 return {filter,update,companyId,...selectedTimeRange(filter),interval:selectedInterval(filter),filterError:financialFilterError(filter)}
}

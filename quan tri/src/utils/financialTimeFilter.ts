import {businessToday,dateRangeError,isIsoDate} from './dateTime.ts'
export type TimeMode='MONTH'|'DAY'|'RANGE'
export interface FinancialTimeFilter {mode:TimeMode;month:string;day:string;from:string;to:string}
export const defaultFinancialFilter=(today=businessToday()):FinancialTimeFilter=>({mode:'RANGE',month:today.slice(0,7),day:today,from:today.slice(0,7)+'-01',to:today})
export const monthBounds=(month:string)=>{
 if(!/^\d{4}-\d{2}$/.test(month)||!isIsoDate(month+'-01'))return {dateFrom:'',dateTo:''}
 let last=31;while(!isIsoDate(month+'-'+last))last--
 return {dateFrom:month+'-01',dateTo:month+'-'+String(last).padStart(2,'0')}
}
export const selectedTimeRange=(f:FinancialTimeFilter)=>f.mode==='MONTH'?monthBounds(f.month):f.mode==='DAY'?{dateFrom:f.day,dateTo:f.day}:{dateFrom:f.from,dateTo:f.to}
export const financialFilterError=(f:FinancialTimeFilter)=>{const r=selectedTimeRange(f);return !r.dateFrom||!r.dateTo?'Vui lòng chọn đầy đủ thời gian hợp lệ.':dateRangeError(r.dateFrom,r.dateTo)}
export const selectedInterval=(f:FinancialTimeFilter):'DAILY'|'MONTHLY'=>{const r=selectedTimeRange(f);return !financialFilterError(f)&&f.mode==='RANGE'&&(Date.parse(r.dateTo+'T00:00:00Z')-Date.parse(r.dateFrom+'T00:00:00Z'))/86400000>92?'MONTHLY':'DAILY'}
export const shiftMonth=(month:string,step:number)=>{
 if(!monthBounds(month).dateFrom)return month
 const [y,m]=month.split('-').map(Number),index=y*12+m-1+step,nextYear=Math.floor(index/12)
 return nextYear<1||nextYear>9999?month:String(nextYear).padStart(4,'0')+'-'+String(index%12+1).padStart(2,'0')
}
export const timeFilterKeys=['timeMode','month','day','rangeFrom','rangeTo','dateFrom','dateTo']
export const hasTimeFilter=(p:URLSearchParams)=>timeFilterKeys.some(k=>p.has(k))
export const readTimeFilter=(p:URLSearchParams,fallback:FinancialTimeFilter):FinancialTimeFilter=>{
 if(!hasTimeFilter(p))return fallback
 const mode=p.get('timeMode')
 return {mode:mode==='MONTH'||mode==='DAY'?mode:'RANGE',month:p.get('month')??fallback.month,day:p.get('day')??fallback.day,from:p.get('rangeFrom')??p.get('dateFrom')??fallback.from,to:p.get('rangeTo')??p.get('dateTo')??fallback.to}
}
export const writeTimeFilter=(previous:URLSearchParams,f:FinancialTimeFilter)=>{
 const p=new URLSearchParams(previous),r=selectedTimeRange(f)
 Object.entries({timeMode:f.mode,month:f.month,day:f.day,rangeFrom:f.from,rangeTo:f.to,dateFrom:r.dateFrom,dateTo:r.dateTo}).forEach(([k,v])=>p.set(k,v))
 return p
}

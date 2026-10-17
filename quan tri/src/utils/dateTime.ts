export const BUSINESS_TIME_ZONE = 'Asia/Ho_Chi_Minh'
export const isIsoDate = (v: string): boolean => {
 if(!/^\d{4}-\d{2}-\d{2}$/.test(v))return false
 const [y,m,d]=v.split('-').map(Number),leap=y%4===0&&(y%100!==0||y%400===0)
 return y>0&&m>0&&m<=12&&d>0&&d<=[31,leap?29:28,31,30,31,30,31,31,30,31,30,31][m-1]
}
export const formatDate = (v?:string|null,fallback='—') => isIsoDate(v||'')?v!.split('-').reverse().join('/'):fallback
export const parseDisplayDate = (v:string) => {const iso=v.split('/').reverse().join('-');return /^\d{2}\/\d{2}\/\d{4}$/.test(v)&&isIsoDate(iso)?iso:v}
const parts=(v:Date)=>Object.fromEntries(new Intl.DateTimeFormat('en-GB',{timeZone:BUSINESS_TIME_ZONE,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(v).map(p=>[p.type,p.value]))
export const businessToday=(now=new Date())=>{const p=parts(now);return p.year+'-'+p.month+'-'+p.day}
export const formatDateTime=(v?:string|null,fallback='—'):string=>{
 if(!v)return fallback
 const n=/^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?$/.exec(v)
 // Legacy LocalDateTime has no offset. Preserve its wall clock without inventing a zone.
 if(n)return isIsoDate(n[1])&&+n[2]<24&&+n[3]<60&&+(n[4]||0)<60?formatDate(n[1])+' '+n[2]+':'+n[3]:fallback
 if(!/^\d{4}-\d{2}-\d{2}T.*(?:Z|[+-]\d{2}:\d{2})$/.test(v)||!isIsoDate(v.slice(0,10)))return fallback
 const date=new Date(v);if(!Number.isFinite(date.getTime()))return fallback
 const p=parts(date);return p.day+'/'+p.month+'/'+p.year+' '+p.hour+':'+p.minute
}
export const timestampDate=(v:string)=>formatDateTime(v).split(' ')[0]
export const dateRangeError=(from?:string,to?:string):string=>{
 if((from&&!isIsoDate(from))||(to&&!isIsoDate(to)))return 'Ngày không hợp lệ. Nhập theo dd/MM/yyyy.'
 return from&&to&&from>to?'Ngày bắt đầu không được sau ngày kết thúc.':''
}
export const assertDateRange=(from?:string,to?:string)=>{const error=dateRangeError(from,to);if(error)throw new Error(error)}
export const instantDateBounds=(from:string,to:string)=>{assertDateRange(from,to);return {from:from?new Date(from+'T00:00:00+07:00').toISOString():undefined,to:to?new Date(to+'T23:59:59.999+07:00').toISOString():undefined}}
export const formatPeriod=(v:string)=>/^\d{4}-\d{2}$/.test(v)?v.split('-').reverse().join('/'):formatDate(v,v)

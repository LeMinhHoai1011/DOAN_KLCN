/** Show transport/status failures separately from an empty successful response. Never expose SQL or stack traces. */
export const apiLoadError=(error:unknown,fallback:string):string=>{
 const e=error as {isAxiosError?:boolean;response?:{status?:number;data?:{error?:unknown}}}|null
 const status=e?.response?.status
 if(status){
  const raw=e?.response?.data?.error,code=typeof raw==='string'&&/^[A-Z][A-Z0-9_]{0,63}$/.test(raw)?raw:''
  const message=status===403?'Bạn không có quyền tải dữ liệu trong phạm vi này.':fallback
  return message+' (HTTP '+status+(code?' · '+code:'')+').'
 }
 return e?.isAxiosError?'Không thể kết nối backend. Vui lòng kiểm tra dịch vụ và kết nối mạng.':fallback
}

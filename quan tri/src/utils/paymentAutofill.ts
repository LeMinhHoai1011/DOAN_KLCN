import type {TransactionRequest,PaymentLinkOption,PaymentPreview,FinancialTransaction,AccountingCategory} from '../services/financialTransactionService'
import {isIsoDate} from './dateTime.ts'
export type PaymentEdits=Partial<Record<'amount'|'description'|'categoryId'|'transactionType',boolean>>
export const paymentLinkKey=(f:{companyId?:number;documentId?:number|null;invoiceId?:number|null})=>[f.companyId??'',f.documentId??'',f.invoiceId??''].join(':')
export type PaymentLinkKind='documents'|'invoices'|'none'
export const clearForSelection=(current:TransactionRequest,option?:PaymentLinkOption,kind:PaymentLinkKind='documents'):TransactionRequest=>({...current,documentId:kind==='documents'?option?.documentId:undefined,invoiceId:kind==='invoices'?option?.invoiceId??undefined:undefined,amount:'',description:undefined,categoryId:undefined,paymentMethod:undefined,paymentConfirmed:false,requestKey:crypto.randomUUID()})
export const applyPaymentPreview=(current:TransactionRequest,requestedKey:string,p:PaymentPreview,edits:PaymentEdits,original?:FinancialTransaction):TransactionRequest=>{
 if(paymentLinkKey(current)!==requestedKey)return current
 const sameOriginal=original&&((current.documentId!=null&&current.documentId===original.documentId)||(current.invoiceId!=null&&current.invoiceId===original.invoiceId))
 if(sameOriginal)return current
 // A suggestion must never silently change the user's Thu/Chi choice.
 const type=current.transactionType
 return {...current,transactionType:type,paymentConfirmed:false,
 ...(!edits.amount&&current.amount===''?{amount:p.availableAmount!=null&&p.availableAmount>0?String(p.availableAmount):''}:{}),
 ...(!edits.description&&!current.description?{description:p.suggestedDescription}:{}),
 ...(!edits.categoryId&&!current.categoryId&&p.link.suggestedCategoryId&&(p.directionSource==='INVOICE_COMPANY_TAX_CODE'&&p.link.suggestedTransactionType===type&&p.suggestedCategoryType===type)?{categoryId:p.link.suggestedCategoryId}:{})}
}
export const paymentFieldErrors=(form:TransactionRequest,methods:string[],categories:AccountingCategory[])=>{
 const errors:Partial<Record<keyof TransactionRequest,string>>={}
 if(!['INCOME','EXPENSE'].includes(form.transactionType))errors.transactionType='Chọn loại Thu hoặc Chi hợp lệ.'
 if(!isIsoDate(form.transactionDate))errors.transactionDate='Ngày giao dịch không hợp lệ.'
 if(!/^\d{1,17}(?:\.\d{1,2})?$/.test(String(form.amount))||!Number.isFinite(Number(form.amount))||Number(form.amount)<=0)errors.amount='Nhập số tiền lớn hơn 0, tối đa 2 chữ số thập phân.'
 if(!methods.includes(form.paymentMethod||''))errors.paymentMethod='Vui lòng chọn phương thức thanh toán.'
 if(form.categoryId){const c=categories.find(c=>c.id===form.categoryId);if(!c||!c.active||(c.companyId!=null&&c.companyId!==form.companyId)||(c.categoryType&&c.categoryType!==form.transactionType))errors.categoryId='Danh mục không hợp lệ hoặc không khớp Thu/Chi.'}
 if(!form.paymentConfirmed)errors.paymentConfirmed='Cần xác nhận đây là khoản tiền đã thực thu/thực chi.'
 return errors
}
export const paymentFormError=(form:TransactionRequest,methods:string[],categories:AccountingCategory[]):string=>Object.values(paymentFieldErrors(form,methods,categories))[0]||''
export const previewRequest=(form:TransactionRequest,kind:PaymentLinkKind,editingId?:number)=>({companyId:form.companyId,...(kind==='invoices'?{invoiceId:form.invoiceId}:kind==='documents'?{documentId:form.documentId}:{}),...(editingId!=null?{editingId}:{})})
export const paymentSaveError=(error:unknown)=>{
 const e=error as {response?:{status?:number;data?:{error?:unknown}};isAxiosError?:boolean}|null
 const status=e?.response?.status,raw=e?.response?.data?.error,code=typeof raw==='string'&&/^[A-Z][A-Z0-9_]{0,63}$/.test(raw)?raw:''
 const message=code==='PAYMENT_SCHEMA_REVIEW_REQUIRED'?'Hồ sơ đã có giao dịch; UNIQUE INDEX hiện tại chặn phiếu bổ sung. Cần phê duyệt migration riêng, không thể tiếp tục lưu.':status===400?'Dữ liệu phiếu không hợp lệ. Kiểm tra các trường và chiều Thu/Chi.':status===403?'Bạn không có quyền tạo hoặc sửa phiếu trong doanh nghiệp này.':status===409?'Hồ sơ hoặc số dư đã thay đổi, hoặc requestKey xung đột. Kiểm tra lại trước khi gửi.':status===500?'Backend gặp lỗi cơ sở dữ liệu; phiếu chưa được xác nhận lưu thành công.':status?'Không thể ghi nhận phiếu. Vui lòng thử lại sau.':e?.isAxiosError?'Không kết nối được backend. Chưa xác định kết quả lưu; giữ nguyên requestKey khi thử lại.':'Không thể ghi nhận phiếu.'
 return message+(status?' (HTTP '+status+(code?' · '+code:'')+').':'')
}
export const previewAllows=(p:PaymentPreview|null,form:TransactionRequest)=>Boolean(p?.canCreate&&p.availableAmount!=null&&Number(form.amount)>0&&Number(form.amount)<=p.availableAmount&&(p.directionSource!=='INVOICE_COMPANY_TAX_CODE'||form.transactionType===p.link.suggestedTransactionType))

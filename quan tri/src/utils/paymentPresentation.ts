export const paymentMethodLabel=(value:string)=>({CASH:'Tiền mặt',BANK_TRANSFER:'Chuyển khoản',CARD:'Thẻ',OTHER:'Khác'}[value]||value);
export const paymentStateLabel=(value:string|null)=>value?({UNPAID:'Chưa thanh toán',PARTIALLY_PAID:'Thanh toán một phần',PAID:'Đã thanh toán',OVERPAID:'Thanh toán vượt'}[value]||'Chưa xác định'):'Chưa xác minh đầy đủ';
export function availableForPayment(summary:{remaining:number|null;documentId:number|null;invoiceId:number|null},original?:{amount:number;documentId:number|null;invoiceId:number|null;paymentConfirmation:string}){
 if(summary.remaining==null)return null;
 const same=original?.paymentConfirmation==='CONFIRMED'&&((summary.documentId!=null&&summary.documentId===original.documentId)||(summary.invoiceId!=null&&summary.invoiceId===original.invoiceId));
 return Number(summary.remaining)+(same?Number(original?.amount||0):0);
}

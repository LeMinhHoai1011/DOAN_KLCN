import test from 'node:test'
import assert from 'node:assert/strict'
import {clearForSelection,applyPaymentPreview,paymentLinkKey,paymentFormError,paymentFieldErrors,paymentSaveError,previewRequest,previewAllows} from './paymentAutofill.ts'
const initial=()=>({companyId:13,transactionType:'EXPENSE',transactionDate:'2026-10-17',amount:'',paymentConfirmed:false,requestKey:crypto.randomUUID()})
const option=(id=1)=>({documentId:id,invoiceId:id+10,documentName:'Hồ sơ',invoiceNumber:'HD'+id,totalAmount:100,suggestedTransactionType:'EXPENSE',suggestedCategoryId:9,currency:'VND'})
const preview=(remaining=60)=>({link:option(),summary:{documentId:1,invoiceId:11,totalAmount:100,confirmedPaid:100-remaining,remaining,paymentStatus:'PARTIALLY_PAID',verifiedPaymentStatus:'PARTIALLY_PAID',unverifiedCount:0,currency:'VND',comparison:'UNDERPAID'},availableAmount:remaining,canCreate:remaining>0,blockedCode:null,userMessage:null,directionSource:'INVOICE_COMPANY_TAX_CODE',suggestedDescription:'Thanh toán hóa đơn HD1',suggestedCategoryType:'EXPENSE'})
test('selection clears stale links/data/confirmation and preserves business date',()=>{
 const old={...initial(),documentId:7,invoiceId:77,amount:500,description:'Old',categoryId:8,paymentMethod:'CASH',paymentConfirmed:true}
 const fresh=clearForSelection(old,option());assert.equal(fresh.transactionDate,'2026-10-17');assert.equal(fresh.documentId,1);assert.equal(fresh.invoiceId,undefined);assert.equal(fresh.amount,'');assert.equal(fresh.description,undefined);assert.equal(fresh.categoryId,undefined);assert.equal(fresh.paymentMethod,undefined);assert.equal(fresh.paymentConfirmed,false);assert.notEqual(fresh.requestKey,old.requestKey)
 const detached=clearForSelection(fresh);assert.equal(detached.documentId,undefined);assert.equal(detached.invoiceId,undefined)
})
test('unpaid/partial/full previews fill only the remaining amount without confirming cash',()=>{
 for(const amount of [100,60,0]){const f=clearForSelection(initial(),option()),p=preview(amount),next=applyPaymentPreview(f,paymentLinkKey(f),p,{});assert.equal(next.amount,amount>0?String(amount):'');assert.equal(next.description,p.suggestedDescription);assert.equal(next.categoryId,9);assert.equal(next.transactionDate,f.transactionDate);assert.equal(next.paymentConfirmed,false);assert.equal(previewAllows(p,next),amount>0)}
})
test('late response for another link cannot overwrite new document selection',()=>{
 const old=clearForSelection(initial(),option()),next=clearForSelection(old,option(2));assert.equal(applyPaymentPreview(next,paymentLinkKey(old),preview(),{}),next)
})
test('manual edits including deliberate clearing survive response and reload',()=>{
 const f={...clearForSelection(initial(),option()),amount:'25',description:'Người dùng sửa',categoryId:22,transactionType:'INCOME'}
 const next=applyPaymentPreview(f,paymentLinkKey(f),preview(),{amount:true,description:true,categoryId:true,transactionType:true});assert.equal(next.amount,'25');assert.equal(next.description,f.description);assert.equal(next.categoryId,22);assert.equal(next.transactionType,'INCOME')
 const cleared={...f,amount:'',description:'',categoryId:undefined};const after=applyPaymentPreview(cleared,paymentLinkKey(cleared),preview(),{amount:true,description:true,categoryId:true});assert.equal(after.amount,'');assert.equal(after.description,'');assert.equal(after.categoryId,undefined)
})
test('editing an existing linked payment preserves fields and wrong direction/overpayment is blocked',()=>{
 const f={...initial(),documentId:1,invoiceId:11,amount:'25',description:'Original'},original={documentId:1,invoiceId:11,paymentConfirmation:'CONFIRMED',amount:25}
 assert.equal(applyPaymentPreview(f,paymentLinkKey(f),preview(),{},original),f)
 assert.equal(previewAllows(preview(),{...f,amount:61}),false);assert.equal(previewAllows(preview(),{...f,transactionType:'INCOME'}),false)
 assert.equal(previewAllows({...preview(),canCreate:false,blockedCode:'PAYMENT_SCHEMA_REVIEW_REQUIRED'},f),false)
})
test('validity includes date/method/cash confirmation/category and Vietnamese money does not change payload',()=>{
 const f={...initial(),amount:'9363000',paymentMethod:'CASH',paymentConfirmed:true};assert.equal(paymentFormError(f,['CASH'],[]),'');assert.equal(Number(f.amount).toLocaleString('vi-VN'),'9.363.000')
 for(const bad of [{amount:'0'},{amount:'1.001'},{transactionDate:'2026-02-29'},{paymentMethod:''},{paymentConfirmed:false},{categoryId:99}])assert.ok(paymentFormError({...f,...bad},['CASH'],[]))
 assert.equal(paymentFormError({...f,categoryId:1},['CASH'],[{id:1,active:true,companyId:13,categoryType:null}]),'')
 assert.ok(paymentFormError({...f,categoryId:1},['CASH'],[{id:1,active:true,companyId:13,categoryType:'INCOME'}]))
})

test('invoice and document modes each submit one primary ID; mode switch clears previous links',()=>{
 const old={...initial(),documentId:218,invoiceId:118,amount:15984000,description:'Old',paymentConfirmed:true}
 const invoice=clearForSelection(old,{...option(),documentId:218,invoiceId:118},'invoices')
 assert.equal(invoice.documentId,undefined);assert.equal(invoice.invoiceId,118);assert.deepEqual(previewRequest(invoice,'invoices'),{companyId:13,invoiceId:118})
 const document=clearForSelection(invoice,{...option(),documentId:218,invoiceId:118},'documents')
 assert.equal(document.documentId,218);assert.equal(document.invoiceId,undefined);assert.deepEqual(previewRequest(document,'documents'),{companyId:13,documentId:218})
 const unlinked=clearForSelection(document,undefined,'none');assert.equal(unlinked.documentId,undefined);assert.equal(unlinked.invoiceId,undefined);assert.equal(unlinked.amount,'')
})
test('preview preserves primary ID and never changes Thu into Chi silently',()=>{
 const form={...initial(),invoiceId:118,transactionType:'INCOME'},p={...preview(),link:{...option(),invoiceId:118,documentId:218}}
 const next=applyPaymentPreview(form,paymentLinkKey(form),p,{})
 assert.equal(next.transactionType,'INCOME');assert.equal(next.documentId,undefined);assert.equal(next.invoiceId,118);assert.equal(next.amount,'60');assert.equal(previewAllows(p,next),false)
 assert.equal(previewAllows(p,{...next,transactionType:'EXPENSE'}),true)
})
test('all client blockers have field-specific messages and server errors distinguish statuses',()=>{
 const errors=paymentFieldErrors(initial(),['CASH'],[]);assert.ok(errors.amount);assert.ok(errors.paymentMethod);assert.ok(errors.paymentConfirmed)
 for(const status of [400,403,409,500])assert.ok(paymentSaveError({response:{status,data:{error:'TEST_ERROR'}}}).includes('HTTP '+status))
 const schema=paymentSaveError({response:{status:409,data:{error:'PAYMENT_SCHEMA_REVIEW_REQUIRED'}}});assert.match(schema,/migration riêng/);assert.match(schema,/PAYMENT_SCHEMA_REVIEW_REQUIRED/)
 assert.match(paymentSaveError({isAxiosError:true}),/giữ nguyên requestKey/)
})

test('untyped categories and unverified direction never autofill even if AI proposes expense',()=>{
 const f=clearForSelection(initial(),option());
 for(const p of [{...preview(),suggestedCategoryType:null},{...preview(),directionSource:'UNKNOWN',link:{...option(),suggestedTransactionType:null}},{...preview(),directionSource:'DOCUMENT_ASSESSMENT_PROPOSAL'}]){
  const next=applyPaymentPreview(f,paymentLinkKey(f),p,{});assert.equal(next.categoryId,undefined);assert.equal(next.transactionType,'EXPENSE');
 }
 const chosen={...f,transactionType:'INCOME'};const next=applyPaymentPreview(chosen,paymentLinkKey(chosen),preview(),{});assert.equal(next.transactionType,'INCOME');assert.equal(next.categoryId,undefined);assert.equal(previewAllows(preview(),next),false);
})

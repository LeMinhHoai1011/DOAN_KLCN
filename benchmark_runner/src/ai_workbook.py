"""Offline three-sheet prediction export and human-reviewed GT; never calls OCR/AI."""
import copy
import hashlib
import json
import re
import uuid
import math
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

from openpyxl import Workbook, load_workbook
from openpyxl.comments import Comment
from openpyxl.styles import Alignment, Font, PatternFill, Protection
from openpyxl.worksheet.datavalidation import DataValidation
from .checkpoint_manager import digest, read, write_new
from .dataset_loader import classify_duplicates
from .field_normalizer import normalize, prediction
from .ground_truth_manager import CORE, HEADER, ITEM, confirm_value, core_fields, latest
from .ai_evaluator import assignment, compare, scores
from .review_workbook import ReviewError, _cell, _evidence, target_file

# key, human label, evaluation group, native schema capability
DOC = [
 ('documentType','Loại chứng từ','Chứng từ',True), ('invoiceNumber','Số chứng từ / hóa đơn','Chứng từ',True),
 ('invoiceSeries','Ký hiệu','Chứng từ',True), ('invoiceDate','Ngày lập','Chứng từ',True),
 ('sellerName','Tên người bán','Chứng từ',True), ('sellerTaxCode','MST người bán','Chứng từ',True),
 ('sellerAddress','Địa chỉ người bán','Chứng từ',True), ('buyerName','Tên người mua','Chứng từ',True),
 ('buyerTaxCode','MST người mua','Chứng từ',True), ('buyerAddress','Địa chỉ người mua','Chứng từ',True),
 ('subtotal','Tiền trước thuế','Tiền',True), ('invoiceDiscountAmount','Chiết khấu toàn hóa đơn','Tiền',False),
 ('allowanceAmount','Các khoản giảm trừ','Tiền',False), ('vatAmount','VAT','Tiền',True),
 ('totalAmount','Tổng thanh toán','Tiền',True), ('paymentMethod','Hình thức thanh toán','Thanh toán',True),
 ('paidAmount','Đã thanh toán (chưa xác minh)','Thanh toán',False),
 ('outstandingAmount','Còn lại (chưa xác minh)','Thanh toán',False),
 ('amountInWords','Tiền bằng chữ','Bổ sung',True), ('lookupCode','Mã tra cứu','Bổ sung',False),
 ('orderNumber','Số đơn hàng','Bổ sung',False), ('notes','Ghi chú chứng từ','Bổ sung',False),
 ('transactionType','Loại thu/chi - đề xuất','Kế toán - đề xuất',True),
 ('debitAccount','Tài khoản Nợ - nghiệp vụ','Kế toán - đề xuất',False),
 ('creditAccount','Tài khoản Có - nghiệp vụ','Kế toán - đề xuất',False),
 ('accountingObject','Đối tượng kế toán - nghiệp vụ','Kế toán - đề xuất',False),
 ('accountingAccountSuggestion','Tài khoản AI gợi ý (không tách Nợ/Có)','Kế toán - đề xuất',True),
 ('accountingCategoryCode','Nhóm nghiệp vụ AI gợi ý','Kế toán - đề xuất',True),
 ('taxAuthorityCode','Mã cơ quan thuế','Bổ sung',True), ('signDate','Ngày ký','Bổ sung',True),
 ('sellerPhone','Điện thoại người bán','Bổ sung',True),
]
GOODS = [
 ('productName','Tên hàng'),('unit','Đơn vị tính'),('quantity','Số lượng'),('unitPrice','Đơn giá'),
 ('amount','Tiền hàng (amount gốc)'),('discountRate','Tỷ lệ chiết khấu'),('discountAmount','Tiền chiết khấu'),
 ('taxRate','Thuế suất'),('taxAmount','Tiền thuế'),('lineTotal','Thành tiền sau thuế/giảm trừ'),
]
LABELS={k:label for k,label,*_ in DOC+GOODS}
ALIASES={'lookupCode':('lookupCode','invoiceCode','searchCode'),'invoiceDiscountAmount':('invoiceDiscountAmount','billDiscountAmount'),
         'orderNumber':('orderNumber','purchaseOrderNumber'),'allowanceAmount':('allowanceAmount',)}
CHECK_HEADERS=['sample_id','kind','row_id','field_label','ai_prediction','gt_candidate','gt_confirmed','field_status',
 'document_status','confirmed_by','reviewed_original','row_action','gt_order','assessment','group','field_key','source','capability',
 'TP','FP','FN','Precision','Recall','F1','AI_minus_GT','absolute_difference','matched_gt_row','gt_version',
 'run_id','review_id','original_filename','file_hash']
EDITABLE={'gt_confirmed','field_status','document_status','confirmed_by','reviewed_original','row_action','gt_order'}
NATIVE_DOC={k for k,_,_,native in DOC if native}|set(sum(CORE.values(),[]))|set(HEADER)
DISPLAY={'ai_prediction':'AI Prediction','gt_candidate':'GT ứng viên','gt_confirmed':'GT xác nhận',
         'field_status':'Trạng thái trường','document_status':'Xác nhận chứng từ','confirmed_by':'Người xác nhận',
         'reviewed_original':'Đã đối chiếu bản gốc','field_key':'Mã trường','field_label':'Tên trường','group':'Nhóm',
         'assessment':'Kết quả kiểm tra','capability':'Hỗ trợ schema','row_action':'Xử lý dòng','gt_order':'Thứ tự GT'}


def display_headers(name,keys):
    return [DISPLAY.get(k,k) if name=='Kiem_tra_GT' else 'GT - '+LABELS[k[3:]] if k.startswith('GT_') and k[3:] in LABELS else LABELS.get(k,k) for k in keys]


def projection(result):
    values,items=prediction(result)
    values.update(transactionType=(result.get('transactionAssessment') or {}).get('type'),
                  accountingAccountSuggestion=result.get('accountingAccount'))
    sources={k:'AI.invoice.'+k if k in (result.get('invoice') or {}) else 'AI.fields/extraFields' for k in values}
    sources.update(documentType='AI.documentType',transactionType='AI.transactionAssessment (đề xuất)',
                   accountingAccountSuggestion='AI.accountingAccount (đề xuất)',accountingCategoryCode='AI.accountingCategoryCode (đề xuất)')
    supported={k:k in NATIVE_DOC or values.get(k) is not None for k,_,_,_ in DOC}
    for key,aliases in ALIASES.items():
        found=[(a,values[a]) for a in aliases if values.get(a) not in (None,'')]
        if found and len({str(v) for _,v in found})==1:
            values[key]=found[0][1];sources[key]='AI.extraFields.'+found[0][0];supported[key]=True
        elif found:
            values[key]=None;sources[key]='CONFLICTING_EXPLICIT_FIELDS: '+','.join(a for a,_ in found);supported[key]=True
    retail=(result.get('invoice') or {}).get('retailDetails') or {}
    discount=retail.get('billDiscount') or {}
    if discount.get('amount') is not None:
        values['invoiceDiscountAmount']=discount['amount'];supported['invoiceDiscountAmount']=True
        sources['invoiceDiscountAmount']='AI.invoice.retailDetails.billDiscount.amount; treatment='+str(discount.get('treatment'))
    elif discount.get('treatment')=='NOT_PRESENT':
        sources['invoiceDiscountAmount']='AI_REPORTS_ABSENT (chưa phải GT)';supported['invoiceDiscountAmount']=True
    projected=[]
    for n,item in enumerate(items,1):
        row=dict(item);source={k:'AI.invoice.items.'+k for k in ITEM};cap={k:k in ITEM or row.get(k) is not None for k,_ in GOODS}
        discounts=[d for d in retail.get('lineDiscounts') or [] if d.get('rowNumber')==n]
        if len(discounts)==1 and discounts[0].get('amount') is not None:
            row['discountAmount']=discounts[0]['amount'];source['discountAmount']='AI.retailDetails.lineDiscounts rowNumber='+str(n);cap['discountAmount']=True
        projected.append({'values':row,'sources':source,'supported':cap})
    return values,sources,supported,projected


def arithmetic(values,keys,signs):
    amounts=[numeric(k,values.get(k)) for k in keys]
    return float(sum(v*s for v,s in zip(amounts,signs))) if all(v is not None for v in amounts) else None


def norm(key,value):
    if key=='discountRate':return normalize('taxRate',value)
    if key=='lineTotal':return normalize('amount',value)
    return normalize(key,value)


def numeric(key,value):
    v=norm(key,value)
    return v if isinstance(v,Decimal) and v.is_finite() else None


def comparison(key,predicted,truth,supported,status):
    if not truth:
        state='RUN_FAILED' if status!='SUCCESS' else 'PREDICTED_UNVERIFIED' if predicted not in (None,'') else 'NOT_RETURNED_NEEDS_REVIEW' if supported else 'NOT_SUPPORTED'
        return {'assessment':state}
    if truth['state']=='UNREADABLE':return {'assessment':'UNREADABLE'}
    if not supported:return {'assessment':'NOT_SUPPORTED'}
    a,b=norm(key,predicted),norm(key,truth.get('value')) if truth['state']=='PRESENT' else None
    match=a==b
    tp=int(a is not None and match);fp=int(a is not None and not match);fn=int(b is not None and not match)
    state='ABSENT_CONFIRMED' if a is None and b is None else 'MISSED_BY_AI' if a is None and b is not None else 'MATCH' if match else 'VALUE_MISMATCH'
    row={'assessment':state,**scores(tp,fp,fn)}
    x,y=numeric(key,predicted),numeric(key,truth.get('value'))
    if x is not None and y is not None:
        row.update(AI_minus_GT=float(x-y),absolute_difference=float(abs(x-y)))
    return row


def item_pairs(predicted,gt):
    def weight(a,b):
        name=b.get('productName',{})
        if name.get('state')!='PRESENT' or norm('productName',a.get('productName'))!=norm('productName',name.get('value')):return 0
        return 1+sum(norm(k,a.get(k))==norm(k,b.get(k,{}).get('value')) for k in ITEM)
    return assignment([[weight(a,b) for b in gt] for a in predicted]) if gt else []


def _row(meta,kind,row_id,key,label,group,value,source,supported,truth,doc_status,order=None,action=None,gt_row=None):
    row=dict(**meta,kind=kind,row_id=row_id,field_key=key,field_label=label,group=group,
        ai_prediction=value,gt_candidate=value,gt_confirmed=truth.get('value') if truth else None,
        field_status=({'PRESENT':'PRESENT','NOT_PRESENT':'ABSENT','UNREADABLE':'UNREADABLE'}[truth['state']] if truth else 'UNCHECKED' if supported else 'NOT_SUPPORTED'),
        document_status=None,confirmed_by=None,reviewed_original=None,row_action=action if key=='productName' else None,gt_order=order if key=='productName' else None,
        source=source or 'NOT_SUPPORTED_IN_CURRENT_SCHEMA',capability='SUPPORTED' if supported else 'NOT_SUPPORTED',matched_gt_row=gt_row)
    row.update(comparison(key,value,truth,supported,doc_status))
    return row


def _totals(rows):
    scored=[r for r in rows if r.get('TP') is not None]
    return scores(*(sum(r[k] for r in scored) for k in ('TP','FP','FN'))) if scored else {k:None for k in scores(0,0,0)}


def _complete(rows):
    supported=[r for r in rows if r['capability']=='SUPPORTED']
    return bool(supported) and all(r.get('TP') is not None or r['assessment']=='UNREADABLE' for r in supported)


def export_ai(run_dir,gt_dir,reports_dir,sample_id=None):
    root=Path(run_dir).resolve();config=read(root/'config.json');cp=read(root/'checkpoint.json')
    samples=classify_duplicates(config['samples'])
    if sample_id:
        selected=[s for s in samples if s['sample_id']==sample_id]
        if not selected:raise ReviewError('SAMPLE_NOT_FOUND')
        if selected[0]['duplicate_of']:raise ReviewError('SKIPPED_DUPLICATE: use '+selected[0]['duplicate_of'])
        samples=selected
    review_id=uuid.uuid4().hex
    records=[];goods=[];checks=[];gt_snapshot=[];evidence_hashes={};missing=Counter();core_rows=[];macro=[]
    group_rows={g:[] for g in ('Chứng từ','Hàng hóa','Tiền','Thanh toán','Kế toán - đề xuất')};group_eligible=Counter()
    for sample in samples:
        sid=sample['sample_id'];alias=sample['duplicate_of'];entry=cp.get(sid,{})
        output,result_hash=_evidence(root,sample,cp) if not alias else ({},None)
        evidence_hashes[sid]=result_hash
        result=(output.get('normalized') or {}).get('result') or output.get('parsed') or {}
        # Failed attempts never become successful extractions, even if some pages returned data.
        if entry.get('status')!='SUCCESS':result={}
        values,sources,supported,items=projection(result)
        gt=latest(gt_dir,sample) if not alias else None
        if gt:gt_snapshot.append(gt)
        meta=dict(run_id=config['run_id'],review_id=review_id,sample_id=sid,original_filename=sample['original_filename'],file_hash=sample['file_hash'])
        base=dict(**meta,status='SKIPPED_DUPLICATE' if alias else entry.get('status','NOT_RUN'),error_code=output.get('errorCode'),duplicate_of=alias,
            model=result.get('model') or config['signature']['api'].get('model'),raw_reference=str(root/entry['relative_result']) if entry.get('relative_result') else None,
            ocr_processing_ms=output.get('ocrMs'),ai_processing_ms=result.get('durationMs'),total_processing_ms=output.get('totalMs'),
            client_seconds=entry.get('seconds'),
            original_file=str(Path(config['dataset_root'])/sample['relative_path']),gt_version=gt['version'] if gt else None)
        for key,_,_,_ in DOC:base[key]=values.get(key)
        base['other_fields_json']=json.dumps({'fields':result.get('fields'),'extraFields':result.get('extraFields')},ensure_ascii=False)
        start=len(checks)
        if not alias:
            specs=list(DOC)
            required=set(CORE.get(values.get('documentType'),sum(CORE.values(),[])))|set((gt or {}).get('scope',[]))
            for key in sorted(required-{k for k,_,_,_ in specs}):specs.append((key,key,'Bổ sung',key in NATIVE_DOC or key in values))
            for key,label,group,native in specs:
                cap=supported.get(key,native)
                truth=(gt or {}).get('fields',{}).get(key)
                row=_row(meta,'DOC','document',key,label,group,values.get(key),sources.get(key),cap,truth,entry.get('status'))
                row['gt_version']=gt['version'] if truth and gt else None
                checks.append(row);missing[row['assessment']]+=1
            gt_items=(gt or {}).get('items',[]);extensions=(gt or {}).get('item_extensions',[])
            pairs=dict(item_pairs([i['values'] for i in items],gt_items));used=set(pairs.values())
            for n,item in enumerate(items):
                b=pairs.get(n);truth=gt_items[b] if b is not None else {k:{'state':'NOT_PRESENT','value':None} for k in ITEM} if gt else {}
                if b is not None and b<len(extensions):truth={**truth,**extensions[b]}
                row_id=f'pred_{n+1:06d}'
                goods.append(dict(**meta,row_id=row_id,**{k:item['values'].get(k) for k,_ in GOODS},
                                  **{'GT_'+k:truth.get(k,{}).get('value') for k,_ in GOODS},
                                  gt_row=b,source_json=json.dumps(item['sources'],ensure_ascii=False)))
                a,q,p=[numeric(k,item['values'].get(k)) for k in ('amount','quantity','unitPrice')]
                goods[-1]['quantity_price_arithmetic_delta']=float(a-q*p) if all(v is not None for v in (a,q,p)) else None
                tax,rate,disc,disc_rate=[numeric(k,item['values'].get(k)) for k in ('taxAmount','taxRate','discountAmount','discountRate')]
                goods[-1]['VAT_arithmetic_delta']=float(tax-a*rate) if all(v is not None for v in (tax,a,rate)) else None
                goods[-1]['discount_arithmetic_delta']=float(disc-q*p*disc_rate) if all(v is not None for v in (disc,q,p,disc_rate)) else None
                goods[-1]['business_check_note']='Chỉ kiểm tra số học; căn cứ trước/sau chiết khấu và thuế cần đối chiếu.'
                for key,label in GOODS:
                    checks.append(_row(meta,'ITEM',row_id,key,label,'Hàng hóa',item['values'].get(key),item['sources'].get(key),
                        item['supported'][key],truth.get(key),entry.get('status'),b+1 if b is not None else n+1,'EXTRA' if gt and b is None else 'KEEP',b))
            for b,truth in enumerate(gt_items):
                if b in used:continue
                extended=extensions[b] if b<len(extensions) else {}
                goods.append(dict(**meta,row_id=f'new_gt_{b+1:06d}',**{k:None for k,_ in GOODS},
                    **{'GT_'+k:({**truth,**extended}).get(k,{}).get('value') for k,_ in GOODS},gt_row=b,source_json='GT_ONLY; AI missing row'))
                for key,label in GOODS:
                    checks.append(_row(meta,'ITEM',f'new_gt_{b+1:06d}',key,label,'Hàng hóa',None,'AI_MISSING_ROW',key in ITEM,
                        ({**truth,**extended}).get(key),entry.get('status'),b+1,'ADD',b))
            own=checks[start:]
            control=next(r for r in own if r['kind']=='DOC' and r['field_key']=='documentType')
            fully_reviewed=bool(gt) and all(r['field_status'] in {'PRESENT','ABSENT','UNREADABLE','NOT_SUPPORTED'} for r in own)
            control.update(document_status='CONFIRMED' if fully_reviewed else 'DRAFT',
                           confirmed_by=gt['confirmed_by']['username'] if gt else None,reviewed_original='YES' if fully_reviewed else 'NO')
            core=set(core_fields(gt)) if gt else set()
            current_core=[r for r in own if r['kind']=='DOC' and r['field_key'] in core]
            if current_core and len(current_core)==len(core) and all(r.get('TP') is not None for r in current_core):
                metrics=_totals(current_core);core_rows.extend(current_core)
                if metrics['F1'] is not None:macro.append(metrics['F1'])
                base['Core_F1']=metrics['F1']
            else:base['Core_F1']=None
            base['All_GT_complete']=bool(gt) and _complete(own)
            base.update({'All_'+k:v for k,v in _totals(own if base['All_GT_complete'] else []).items()})
            scorable=[r for r in own if r.get('TP') is not None]
            base['Field_Accuracy']=sum(r['assessment'] in {'MATCH','ABSENT_CONFIRMED'} for r in scorable)/len(scorable) if scorable and base['All_GT_complete'] else None
            for group in ('Chứng từ','Hàng hóa','Tiền','Thanh toán','Kế toán - đề xuất'):
                scope=[r for r in own if r['group']==group]
                eligible=bool(gt) and _complete(scope)
                base[group+'_GT_complete']=eligible
                base[group+'_F1']=_totals(scope if eligible else [])['F1']
                if eligible:group_rows[group].extend(scope);group_eligible[group]+=1
            for key in ('subtotal','vatAmount','totalAmount','paidAmount','outstandingAmount'):
                truth=(gt or {}).get('fields',{}).get(key)
                base[key+'_AI_minus_GT']=comparison(key,values.get(key),truth,supported.get(key,False),entry.get('status')).get('AI_minus_GT')
        # Arithmetic is a diagnostic, not inferred extraction or proof of settlement.
        for name,keys,signs in [('header_arithmetic_delta',['totalAmount','subtotal','vatAmount'],[1,-1,-1]),
                               ('payment_arithmetic_delta',['paidAmount','outstandingAmount','totalAmount'],[1,1,-1])]:
            base[name]=arithmetic(values,keys,signs)
        line_amounts=[numeric('amount',item['values'].get('amount')) for item in items]
        subtotal=numeric('subtotal',values.get('subtotal'))
        base['item_sum_minus_subtotal']=float(sum(line_amounts)-subtotal) if line_amounts and subtotal is not None and all(v is not None for v in line_amounts) else None
        base['adjusted_header_arithmetic_delta']=arithmetic(values,['subtotal','invoiceDiscountAmount','allowanceAmount','vatAmount','totalAmount'],[1,-1,-1,1,-1])
        base['arithmetic_note']='Chỉ đối chiếu số học; chưa xác minh căn cứ chiết khấu/giảm trừ/thanh toán.'
        records.append(base)
    missing=Counter(r['assessment'] for r in checks)
    summary=dict(run_id=config['run_id'],documents=len(records),success=sum(r['status']=='SUCCESS' for r in records),
        failed=sum(r['status']=='FAILED' for r in records),duplicates=sum(r['status']=='SKIPPED_DUPLICATE' for r in records),
        goods=sum(r['row_id'].startswith('pred_') for r in goods),gt_only_goods=sum(r['row_id'].startswith('new_') for r in goods),review_fields=len(checks),confirmed_gt_documents=len(gt_snapshot),
        Core_Micro=_totals(core_rows),Document_Macro_F1=sum(macro)/len(macro) if macro else None,
        groups={g:_totals(rows) for g,rows in group_rows.items()},eligible_documents_by_group=dict(group_eligible),
        missing_fields=dict(missing),populated_ai_fields=sum(r['ai_prediction'] not in (None,'') for r in checks),
        missing_by_field=dict(Counter(r['field_key'] for r in checks if r['assessment'] in {'NOT_SUPPORTED','NOT_RETURNED_NEEDS_REVIEW','RUN_FAILED'})))
    monetary={k for k,_,group,_ in DOC if group in {'Tiền','Thanh toán'} and k.endswith('Amount')}|{'subtotal','unitPrice','amount','taxAmount','lineTotal','discountAmount'}
    differences=[r['AI_minus_GT'] for r in checks if r['field_key'] in monetary and r.get('AI_minus_GT') is not None]
    summary['money_error']={'count':len(differences),'mean_absolute_error':sum(abs(v) for v in differences)/len(differences) if differences else None,
                            'signed_sum':sum(differences) if differences else None,'max_absolute_error':max(map(abs,differences)) if differences else None}
    for record in records:
        record.update(Run_Core_Micro_F1=summary['Core_Micro']['F1'],Run_Document_Macro_F1=summary['Document_Macro_F1'],
                      Run_Confirmed_GT=summary['confirmed_gt_documents'])
    wb=Workbook();wb.remove(wb.active)
    ai_headers=['original_filename']+[k for k,_,_,_ in DOC]+['status','error_code','sample_id','run_id','review_id','duplicate_of','model']
    ai_headers+=list(dict.fromkeys(k for r in records for k in r if k not in ai_headers))
    goods_headers=['sample_id','row_id']+[k for k,_ in GOODS]+['GT_'+k for k,_ in GOODS]+['quantity_price_arithmetic_delta','VAT_arithmetic_delta','discount_arithmetic_delta','business_check_note','original_filename','run_id','review_id','file_hash','gt_row','source_json']
    tables={'Ai':records,'Hang_hoa':goods,'Kiem_tra_GT':checks}
    headers={'Ai':ai_headers,'Hang_hoa':goods_headers,'Kiem_tra_GT':CHECK_HEADERS}
    snapshot=dict(format='Ai.xlsx/1.0',run_id=config['run_id'],review_id=review_id,config_hash=digest(config),
                  checkpoint_hash=digest(cp),evidence_hashes=evidence_hashes,sample_ids=[s['sample_id'] for s in samples],tables=tables,headers=headers,
                  display_headers={n:display_headers(n,h) for n,h in headers.items()})
    _write_workbook(wb,tables,headers,summary)
    path=target_file(reports_dir,'Ai.xlsx',config['run_id'])
    write_new(root/'ai_reviews'/(review_id+'.json'),snapshot)
    write_new(path.with_suffix('.summary.json'),summary)
    write_new(path.with_suffix('.gt.json'),gt_snapshot)
    with path.open('xb') as stream:wb.save(stream)
    return path,summary


def _write_workbook(wb,tables,headers,summary):
    for name,records in tables.items():
        ws=wb.create_sheet(name);keys=headers[name]
        ws.append(display_headers(name,keys));ws.freeze_panes='B2' if name=='Ai' else 'D2' if name=='Kiem_tra_GT' else 'C2';ws.sheet_view.showGridLines=False
        for record in records:
            ws.append([record.get(k) for k in keys])
        for cells in ws.iter_rows():
            for cell in cells:
                key=keys[cell.column-1]
                editable=name=='Kiem_tra_GT' and key in EDITABLE and cell.row>1
                control=ws.cell(cell.row,keys.index('field_key')+1).value if name=='Kiem_tra_GT' and cell.row>1 else None
                if key in {'document_status','confirmed_by','reviewed_original'} and control!='documentType':editable=False
                if key in {'row_action','gt_order'} and control!='productName':editable=False
                cell.protection=Protection(locked=not editable)
                cell.font=Font(name='Arial',size=10,color='174EA6' if editable else '202124')
                cell.alignment=Alignment(vertical='top',wrap_text=True)
                if editable:cell.fill=PatternFill('solid',fgColor='FFF2CC')
                if isinstance(cell.value,str):
                    if len(cell.value)>32767:raise ReviewError('CELL_TOO_LONG: '+name+'!'+cell.coordinate)
                    cell.data_type='s'
                if key in {'gt_candidate','gt_confirmed','invoiceNumber','invoiceSeries','sellerTaxCode','buyerTaxCode','lookupCode'}:cell.number_format='@'
                if key in {'F1','Precision','Recall','Core_F1','Field_Accuracy'} or key.endswith(('_F1','_Precision','_Recall')):cell.number_format='0.00%'
                if key in {'subtotal','vatAmount','totalAmount','unitPrice','amount','taxAmount','AI_minus_GT','absolute_difference'}:cell.number_format='#,##0.00'
                if key in {'taxRate','discountRate'}:cell.number_format='0.00%'
        for cell in ws[1]:
            key=keys[cell.column-1];cell.comment=Comment((key+'\n'+LABELS.get(key,key)+'\n'+('Ô nhập GT; không dùng ứng viên AI làm đáp án khi chưa đối chiếu.' if key in EDITABLE else 'Dữ liệu gốc / tính toán chỉ đọc.')),'Benchmark')
            cell.font=Font(name='Arial',bold=True,color='FFFFFF',size=10);cell.fill=PatternFill('solid',fgColor='183B56')
        ws.row_dimensions[1].height=42
        for n in range(2,ws.max_row+1):ws.row_dimensions[n].height=110 if name=='Ai' else 60 if name=='Hang_hoa' else 72
        for i,key in enumerate(keys,1):
            ws.column_dimensions[ws.cell(1,i).column_letter].width=42 if key in {'original_filename','productName','field_label','ai_prediction','gt_candidate','gt_confirmed','source','sellerName','buyerName','sellerAddress','buyerAddress'} else 14 if key in {'unit','quantity','row_id','kind','TP','FP','FN'} else 24
        if name=='Ai':
            for n,record in enumerate(records,2):ws.cell(n,keys.index('original_filename')+1).hyperlink=Path(record['original_file']).as_uri()
        ws.auto_filter.ref=ws.dimensions;ws.protection.sheet=True;ws.protection.autoFilter=False;ws.protection.insertRows=False
        if name=='Kiem_tra_GT':
            for key,options in {'field_status':'UNCHECKED,PRESENT,ABSENT,UNREADABLE,NOT_SUPPORTED','document_status':'DRAFT,CONFIRMED',
                                'reviewed_original':'NO,YES','row_action':'KEEP,EXTRA,ADD'}.items():
                dv=DataValidation(type='list',formula1='"'+options+'"');dv.showErrorMessage=True;ws.add_data_validation(dv)
                letter=ws.cell(1,keys.index(key)+1).column_letter;dv.add(letter+'2:'+letter+'1048576')
        if name=='Ai':ws['A1'].comment=Comment(json.dumps(summary,ensure_ascii=False,indent=2),'Tổng hợp benchmark')


def _key(name,row):
    return (row['sample_id'],) if name=='Ai' else (row['sample_id'],row['row_id']) if name=='Hang_hoa' else (row['sample_id'],row['kind'],row['row_id'],row['field_key'])


def _indexed(name,rows):
    indexed={}
    for row in rows:
        key=_key(name,row)
        if key in indexed:raise ReviewError('DUPLICATE_ROW: '+name+' '+str(key))
        indexed[key]=row
    return indexed


def _same_cell(key,a,b):
    if a==b or (a in (None,'') and b in (None,'')):return True
    derived=key in {'F1','Precision','Recall','Field_Accuracy','AI_minus_GT','absolute_difference','client_seconds'} or key.endswith(('_F1','_Precision','_Recall','_delta','_AI_minus_GT'))
    # Excel stores only ~15 significant digits. Allow serialization rounding for derived metrics only.
    return derived and isinstance(a,(int,float)) and isinstance(b,(int,float)) and math.isclose(a,b,rel_tol=1e-14,abs_tol=1e-12)


def _import_cell(key,value,state):
    return _cell('taxRate' if key=='discountRate' else 'amount' if key=='lineTotal' else key,value,state,'Kiem_tra_GT '+key)


def import_ai(excel,results_dir,gt_dir,identity):
    if not identity.get('id') or not identity.get('username'):raise ReviewError('AUTHENTICATED_IDENTITY_REQUIRED')
    from .review_workbook import resolve_run
    wb=load_workbook(excel,data_only=False)
    try:
        if wb.sheetnames!=['Ai','Hang_hoa','Kiem_tra_GT']:raise ReviewError('EXACTLY_THREE_SHEETS_REQUIRED')
        ai_keys=[c.value for c in wb['Ai'][1]]
        if not all(k in ai_keys for k in ('run_id','review_id','sample_id')) or wb['Ai'].max_row<2:raise ReviewError('MISSING_AI_METADATA')
        first=dict(zip(ai_keys,[c.value for c in wb['Ai'][2]]));rid=first['review_id']
        if not isinstance(rid,str) or not re.fullmatch('[a-f0-9]{32}',rid):raise ReviewError('INVALID_REVIEW_ID')
        root=resolve_run(results_dir,first['run_id']);snapshot=read(root/'ai_reviews'/(rid+'.json'))
        config=read(root/'config.json');cp=read(root/'checkpoint.json')
        if digest(config)!=snapshot['config_hash'] or digest(cp)!=snapshot['checkpoint_hash']:raise ReviewError('RUN_CHANGED_REEXPORT_AI')
        samples={s['sample_id']:s for s in classify_duplicates(config['samples'])}
        for sid,expected in snapshot['evidence_hashes'].items():
            if not samples[sid]['duplicate_of'] and _evidence(root,samples[sid],cp)[1]!=expected:raise ReviewError('ORIGINAL_AI_CHANGED: '+sid)
            file=(Path(config['dataset_root'])/samples[sid]['relative_path']).resolve()
            if not file.is_relative_to(Path(config['dataset_root']).resolve()):raise ReviewError('INVALID_SOURCE_PATH')
            with file.open('rb') as stream:actual=hashlib.file_digest(stream,'sha256').hexdigest()
            if actual!=samples[sid]['file_hash']:raise ReviewError('ORIGINAL_FILE_HASH_CHANGED: '+sid)
        tables={}
        for name in wb.sheetnames:
            actual_headers=[c.value for c in wb[name][1]]
            keys=snapshot['headers'][name]
            if actual_headers!=snapshot.get('display_headers',snapshot['headers'])[name]:raise ReviewError('INVALID_HEADERS: '+name)
            records=[]
            for cells in wb[name].iter_rows(min_row=2):
                if all(c.value is None for c in cells):continue
                if any(c.data_type in {'f','e'} for c in cells):raise ReviewError('FORMULA_OR_ERROR_NOT_ALLOWED: '+name)
                records.append(dict(zip(keys,[c.value for c in cells])))
            original=_indexed(name,snapshot['tables'][name]);edited=_indexed(name,records)
            if set(original)-set(edited):raise ReviewError('ORIGINAL_ROW_REMOVED: '+name)
            for key,row in edited.items():
                if key in original:
                    for field in keys:
                        if name=='Kiem_tra_GT' and field in EDITABLE:
                            if field in {'document_status','confirmed_by','reviewed_original'} and row['field_key']!='documentType' and row[field] not in (None,''):raise ReviewError('DOCUMENT_CONTROL_ONLY_ON_DOCUMENT_TYPE')
                            if field in {'row_action','gt_order'} and row['field_key']!='productName' and row[field] not in (None,''):raise ReviewError('ITEM_CONTROL_ONLY_ON_PRODUCT_NAME')
                            continue
                        a,b=row[field],original[key].get(field)
                        if not _same_cell(field,a,b):raise ReviewError('IMMUTABLE_VALUE_CHANGED: '+name+' '+field)
                else:
                    if name!='Kiem_tra_GT':raise ReviewError('ADD_GT_ROWS_ONLY_IN_KIEM_TRA_GT')
                    sid=row['sample_id']
                    if sid not in snapshot['sample_ids'] or samples[sid]['duplicate_of']:raise ReviewError('UNKNOWN_OR_DUPLICATE_SAMPLE')
                    sample=samples[sid]
                    if row['run_id']!=config['run_id'] or row['review_id']!=rid or row['file_hash']!=sample['file_hash'] or row['original_filename']!=sample['original_filename']:raise ReviewError('IDENTITY_MISMATCH')
                    if row['kind']=='ITEM':
                        if not isinstance(row['row_id'],str) or not re.fullmatch('new_[A-Za-z0-9_-]+',row['row_id']) or row['field_key'] not in dict(GOODS):raise ReviewError('NEW_ITEM_REQUIRES_NEW_ID_AND_KNOWN_FIELD')
                    elif row['kind']=='DOC':
                        if row['row_id']!='document' or not re.fullmatch('[A-Za-z][A-Za-z0-9_]{0,119}',str(row['field_key'])):raise ReviewError('INVALID_NEW_FIELD')
                    else:raise ReviewError('INVALID_KIND')
                    if any(row.get(k) not in (None,'') for k in ('ai_prediction','gt_candidate','TP','FP','FN','Precision','Recall','F1','AI_minus_GT','absolute_difference','matched_gt_row','gt_version')):raise ReviewError('NEW_GT_ROW_MUST_HAVE_BLANK_AI_AND_SCORES')
                    if row['source'] not in (None,'','MANUAL_ADDITION'):raise ReviewError('NEW_GT_SOURCE_MUST_BE_MANUAL')
            tables[name]=records
    finally:wb.close()
    with Path(excel).open('rb') as stream:excel_hash=hashlib.file_digest(stream,'sha256').hexdigest()
    outcomes=[];candidates=[]
    for record in tables['Ai']:
        sid=record['sample_id'];sample=samples[sid]
        if sample['duplicate_of']:
            outcomes.append(dict(sample_id=sid,status='SKIPPED_DUPLICATE'));continue
        own=[r for r in tables['Kiem_tra_GT'] if r['sample_id']==sid]
        control=next(r for r in own if r['kind']=='DOC' and r['field_key']=='documentType')
        if control['document_status']=='DRAFT':
            outcomes.append(dict(sample_id=sid,status='UNCONFIRMED'));continue
        try:
            if control['document_status']!='CONFIRMED' or control['reviewed_original']!='YES':raise ReviewError('DOCUMENT_NOT_FULLY_REVIEWED')
            if control['confirmed_by']!=identity['username']:raise ReviewError('CONFIRMER_MISMATCH')
            fields={}
            for row in own:
                if row['kind']!='DOC':continue
                if row['field_status']=='NOT_SUPPORTED':
                    if row['capability']=='SUPPORTED' or row['gt_confirmed'] not in (None,''):raise ReviewError('CANNOT_SKIP_SUPPORTED_FIELD: '+row['field_key'])
                    continue
                fields[row['field_key']]=_import_cell(row['field_key'],row['gt_confirmed'],row['field_status'])
            doc_type=fields.get('documentType',{}).get('value')
            if doc_type not in CORE:raise ReviewError('INVALID_DOCUMENT_TYPE')
            item_groups={}
            for row in own:
                if row['kind']=='ITEM':item_groups.setdefault(row['row_id'],{})[row['field_key']]=row
            entries=[];seen_orders=set()
            for row_id,cells in item_groups.items():
                if set(cells)!=set(dict(GOODS)):raise ReviewError('ITEM_REQUIRES_ALL_TEN_CELLS: '+row_id)
                item_control=cells['productName'];action=item_control['row_action']
                if action=='EXTRA':
                    if not row_id.startswith('pred_') or any(r['gt_confirmed'] not in (None,'') for r in cells.values()):raise ReviewError('EXTRA_ROW_MUST_HAVE_BLANK_CONFIRMED_VALUES')
                    continue
                if action!=('ADD' if row_id.startswith('new_') else 'KEEP'):raise ReviewError('INVALID_ITEM_ACTION: '+row_id)
                order=item_control['gt_order']
                if not isinstance(order,int) or isinstance(order,bool) or order<1 or order in seen_orders:raise ReviewError('INVALID_OR_DUPLICATE_GT_ORDER')
                seen_orders.add(order);values={}
                for key,row in cells.items():
                    if row['field_status']=='NOT_SUPPORTED':
                        if key in ITEM or row['capability']=='SUPPORTED' or row['gt_confirmed'] not in (None,''):raise ReviewError('CANNOT_SKIP_SUPPORTED_ITEM_CELL')
                        continue
                    values[key]=_import_cell(key,row['gt_confirmed'],row['field_status'])
                if all(c['state']=='NOT_PRESENT' for c in values.values()):raise ReviewError('EMPTY_GT_ITEM')
                entries.append((order,values))
            entries.sort(key=lambda x:x[0])
            previous=latest(gt_dir,sample)
            value=dict(schema_version='1.0',**{k:sample[k] for k in ('sample_id','original_filename','file_hash')},
                document_type=doc_type,scope=list(fields),fields=fields,items_reviewed=True,
                items=[{k:cells[k] for k in ITEM} for _,cells in entries],
                item_extensions=[{k:v for k,v in cells.items() if k not in ITEM} for _,cells in entries],
                ocr=copy.deepcopy(previous['ocr']) if previous else {'checked':False,'text':''},
                review_source={'run_id':config['run_id'],'review_id':rid,'excel_sha256':excel_hash,'format':'Ai.xlsx/1.0'})
            # Validate all candidates before committing any new versions.
            from .ground_truth_manager import validate
            validate(value);candidates.append(value)
        except (ValueError,KeyError,TypeError) as error:outcomes.append(dict(sample_id=sid,status='INVALID',error=str(error)))
    for value in candidates:
        old=latest(gt_dir,value)
        if old and old['confirmed_by']==identity and all(old.get(k)==value.get(k) for k in ('fields','items','item_extensions','ocr','review_source')):
            outcomes.append(dict(sample_id=value['sample_id'],status='UNCHANGED',version=old['version']));continue
        path=confirm_value(value,gt_dir,identity)
        outcomes.append(dict(sample_id=value['sample_id'],status='IMPORTED',path=str(path),version=read(path)['version']))
    return {'run_id':config['run_id'],'review_id':rid,'samples':outcomes}


def verify_ai_export(run_dir,excel):
    """Verify a fresh export against its sidecar snapshot and every original JSON evidence file."""
    root=Path(run_dir).resolve();wb=load_workbook(excel,data_only=False)
    try:
        if wb.sheetnames!=['Ai','Hang_hoa','Kiem_tra_GT']:raise ReviewError('INVALID_SHEETS')
        keys=[c.value for c in wb['Ai'][1]];first=dict(zip(keys,[c.value for c in wb['Ai'][2]]))
        snapshot=read(root/'ai_reviews'/(first['review_id']+'.json'))
        config=read(root/'config.json');cp=read(root/'checkpoint.json')
        samples={s['sample_id']:s for s in config['samples']}
        for sid,expected in snapshot['evidence_hashes'].items():
            if expected is not None and _evidence(root,samples[sid],cp)[1]!=expected:raise ReviewError('ORIGINAL_AI_CHANGED')
        counts={}
        checked=0
        for name,headers in snapshot['headers'].items():
            ws=wb[name]
            if [c.value for c in ws[1]]!=snapshot.get('display_headers',snapshot['headers'])[name]:raise ReviewError('INVALID_HEADERS')
            expected=_indexed(name,snapshot['tables'][name]);actual={}
            for cells in ws.iter_rows(min_row=2):
                if any(c.data_type in {'f','e'} for c in cells):raise ReviewError('UNEXPECTED_FORMULA_OR_ERROR')
                row=dict(zip(headers,[c.value for c in cells]));actual[_key(name,row)]=row
            if set(actual)!=set(expected):raise ReviewError('ROW_COUNT_OR_KEY_MISMATCH: '+name)
            for key,row in actual.items():
                for field,value in row.items():
                    if not _same_cell(field,value,expected[key].get(field)):raise ReviewError('EXPORTED_VALUE_MISMATCH: '+name+' '+field)
                    checked+=1
            counts[name]=len(actual)
        return {'status':'VERIFIED','run_id':config['run_id'],'excel':str(Path(excel).resolve()),'rows':counts,
                'cells_compared':checked,'evidence_files_checked':sum(v is not None for v in snapshot['evidence_hashes'].values()),
                'ground_truth_auto_confirmed':False}
    finally:wb.close()

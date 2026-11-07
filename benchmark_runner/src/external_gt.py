"""Read independently maintained Vietnamese GT workbooks without modifying predictions."""
import hashlib
from pathlib import Path
from openpyxl import load_workbook
from .checkpoint_manager import read
from .ground_truth_manager import CORE, HEADER, ITEM, validate, confirm_value, latest
from .review_workbook import ReviewError, _cell

DOC = dict(zip(
    ['Loại chứng từ','Số hóa đơn','Ký hiệu','Ngày lập','Tên công ty bán','MST bên bán','Địa chỉ bên bán','Tên bên mua','MST bên mua','Địa chỉ bên mua','Tiền trước thuế','Tiền VAT','Tổng thanh toán','Hình thức TT','Tiền bằng chữ'],
    ['documentType','invoiceNumber','invoiceSeries','invoiceDate','sellerName','sellerTaxCode','sellerAddress','buyerName','buyerTaxCode','buyerAddress','subtotal','vatAmount','totalAmount','paymentMethod','amountInWords']))
GOODS = {'Tên hàng':'productName','Số lượng':'quantity','Đơn vị':'unit','Đơn giá':'unitPrice','Thuế suất':'taxRate','Tiền thuế':'taxAmount','Thành tiền':'amount'}

def import_external(excel, run_dir, gt_dir, identity=None, blank_policy='exclude', reviewed=False):
    root = Path(run_dir)
    config = read(root/'config.json')
    samples = {s['sample_id']:s for s in config['samples']}
    source = Path(excel)
    sha = hashlib.sha256(source.read_bytes()).hexdigest()
    wb = load_workbook(source, read_only=True, data_only=False)
    def rows(sheet, required):
        if sheet not in wb.sheetnames: raise ReviewError('MISSING_SHEET: '+sheet)
        iterator = wb[sheet].iter_rows()
        headers = [c.value for c in next(iterator)]
        if len(headers)!=len(set(headers)) or not set(required).issubset(headers):
            raise ReviewError('INVALID_HEADERS: '+sheet)
        result=[]
        for cells in iterator:
            if all(c.value is None for c in cells): continue
            if any(c.data_type in {'f','e'} for c in cells): raise ReviewError('FORMULA_OR_ERROR: '+sheet)
            result.append(dict(zip(headers,[c.value for c in cells])))
        return result
    try:
        docs=rows('GT_Chung_tu',['Mã mẫu','Tên file','Loại chứng từ','Trạng thái GT','Người xác nhận'])
        goods=rows('GT_Hang_hoa',['Mã mẫu','Tên file','STT',*GOODS])
    finally: wb.close()
    ids=[d['Mã mẫu'] for d in docs]
    if len(ids)!=len(set(ids)): raise ReviewError('DUPLICATE_DOCUMENT_ID')
    if any(g['Mã mẫu'] not in ids for g in goods): raise ReviewError('ORPHAN_ITEM')
    candidates=[]; outcomes=[]
    def cell(key,value,excluded):
        if value is None or value=='':
            excluded.append(key)
            return {'state':'NOT_PRESENT' if blank_policy=='absent' else 'UNREADABLE','value':None}
        return _cell(key,value,'PRESENT','external GT')
    for doc in docs:
        sid=doc['Mã mẫu']; excluded=[]
        if sid not in samples: raise ReviewError('UNKNOWN_SAMPLE: '+str(sid))
        sample=samples[sid]
        if doc['Tên file']!=sample['original_filename']: raise ReviewError('FILENAME_MISMATCH: '+sid)
        if sample.get('duplicate_of'): raise ReviewError('DUPLICATE_SAMPLE: '+sid)
        if doc['Trạng thái GT']!='CONFIRMED':
            outcomes.append({'sample_id':sid,'status':'UNCONFIRMED'}); continue
        kind=doc['Loại chứng từ']
        if kind not in CORE:
            outcomes.append({'sample_id':sid,'status':'INVALID','error':'INVALID_DOCUMENT_TYPE: fill Loại chứng từ'});continue
        original=(Path(config['dataset_root'])/sample['relative_path']).resolve()
        if not original.is_relative_to(Path(config['dataset_root']).resolve()): raise ReviewError('INVALID_SOURCE_PATH')
        if hashlib.sha256(original.read_bytes()).hexdigest()!=sample['file_hash']: raise ReviewError('FILE_HASH_MISMATCH: '+sid)
        fields={key:cell(key,doc[label],excluded) for label,key in DOC.items() if label in doc}
        required=set(CORE[kind])|{'documentType','accountingCategoryCode'}
        if kind.endswith('_INVOICE'): required.update(HEADER)
        # Unlabelled columns are always excluded, never asserted absent.
        for key in required-set(fields):
            fields[key]={'state':'UNREADABLE','value':None};excluded.append(key)
        items=[]; orders=set()
        for row in sorted([r for r in goods if r['Mã mẫu']==sid],key=lambda r:str(r['STT'])):
            if row['Tên file']!=sample['original_filename']: raise ReviewError('ITEM_FILENAME_MISMATCH: '+sid)
            order=row['STT']
            if isinstance(order,bool) or not isinstance(order,int) or order<1 or order in orders: raise ReviewError('INVALID_ITEM_ORDER: '+sid)
            orders.add(order)
            if row.get('Thao tác dòng','KEEP')!='KEEP': raise ReviewError('EXTERNAL_ITEMS_MUST_BE_KEEP: '+sid)
            item={key:cell(key,row[label],excluded) for label,key in GOODS.items()}
            if item['productName']['state']!='PRESENT': raise ReviewError('ITEM_NAME_REQUIRED: '+sid)
            items.append((order,item))
        value=dict(schema_version='1.0',sample_id=sid,original_filename=sample['original_filename'],file_hash=sample['file_hash'],
            document_type=kind,scope=sorted(fields),fields=fields,items_reviewed=True,items=[v for _,v in sorted(items)],
            ocr={'checked':False,'text':''},review_source={'format':'external-gt/1','excel_sha256':sha,'run_id':config['run_id'],
            'blank_policy':blank_policy,'excluded_or_blank_fields':sorted(set(excluded)),
            'note':'UNREADABLE also encodes unlabelled cells excluded from scoring; source discount columns are not mapped.'})
        validate(value)
        if identity and doc['Người xác nhận']!=identity['username']: raise ReviewError('CONFIRMER_MISMATCH: '+sid)
        previous=latest(gt_dir,sample)
        if previous: value['ocr']=previous.get('ocr',value['ocr'])
        candidates.append((value,previous))
    if identity and not reviewed: raise ReviewError('REVIEWED_ORIGINAL_REQUIRED')
    for value,previous in candidates:
        status='READY'
        if identity:
            if previous and all(previous.get(k)==value.get(k) for k in ('fields','items','review_source')): status='UNCHANGED'
            else: confirm_value(value,gt_dir,identity);status='IMPORTED'
        outcomes.append({'sample_id':value['sample_id'],'status':status,'excluded_or_blank_fields':value['review_source']['excluded_or_blank_fields']})
    return {'dry_run':identity is None,'documents':len(docs),'items':len(goods),'missing_samples':sorted(set(samples)-set(ids)),
        'warning':'Unlabelled cells excluded using UNREADABLE; Core F1 may be unavailable. Discount columns are not imported.', 'samples':outcomes}

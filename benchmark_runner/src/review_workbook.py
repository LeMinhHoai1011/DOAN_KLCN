"""V1.1 accountant review: immutable predictions, explicit human values, verified import."""
import hashlib
import json
import re
import uuid
import math
from zipfile import BadZipFile
from datetime import date, datetime, timezone
from decimal import Decimal
from pathlib import Path

from openpyxl import Workbook, load_workbook
from openpyxl.styles import Alignment, Font, PatternFill, Protection
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.utils.exceptions import InvalidFileException
from .checkpoint_manager import digest, read, write_new
from .field_normalizer import MONEY, normalize, prediction
from .ground_truth_manager import CORE, HEADER, ITEM, core_fields, confirm_value, latest, validate
from .dataset_loader import classify_duplicates

COMMON = ['run_id', 'sample_id', 'original_filename', 'file_hash']
HEADERS = {
    'Documents_Review': COMMON + ['ai_document_type', 'document_type', 'confirmation_status', 'confirmed_by',
                                 'reviewed_original', 'items_reviewed', 'notes', 'original_file'],
    'AI_Fields_Review': COMMON + ['field_name', 'field_group', 'ai_value', 'confirmed_value', 'field_status'],
    'AI_Items_Review': COMMON + ['item_id', 'row_action', 'gt_order'] +
                       [column for key in ITEM for column in ('ai_'+key, 'confirmed_'+key, key+'_status')],
    'OCR_Review': COMMON + ['part', 'ocr_source', 'ocr_text', 'confirmed_text', 'gt_checked'],
    'Run_Info': ['template_version', 'review_id'] + COMMON +
                ['model', 'model_digest', 'run_started_at', 'exported_at', 'processing_seconds', 'result_hash'],
}
EDITABLE = {
    'Documents_Review': {'document_type', 'confirmation_status', 'confirmed_by', 'reviewed_original', 'items_reviewed', 'notes'},
    'AI_Fields_Review': {'confirmed_value', 'field_status'},
    'AI_Items_Review': {'row_action', 'gt_order'} | {'confirmed_'+k for k in ITEM} | {k+'_status' for k in ITEM},
    'OCR_Review': {'confirmed_text', 'gt_checked'},
    'Run_Info': set(),
    'Duplicates': set(),
}
DUPLICATE_HEADERS=COMMON+['relative_path','duplicate_of','status']


class ReviewError(ValueError):
    pass


def target_file(directory, name, run_id):
    """Keep requested filename, archive subsequent outputs in a new subdirectory."""
    root = Path(directory)
    root.mkdir(parents=True, exist_ok=True)
    path = root/name
    if path.exists() or path.with_suffix('.gt.json').exists():
        root = root/(run_id+'-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
        root.mkdir()
        path = root/name
    return path


def resolve_run(results, name='latest'):
    root = Path(results).resolve()
    if name == 'latest':
        candidates = [(read(p)['started_at'], p.parent) for p in root.glob('*/config.json')]
        if not candidates:
            raise ReviewError('NO_RUNS: no results/<run_id>/config.json')
        return max(candidates)[1]
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,99}', name):
        raise ReviewError('UNSAFE_RUN_ID')
    path = root/name
    if not (path/'config.json').is_file():
        raise ReviewError('RUN_NOT_FOUND: '+name)
    return path


def _evidence(root, sample, checkpoint):
    entry = checkpoint.get(sample['sample_id'])
    if not entry:
        return {}, None
    path = (root/entry['relative_result']).resolve()
    if not path.is_relative_to(root.resolve()):
        raise ReviewError('EVIDENCE_OUTSIDE_RUN')
    value = read(path)
    return value, digest(value)


def _common(config, sample):
    return {'run_id': config['run_id'], **{k: sample[k] for k in COMMON[1:]}}


def _blank(value):
    return value is None or value == ''


def _readonly_equal(key,a,b):
    if a==b or (_blank(a) and _blank(b)):return True
    if key=='ocr_text' and isinstance(a,str) and isinstance(b,str) and '\r' in b:
        # Legacy XLSX/XML writers represented Windows CRLF as either LF or two LF characters.
        # Permit only these exact source-derived views, not arbitrary whitespace edits.
        return a in {b.replace('\r\n','\n'),b.replace('\r','\n')}
    # Excel serializes numeric timing metadata to ~15 significant digits; predictions stay exact.
    return key=='processing_seconds' and isinstance(a,(int,float)) and isinstance(b,(int,float)) and math.isclose(a,b,rel_tol=1e-14,abs_tol=1e-12)


def export_review(run_dir, reports_dir):
    root = Path(run_dir).resolve()
    config = read(root/'config.json')
    checkpoint = read(root/'checkpoint.json')
    if set(checkpoint) != {s['sample_id'] for s in config['samples']}:
        raise ReviewError('RUN_INCOMPLETE: finish/resume all samples before exporting review')
    review_id = uuid.uuid4().hex
    stamp = datetime.now(timezone.utc).isoformat()
    tables = {name: [] for name in HEADERS}
    samples=classify_duplicates(config['samples'])
    duplicates=[s for s in samples if s['duplicate_of']]
    headers_by_sheet=dict(HEADERS)
    if duplicates:
        headers_by_sheet['Duplicates']=DUPLICATE_HEADERS
        tables['Duplicates']=[dict(**_common(config,s),relative_path=s['relative_path'],
            duplicate_of=s['duplicate_of'],status='SKIPPED_DUPLICATE') for s in duplicates]
    for sample in samples:
        if sample['duplicate_of']:
            continue
        meta = _common(config, sample)
        output, result_hash = _evidence(root, sample, checkpoint)
        result = (output.get('normalized') or {}).get('result') or {}
        values, items = prediction(result)
        document_type = result.get('documentType')
        core = set(core_fields({'document_type':document_type,'fields':{'receiptKind':{'value':values.get('receiptKind')}}})) if document_type in CORE else set()
        scope = set(CORE.get(document_type, sum(CORE.values(), []))) | set(values) | {'documentType', 'accountingCategoryCode'}
        if document_type in {'VAT_INVOICE', 'RETAIL_INVOICE'}:
            scope |= set(HEADER)
        if document_type == 'RECEIPT':
            scope |= {'payerName', 'totalAmount', 'currency'}  # supports receiptKind correction to PAYMENT
        tables['Documents_Review'].append(dict(**meta, ai_document_type=document_type,
            document_type=document_type, confirmation_status='DRAFT', confirmed_by=None,
            reviewed_original='NO', items_reviewed='NO', notes=None,
            original_file=str(Path(config['dataset_root'])/sample['relative_path'])))
        for key in sorted(scope):
            group = 'Core' if key in core else 'Optional' if key in HEADER or key in {'documentType','accountingCategoryCode'} else 'Extended'
            tables['AI_Fields_Review'].append(dict(**meta, field_name=key, field_group=group,
                ai_value=values.get(key), confirmed_value=None, field_status='UNCHECKED'))
        for n, item in enumerate(items, 1):
            row = dict(**meta, item_id=f'pred_{n:06d}', row_action='KEEP', gt_order=n)
            for key in ITEM:
                row.update({ 'ai_'+key: item.get(key), 'confirmed_'+key: None, key+'_status': 'UNCHECKED'})
            tables['AI_Items_Review'].append(row)
        ocr = output.get('ocr') or {}
        raw = ocr.get('rawText') or ''
        source = '+'.join(sorted({'PDF_TEXT_LAYER' if p.get('language') == 'pdf-text-layer' else 'TESSERACT' for p in ocr.get('pages', [])}))
        for part, start in enumerate(range(0, max(1,len(raw)), 30000), 1):
            tables['OCR_Review'].append(dict(**meta, part=part, ocr_source=source,
                ocr_text=raw[start:start+30000], confirmed_text=None, gt_checked='NO'))
        api = config['signature']['api']
        tables['Run_Info'].append(dict(template_version='1.1', review_id=review_id, **meta,
            model=api.get('model'), model_digest=api.get('modelDigest'), run_started_at=config['started_at'],
            exported_at=stamp, processing_seconds=checkpoint[sample['sample_id']]['seconds'], result_hash=result_hash))
    snapshot = dict(review_id=review_id, run_id=config['run_id'], config_hash=digest(config),
                    checkpoint_hash=digest(checkpoint), tables=tables)
    wb = Workbook(); wb.remove(wb.active)
    for name, headers in headers_by_sheet.items():
        ws = wb.create_sheet(name)
        ws.append(headers)
        ws.freeze_panes='C2'; ws.sheet_view.showGridLines=False
        for record in tables[name]:
            ws.append([record.get(k) for k in headers])
        for cells in ws.iter_rows():
            for cell in cells:
                key = headers[cell.column-1]
                editable = key in EDITABLE[name] and cell.row > 1
                cell.protection=Protection(locked=not editable)
                cell.font=Font(name='Arial', size=10, color='174EA6' if editable else '202124')
                cell.alignment=Alignment(vertical='top', wrap_text=True)
                if isinstance(cell.value, str):
                    if len(cell.value)>32767:
                        raise ReviewError(f'CELL_TOO_LONG: {name}!{cell.coordinate}; original JSON is preserved')
                    cell.data_type='s'  # formula injection protection
                if editable:
                    cell.fill=PatternFill('solid',fgColor='FFF2CC')
                if key in {'confirmed_value'} or key.startswith('confirmed_'):
                    cell.number_format='@'  # preserve leading zeros; numeric input is still accepted for amounts
        ws.auto_filter.ref=ws.dimensions
        ws.protection.sheet=True; ws.protection.autoFilter=False
        # No password: additions are allowed after Unprotect Sheet; import checks evidence regardless of protection.
        ws.protection.insertRows=False
        ws.row_dimensions[1].height=34
        for cell in ws[1]:
            cell.font=Font(name='Arial',size=10,bold=True,color='FFFFFF')
            cell.fill=PatternFill('solid',fgColor='183B56')
        for column, key in enumerate(headers,1):
            ws.column_dimensions[ws.cell(1,column).column_letter].width=(60 if 'text' in key else 36 if key.endswith('value') or key in {'original_filename','original_file','notes'} else 25)
            enum = None
            if key == 'confirmation_status': enum='DRAFT,CONFIRMED'
            elif key.endswith('_status'): enum='UNCHECKED,PRESENT,ABSENT,UNREADABLE'
            elif key in {'reviewed_original','items_reviewed','gt_checked'}: enum='NO,YES'
            elif key=='row_action': enum='KEEP,EXTRA,ADD'
            elif key=='document_type': enum=','.join(CORE)
            if enum:
                validation=DataValidation(type='list',formula1='"'+enum+'"',allow_blank=False)
                validation.errorTitle='Giá trị không hợp lệ';validation.error='Chọn giá trị trong danh sách.';validation.showErrorMessage=True
                ws.add_data_validation(validation)
                letter=ws.cell(1,column).column_letter;validation.add(f'{letter}2:{letter}1048576')
    path = target_file(reports_dir,'initial_results.xlsx',config['run_id'])
    write_new(root/'reviews'/(review_id+'.json'),snapshot)
    with path.open('xb') as stream:
        wb.save(stream)
    with (root/'review_exports.jsonl').open('a',encoding='utf-8') as log:
        log.write(json.dumps({'review_id':review_id,'path':str(path),'exported_at':stamp})+'\n')
    return path


def _read_tables(workbook):
    if set(workbook.sheetnames) not in (set(HEADERS),set(HEADERS)|{'Duplicates'}):
        raise ReviewError('INVALID_SHEETS: expected '+', '.join(HEADERS))
    tables={}
    headers_by_sheet=dict(HEADERS)
    if 'Duplicates' in workbook.sheetnames:headers_by_sheet['Duplicates']=DUPLICATE_HEADERS
    for name, headers in headers_by_sheet.items():
        ws=workbook[name]
        if [c.value for c in ws[1]] != headers:
            raise ReviewError('INVALID_HEADERS: '+name)
        rows=[]
        for number, cells in enumerate(ws.iter_rows(min_row=2),2):
            if all(_blank(c.value) for c in cells):
                continue
            for cell in cells:
                if cell.data_type in {'f','e'}:
                    raise ReviewError(f'FORMULA_OR_ERROR_NOT_ALLOWED: {name}!{cell.coordinate}')
            rows.append(dict(zip(headers,[c.value for c in cells]),_row=number))
        tables[name]=rows
    return tables


def _key(name, row):
    sid=row['sample_id']
    return (sid,row['field_name']) if name=='AI_Fields_Review' else (sid,row['item_id']) if name=='AI_Items_Review' else (sid,row['part']) if name=='OCR_Review' else (sid,)


def _index(name, rows):
    result={}
    for row in rows:
        key=_key(name,row)
        if key in result:
            raise ReviewError(f'DUPLICATE_ROW: {name} sample/key {key}, rows {result[key].get("_row")} and {row.get("_row")}')
        result[key]=row
    return result


def _integrity(tables, snapshot, config, root):
    """Workbook checksums are never trusted: compare to protected, file-side review snapshot + evidence."""
    if snapshot['config_hash'] != digest(config):
        raise ReviewError('RUN_CONFIG_CHANGED')
    cp=read(root/'checkpoint.json')
    if snapshot['checkpoint_hash'] != digest(cp):
        raise ReviewError('RUN_CHECKPOINT_CHANGED: re-export review after retries')
    samples={s['sample_id']:s for s in config['samples']}
    for info in snapshot['tables']['Run_Info']:
        _, expected=_evidence(root,samples[info['sample_id']],cp)
        if expected != info['result_hash']:
            raise ReviewError('ORIGINAL_AI_CHANGED: '+info['sample_id'])
    for sample in samples.values():
        original=(Path(config['dataset_root'])/sample['relative_path']).resolve()
        if not original.is_relative_to(Path(config['dataset_root']).resolve()) or not original.is_file():
            raise ReviewError('ORIGINAL_FILE_UNAVAILABLE: '+sample['sample_id'])
        with original.open('rb') as stream:actual_hash=hashlib.file_digest(stream,'sha256').hexdigest()
        if actual_hash!=sample['file_hash']:
            raise ReviewError('ORIGINAL_FILE_HASH_CHANGED: '+sample['sample_id'])
    if set(tables)!=set(snapshot['tables']):
        raise ReviewError('REVIEW_SHEETS_CHANGED')
    for name in snapshot['tables']:
        original=_index(name,snapshot['tables'][name])
        edited=_index(name,tables[name])
        missing=set(original)-set(edited)
        if missing:
            raise ReviewError(f'ORIGINAL_ROW_REMOVED: {name} {sorted(missing, key=str)}')
        for key, row in edited.items():
            sid=row['sample_id']
            if sid not in samples:
                raise ReviewError(f'UNKNOWN_SAMPLE: {name} row {row["_row"]} {sid}')
            common=_common(config,samples[sid])
            for column in COMMON:
                if row[column] != common[column]:
                    raise ReviewError(f'IDENTITY_MISMATCH: {name} row {row["_row"]} column {column}')
            if key in original:
                for column in (DUPLICATE_HEADERS if name=='Duplicates' else HEADERS[name]):
                    if column not in EDITABLE[name] and not _readonly_equal(column,row[column],original[key].get(column)):
                        raise ReviewError(f'IMMUTABLE_VALUE_CHANGED: {name} row {row["_row"]} column {column}')
            elif name=='AI_Items_Review':
                if not isinstance(row['item_id'],str) or not re.fullmatch(r'new_[A-Za-z0-9_-]+',row['item_id']):
                    raise ReviewError('NEW_ITEM_ID_REQUIRED: '+str(key))
                if row['row_action']!='ADD' or any(not _blank(row['ai_'+k]) for k in ITEM):
                    raise ReviewError('NEW_ITEM_MUST_HAVE_ADD_AND_BLANK_AI: '+str(key))
            elif name=='AI_Fields_Review':
                if not isinstance(row['field_name'],str) or not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{0,119}',row['field_name']):
                    raise ReviewError('INVALID_NEW_FIELD_NAME: '+str(key))
                if not _blank(row['ai_value']) or row['field_group'] not in {'Core','Optional','Extended'}:
                    raise ReviewError('NEW_FIELD_HAS_AI_VALUE_OR_INVALID_GROUP: '+str(key))
            elif name=='OCR_Review':
                if not isinstance(row['part'],int) or isinstance(row['part'],bool) or row['part']<1 or not _blank(row['ocr_text']):
                    raise ReviewError('INVALID_NEW_OCR_PART: '+str(key))
                source=next(r['ocr_source'] for r in snapshot['tables']['OCR_Review'] if r['sample_id']==sid)
                if row['ocr_source'] != source and not (_blank(row['ocr_source']) and _blank(source)):
                    raise ReviewError('INVALID_NEW_OCR_SOURCE: '+str(key))
            else:
                raise ReviewError('UNEXPECTED_ROW: '+name)


def _cell(key, value, state, location):
    states={'PRESENT':'PRESENT','ABSENT':'NOT_PRESENT','UNREADABLE':'UNREADABLE'}
    if state not in states:
        raise ReviewError(f'FIELD_NOT_REVIEWED: {location} {key} status={state}')
    if state != 'PRESENT':
        if not _blank(value):
            raise ReviewError(f'VALUE_MUST_BE_EMPTY: {location} {key} status={state}')
        return {'state':states[state],'value':None}
    if _blank(value) or isinstance(value,bool):
        raise ReviewError(f'PRESENT_REQUIRES_VALUE: {location} {key}')
    if isinstance(value,(date,datetime)):
        if not key.endswith('Date'):
            raise ReviewError(f'UNEXPECTED_EXCEL_DATE: {location} {key}')
        value=value.date().isoformat() if isinstance(value,datetime) else value.isoformat()
    if key.endswith('Date'):
        try: value=date.fromisoformat(normalize(key,value)).isoformat()
        except (ValueError,TypeError): raise ReviewError(f'INVALID_DATE: {location} {key}') from None
    elif key in MONEY or key.endswith('Amount'):
        if not isinstance(normalize(key,value),Decimal):
            raise ReviewError(f'INVALID_NUMBER: {location} {key}')
    elif not isinstance(value,str):
        raise ReviewError(f'TEXT_REQUIRED: {location} {key}; format identifiers as Text to preserve zeros')
    return {'state':'PRESENT','value':value}


def _build_gt(doc, tables):
    sid=doc['sample_id']
    if doc['document_type'] not in CORE:
        raise ReviewError('INVALID_DOCUMENT_TYPE: '+str(doc['document_type']))
    if doc['reviewed_original']!='YES' or doc['items_reviewed']!='YES':
        raise ReviewError('REVIEW_INCOMPLETE: reviewed_original and items_reviewed must both be YES')
    fields={r['field_name']:_cell(r['field_name'],r['confirmed_value'],r['field_status'],f'AI_Fields_Review row {r["_row"]}')
            for r in tables['AI_Fields_Review'] if r['sample_id']==sid}
    items=[];orders=set()
    for row in tables['AI_Items_Review']:
        if row['sample_id']!=sid:continue
        action=row['row_action']
        if action=='EXTRA':
            if not row['item_id'].startswith('pred_') or any(not _blank(row['confirmed_'+k]) for k in ITEM):
                raise ReviewError(f'INVALID_EXTRA_ITEM: row {row["_row"]}; clear confirmed cells')
            continue
        if action not in {'KEEP','ADD'} or (action=='KEEP') != row['item_id'].startswith('pred_'):
            raise ReviewError(f'INVALID_ITEM_ACTION: row {row["_row"]}')
        order=row['gt_order']
        if not isinstance(order,int) or isinstance(order,bool) or order<1 or order in orders:
            raise ReviewError(f'INVALID_OR_DUPLICATE_GT_ORDER: row {row["_row"]}')
        orders.add(order)
        item={k:_cell(k,row['confirmed_'+k],row[k+'_status'],f'AI_Items_Review row {row["_row"]}') for k in ITEM}
        if all(c['state']=='NOT_PRESENT' for c in item.values()):
            raise ReviewError(f'EMPTY_ITEM: row {row["_row"]}; use EXTRA for an extra predicted row')
        items.append((order,item))
    parts=sorted([r for r in tables['OCR_Review'] if r['sample_id']==sid],key=lambda r:r['part'])
    if [r['part'] for r in parts] != list(range(1,len(parts)+1)):
        raise ReviewError('OCR_PARTS_MUST_BE_CONTIGUOUS')
    flags={r['gt_checked'] for r in parts}
    if not flags.issubset({'YES','NO'}) or len(flags)!=1:
        raise ReviewError('OCR_CHECK_INCONSISTENT: all parts must have the same YES/NO')
    if any(not _blank(r['confirmed_text']) and not isinstance(r['confirmed_text'],str) for r in parts):
        raise ReviewError('OCR_GROUND_TRUTH_MUST_BE_TEXT')
    gt=dict(schema_version='1.0',sample_id=sid,original_filename=doc['original_filename'],file_hash=doc['file_hash'],
        document_type=doc['document_type'],status='DRAFT',scope=list(fields),fields=fields,items_reviewed=True,
        items=[item for _,item in sorted(items)],ocr={'checked':flags=={'YES'},'text':''.join(r['confirmed_text'] or '' for r in parts)})
    missing=set(core_fields(gt))-set(fields)
    if gt['document_type'].endswith('_INVOICE'):missing|=set(HEADER)-set(fields)
    if missing:raise ReviewError('MISSING_REQUIRED_FIELDS: '+', '.join(sorted(missing)))
    validate(gt)
    return gt


def import_review(excel, results_dir, gt_dir, identity):
    if not isinstance(identity,dict) or not identity.get('id') or not identity.get('username'):
        raise ReviewError('AUTHENTICATED_IDENTITY_REQUIRED')
    excel=Path(excel)
    with excel.open('rb') as source: excel_hash=hashlib.file_digest(source,'sha256').hexdigest()
    try:
        wb=load_workbook(excel,data_only=False)
    except (ValueError, OSError, BadZipFile, InvalidFileException, KeyError) as error:
        raise ReviewError('INVALID_EXCEL_FILE: '+type(error).__name__) from None
    try: tables=_read_tables(wb)
    finally: wb.close()
    info=tables['Run_Info']
    if not info or len({r['run_id'] for r in info})!=1 or len({r['review_id'] for r in info})!=1:
        raise ReviewError('MIXED_OR_MISSING_RUN_INFO')
    run_id=info[0]['run_id'];review_id=info[0]['review_id']
    if not isinstance(review_id,str) or not re.fullmatch('[0-9a-f]{32}',review_id):raise ReviewError('INVALID_REVIEW_ID')
    root=resolve_run(results_dir,run_id)
    snapshot=read(root/'reviews'/(review_id+'.json'))
    config=read(root/'config.json')
    _integrity(tables,snapshot,config,root)
    # Validate the whole workbook's immutable content before writing any sample.
    outcomes=[];candidates=[]
    aliases={s['sample_id']:s['duplicate_of'] for s in classify_duplicates(config['samples']) if s['duplicate_of']}
    for sid,canonical in aliases.items():
        outcomes.append(dict(sample_id=sid,status='SKIPPED_DUPLICATE',duplicate_of=canonical))
    for doc in tables['Documents_Review']:
        sid=doc['sample_id']
        if sid in aliases:
            continue
        if doc['confirmation_status']=='DRAFT':
            outcomes.append(dict(sample_id=sid,status='UNCONFIRMED',error='Document is DRAFT; no Ground Truth created'))
            continue
        try:
            if doc['confirmation_status']!='CONFIRMED':raise ReviewError('INVALID_CONFIRMATION_STATUS')
            if doc['confirmed_by']!=identity['username']:raise ReviewError('CONFIRMER_MISMATCH: Excel username must match authenticated API user')
            value=_build_gt(doc,tables)
            value['review_source']=dict(run_id=run_id,review_id=review_id,excel_sha256=excel_hash,notes=doc['notes'])
            candidates.append(value)
        except (ValueError,TypeError) as error:
            outcomes.append(dict(sample_id=sid,status='INVALID',error=str(error)))
    for value in candidates:
        previous=latest(gt_dir,value)
        if previous and all(previous.get(k)==value.get(k) for k in ('fields','items','ocr','document_type','review_source')) and previous['confirmed_by']==identity:
            outcomes.append(dict(sample_id=value['sample_id'],status='UNCHANGED',version=previous['version']))
            continue
        path=confirm_value(value,gt_dir,identity)
        outcomes.append(dict(sample_id=value['sample_id'],status='IMPORTED',ground_truth=str(path),version=read(path)['version']))
    return dict(run_id=run_id,review_id=review_id,excel_sha256=excel_hash,imported_at=datetime.now(timezone.utc).isoformat(),samples=outcomes)

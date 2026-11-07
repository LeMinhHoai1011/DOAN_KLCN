"""Standalone OCR workbook from saved evidence; source text, candidate and confirmed GT stay separate."""
import hashlib
import json
import math
import re
import uuid
from zipfile import BadZipFile
from pathlib import Path
from openpyxl import Workbook, load_workbook
from openpyxl.comments import Comment
from openpyxl.styles import Alignment, Font, PatternFill, Protection
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.utils.exceptions import InvalidFileException
from .checkpoint_manager import read, write_new, digest
from .dataset_loader import classify_duplicates
from .ocr_ground_truth import latest_ocr, confirm_ocr
from .ocr_evaluator import evaluate_ocr
from .review_workbook import ReviewError, _evidence, resolve_run, target_file

BASE=['sample_id','original_filename','source_type','raw_text','gt_candidate','confirmed_text','confirmation_status',
      'confirmed_by','reviewed_original','CER','WER','processing_ms','ocr_status','error_code','page_count',
      'duplicate_of','gt_version','gt_source','source_reference','run_id','review_id','file_hash']
EDITABLE={'confirmed_text','confirmation_status','confirmed_by','reviewed_original'}
LABELS={'source_type':'Nguồn: OCR / PDF text layer','raw_text':'Văn bản nhận dạng','gt_candidate':'GT ứng viên chưa xác minh',
        'confirmed_text':'Văn bản chuẩn GT','confirmation_status':'Trạng thái xác nhận','confirmed_by':'Người xác nhận',
        'reviewed_original':'Đã đối chiếu bản gốc','processing_ms':'Thời gian OCR/text extraction (ms)'}


def display(key):
    return LABELS.get(key,key)


def export_ocr(run_dir,gt_dir,reports_dir,sample_id=None):
    root=Path(run_dir).resolve();config=read(root/'config.json');checkpoint=read(root/'checkpoint.json')
    samples=classify_duplicates(config['samples'])
    if sample_id:
        samples=[s for s in samples if s['sample_id']==sample_id]
        if not samples:raise ReviewError('SAMPLE_NOT_FOUND')
    rid=uuid.uuid4().hex;rows=[];texts={};hashes={};counts=[0,0,0,0];evaluated=0;slots=2
    for sample in samples:
        sid=sample['sample_id'];alias=sample['duplicate_of'];entry=checkpoint.get(sid,{})
        output,evidence_hash=_evidence(root,sample,checkpoint) if not alias else ({},None)
        hashes[sid]=evidence_hash
        ocr=output.get('ocr') or {};raw=ocr.get('rawText') or ''
        if not isinstance(raw,str):raise ReviewError('INVALID_RAW_OCR_TEXT')
        pages=ocr.get('pages',[])
        sources={'PDF_TEXT_LAYER' if p.get('language')=='pdf-text-layer' else 'OCR' for p in pages}
        source=next(iter(sources)) if len(sources)==1 else 'MIXED' if sources else 'UNKNOWN'
        # AI failure is independent: usable OCR returned before that failure is still OCR evidence.
        status='SKIPPED_DUPLICATE' if alias else 'SUCCESS' if raw and pages and all(p.get('text') for p in pages) else 'FAILED' if entry else 'NOT_RUN'
        gt=latest_ocr(gt_dir,sample) if not alias else None
        text=gt['ocr']['text'] if gt else ''
        metrics=evaluate_ocr(raw,gt['ocr']) if gt else None
        if metrics:
            evaluated+=1
            for i,k in enumerate(('char_errors','char_count','word_errors','word_count')):counts[i]+=metrics[k]
        who=gt.get('confirmed_by') if gt else None
        row=dict(sample_id=sid,original_filename=sample['original_filename'],source_type=source,
                 confirmation_status='SKIPPED_DUPLICATE' if alias else 'CONFIRMED' if gt else 'DRAFT',
                 confirmed_by=who.get('username') if isinstance(who,dict) else who,reviewed_original='YES' if gt else 'NO',
                 CER=metrics['CER'] if metrics else None,WER=metrics['WER'] if metrics else None,
                 processing_ms=output.get('ocrMs'),ocr_status=status,error_code=output.get('errorCode') if status=='FAILED' else None,
                 page_count=len(pages),duplicate_of=alias,gt_version=gt['version'] if gt else None,
                 gt_source=gt.get('source_format','OCR_GT') if gt else None,
                 source_reference=str(root/entry['relative_result']) if entry.get('relative_result') else None,
                 run_id=config['run_id'],review_id=rid,file_hash=sample['file_hash'])
        # XML/Excel may normalize literal CR into LF and turn CRLF into double newlines.
        # Use the same declared CRLF->LF view as CER/WER; original evidence JSON is untouched.
        display_raw=raw.replace('\r\n','\n');display_gt=text.replace('\r\n','\n')
        rows.append(row);texts[sid]=(display_raw,display_gt)
        slots=max(slots,math.ceil(max(len(display_raw),len(display_gt))/30000))
    headers=BASE+[f'{kind}_{n}' for n in range(2,slots+1) for kind in ('raw_text','gt_candidate','confirmed_text')]
    for row in rows:
        raw,text=texts[row['sample_id']]
        for n in range(1,slots+1):
            suffix='' if n==1 else '_'+str(n)
            row['raw_text'+suffix]=raw[(n-1)*30000:n*30000]
            row['gt_candidate'+suffix]=row['raw_text'+suffix]
            row['confirmed_text'+suffix]=text[(n-1)*30000:n*30000] if row['confirmation_status']=='CONFIRMED' else None
    summary=dict(run_id=config['run_id'],documents=len(rows),unique_documents=sum(not r['duplicate_of'] for r in rows),
        duplicates=sum(bool(r['duplicate_of']) for r in rows),success=sum(r['ocr_status']=='SUCCESS' for r in rows),
        failed=sum(r['ocr_status']=='FAILED' for r in rows),confirmed_gt=evaluated,
        CER=counts[0]/counts[1] if counts[1] else None,WER=counts[2]/counts[3] if counts[3] else None,
        character_errors=counts[0] if evaluated else None,character_denominator=counts[1] if evaluated else None,
        word_errors=counts[2] if evaluated else None,word_denominator=counts[3] if evaluated else None,
        sources={kind:sum(r['source_type']==kind and not r['duplicate_of'] for r in rows) for kind in ('OCR','PDF_TEXT_LAYER','MIXED','UNKNOWN')})
    durations=[r['processing_ms'] for r in rows if not r['duplicate_of'] and r['processing_ms'] is not None]
    summary.update(pages=sum(r['page_count'] for r in rows if not r['duplicate_of']),
                   mean_processing_ms=sum(durations)/len(durations) if durations else None)
    wb=Workbook();ws=wb.active;ws.title='OCR';ws.append([display(k) for k in headers]);ws.freeze_panes='C2';ws.sheet_view.showGridLines=False
    for row in rows:ws.append([row.get(k) for k in headers])
    for cells in ws.iter_rows():
        for cell in cells:
            key=headers[cell.column-1];editable=(key in EDITABLE or key.startswith('confirmed_text_')) and cell.row>1
            cell.protection=Protection(locked=not editable);cell.font=Font(name='Arial',size=10,color='174EA6' if editable else '202124')
            cell.alignment=Alignment(vertical='top',wrap_text=True)
            if editable:cell.fill=PatternFill('solid',fgColor='FFF2CC')
            if isinstance(cell.value,str):cell.data_type='s'
            if 'text' in key or key=='gt_candidate' or key.startswith('gt_candidate_'):cell.number_format='@'
            if key in {'CER','WER'}:cell.number_format='0.00%'
    for cell in ws[1]:cell.font=Font(name='Arial',size=10,bold=True,color='FFFFFF');cell.fill=PatternFill('solid',fgColor='183B56')
    for i,key in enumerate(headers,1):ws.column_dimensions[ws.cell(1,i).column_letter].width=60 if 'text' in key or key=='gt_candidate' else 34 if key in {'sample_id','original_filename'} else 24
    ws.row_dimensions[1].height=42
    for n in range(2,ws.max_row+1):ws.row_dimensions[n].height=110
    ws.auto_filter.ref=ws.dimensions;ws.protection.sheet=True;ws.protection.autoFilter=False
    for key,values in [('confirmation_status','DRAFT,CONFIRMED'),('reviewed_original','NO,YES')]:
        dv=DataValidation(type='list',formula1='"'+values+'"');dv.showErrorMessage=True;ws.add_data_validation(dv)
        col=ws.cell(1,headers.index(key)+1).column_letter;dv.add(col+'2:'+col+'1048576')
    ws['A1'].comment=Comment(json.dumps(summary,ensure_ascii=False,indent=2),'Tổng hợp OCR')
    snapshot=dict(format='OCR.xlsx/1.0',display_line_endings='CRLF_TO_LF',run_id=config['run_id'],review_id=rid,config_hash=digest(config),
                  checkpoint_hash=digest(checkpoint),evidence_hashes=hashes,headers=headers,rows=rows)
    path=target_file(reports_dir,'OCR.xlsx',config['run_id'])
    write_new(root/'ocr_reviews'/(rid+'.json'),snapshot);write_new(path.with_suffix('.summary.json'),summary)
    with path.open('xb') as stream:wb.save(stream)
    return path,summary


def import_ocr(excel,results_dir,gt_dir,identity):
    if not identity.get('id') or not identity.get('username'):raise ReviewError('AUTHENTICATED_IDENTITY_REQUIRED')
    try:wb=load_workbook(excel,data_only=False)
    except (ValueError,OSError,BadZipFile,InvalidFileException,KeyError) as error:
        raise ReviewError('INVALID_OCR_WORKBOOK: '+type(error).__name__) from None
    try:
        if wb.sheetnames!=['OCR']:
            if {'OCR_Review','Run_Info','Documents_Review'}.issubset(wb.sheetnames):
                wb.close();return import_legacy_ocr(excel,results_dir,gt_dir,identity)
            raise ReviewError('EXPECTED_OCR_SHEET_OR_LEGACY_REVIEW')
        if wb['OCR'].max_row<2:raise ReviewError('EXPECTED_OCR_SHEET')
        ws=wb['OCR'];labels=[c.value for c in ws[1]]
        if not all(k in labels for k in ('run_id','review_id','sample_id')):raise ReviewError('MISSING_METADATA')
        first=dict(zip(labels,[c.value for c in ws[2]]));rid=first['review_id']
        if not isinstance(rid,str) or not re.fullmatch('[a-f0-9]{32}',rid):raise ReviewError('INVALID_REVIEW_ID')
        root=resolve_run(results_dir,first['run_id']);config=read(root/'config.json');cp=read(root/'checkpoint.json')
        snapshot=read(root/'ocr_reviews'/(rid+'.json'));headers=snapshot['headers']
        if labels!=[display(k) for k in headers]:raise ReviewError('INVALID_HEADERS')
        if digest(config)!=snapshot['config_hash'] or digest(cp)!=snapshot['checkpoint_hash']:raise ReviewError('RUN_CHANGED')
        samples={s['sample_id']:s for s in classify_duplicates(config['samples'])}
        original={r['sample_id']:r for r in snapshot['rows']};edited={}
        for cells in ws.iter_rows(min_row=2):
            if all(c.value is None for c in cells):continue
            if any(c.data_type in {'f','e'} for c in cells):raise ReviewError('FORMULA_OR_ERROR_NOT_ALLOWED')
            row=dict(zip(headers,[c.value for c in cells]));sid=row['sample_id']
            if sid in edited:raise ReviewError('DUPLICATE_SAMPLE_ID')
            if sid not in original:raise ReviewError('UNKNOWN_SAMPLE')
            for key,value in row.items():
                if key in EDITABLE or key.startswith('confirmed_text_'):continue
                a=original[sid].get(key)
                equal=value==a or (value in (None,'') and a in (None,''))
                if not equal and key in {'CER','WER'} and isinstance(value,(float,int)) and isinstance(a,(float,int)):equal=math.isclose(value,a,rel_tol=1e-14,abs_tol=1e-12)
                if not equal:raise ReviewError('IMMUTABLE_VALUE_CHANGED: '+key)
            edited[sid]=row
        if set(edited)!=set(original):raise ReviewError('ORIGINAL_SAMPLE_REMOVED')
        for sid,expected in snapshot['evidence_hashes'].items():
            if expected is not None and _evidence(root,samples[sid],cp)[1]!=expected:raise ReviewError('ORIGINAL_OCR_CHANGED')
            file=(Path(config['dataset_root'])/samples[sid]['relative_path']).resolve()
            if not file.is_relative_to(Path(config['dataset_root']).resolve()):raise ReviewError('INVALID_SOURCE_PATH')
            with file.open('rb') as stream:actual_hash=hashlib.file_digest(stream,'sha256').hexdigest()
            if actual_hash!=samples[sid]['file_hash']:raise ReviewError('ORIGINAL_FILE_CHANGED')
    finally:wb.close()
    with Path(excel).open('rb') as stream:excel_hash=hashlib.file_digest(stream,'sha256').hexdigest()
    outcomes=[];valid=[]
    for sid,row in edited.items():
        if samples[sid]['duplicate_of']:
            outcomes.append(dict(sample_id=sid,status='SKIPPED_DUPLICATE'));continue
        if row['confirmation_status']=='DRAFT':outcomes.append(dict(sample_id=sid,status='UNCONFIRMED'));continue
        try:
            if row['confirmation_status']!='CONFIRMED' or row['reviewed_original']!='YES':raise ReviewError('OCR_NOT_FULLY_REVIEWED')
            if row['confirmed_by']!=identity['username']:raise ReviewError('CONFIRMER_MISMATCH')
            keys=['confirmed_text']+sorted([k for k in headers if k.startswith('confirmed_text_')],key=lambda k:int(k.rsplit('_',1)[1]))
            if any(row[k] is not None and not isinstance(row[k],str) for k in keys):raise ReviewError('OCR_GT_MUST_BE_TEXT')
            valid.append((sid,''.join(row[k] or '' for k in keys)))
        except ValueError as error:outcomes.append(dict(sample_id=sid,status='INVALID',error=str(error)))
    for sid,text in valid:
        path,value=confirm_ocr(gt_dir,samples[sid],text,identity,dict(run_id=config['run_id'],review_id=rid,excel_sha256=excel_hash))
        outcomes.append(dict(sample_id=sid,status='IMPORTED' if path else 'UNCHANGED',version=value['version']))
    return {'run_id':config['run_id'],'review_id':rid,'samples':outcomes}


def import_legacy_ocr(excel,results_dir,gt_dir,identity):
    from .review_workbook import _read_tables,_integrity
    wb=load_workbook(excel,data_only=False)
    try:tables=_read_tables(wb)
    finally:wb.close()
    info=tables['Run_Info']
    if not info or len({r['run_id'] for r in info})!=1 or len({r['review_id'] for r in info})!=1:raise ReviewError('MIXED_RUN_INFO')
    rid=info[0]['review_id']
    if not isinstance(rid,str) or not re.fullmatch('[a-f0-9]{32}',rid):raise ReviewError('INVALID_REVIEW_ID')
    root=resolve_run(results_dir,info[0]['run_id']);config=read(root/'config.json');snapshot=read(root/'reviews'/(rid+'.json'))
    _integrity(tables,snapshot,config,root)
    samples={s['sample_id']:s for s in classify_duplicates(config['samples'])}
    with Path(excel).open('rb') as stream:excel_hash=hashlib.file_digest(stream,'sha256').hexdigest()
    outcomes=[];candidates=[]
    for doc in tables['Documents_Review']:
        sid=doc['sample_id']
        if samples[sid]['duplicate_of']:outcomes.append(dict(sample_id=sid,status='SKIPPED_DUPLICATE'));continue
        parts=sorted([r for r in tables['OCR_Review'] if r['sample_id']==sid],key=lambda r:r['part'])
        if all(r['gt_checked']=='NO' for r in parts):outcomes.append(dict(sample_id=sid,status='UNCONFIRMED'));continue
        try:
            if not parts or [r['part'] for r in parts]!=list(range(1,len(parts)+1)) or any(r['gt_checked']!='YES' for r in parts):raise ReviewError('OCR_PARTS_NOT_FULLY_CONFIRMED')
            if doc['reviewed_original']!='YES' or doc['confirmed_by']!=identity['username']:raise ReviewError('ORIGINAL_REVIEW_AND_VERIFIED_USERNAME_REQUIRED')
            if any(r['confirmed_text'] is not None and not isinstance(r['confirmed_text'],str) for r in parts):raise ReviewError('OCR_GT_MUST_BE_TEXT')
            candidates.append((sid,''.join(r['confirmed_text'] or '' for r in parts)))
        except ValueError as error:outcomes.append(dict(sample_id=sid,status='INVALID',error=str(error)))
    for sid,text in candidates:
        path,value=confirm_ocr(gt_dir,samples[sid],text,identity,dict(run_id=config['run_id'],review_id=rid,excel_sha256=excel_hash,format='LEGACY_EXCEL_OCR_ONLY'))
        outcomes.append(dict(sample_id=sid,status='IMPORTED' if path else 'UNCHANGED',version=value['version']))
    return {'run_id':config['run_id'],'review_id':rid,'format':'LEGACY_EXCEL_OCR_ONLY','samples':outcomes}


def verify_ocr_export(run_dir,excel):
    root=Path(run_dir).resolve();wb=load_workbook(excel,data_only=False)
    try:
        if wb.sheetnames!=['OCR']:raise ReviewError('INVALID_SHEETS')
        ws=wb['OCR'];first=dict(zip([c.value for c in ws[1]],[c.value for c in ws[2]]))
        snapshot=read(root/'ocr_reviews'/(first['review_id']+'.json'));headers=snapshot['headers']
        if [c.value for c in ws[1]]!=[display(k) for k in headers]:raise ReviewError('INVALID_HEADERS')
        config=read(root/'config.json');cp=read(root/'checkpoint.json');samples={s['sample_id']:s for s in config['samples']}
        for sid,expected in snapshot['evidence_hashes'].items():
            if expected is not None and _evidence(root,samples[sid],cp)[1]!=expected:raise ReviewError('ORIGINAL_OCR_CHANGED')
        if ws.max_row!=len(snapshot['rows'])+1:raise ReviewError('ROW_COUNT_MISMATCH')
        compared=0
        for cells,row in zip(ws.iter_rows(min_row=2),snapshot['rows']):
            for key,cell in zip(headers,cells):
                if cell.data_type in {'f','e'}:raise ReviewError('FORMULA_OR_ERROR')
                a,b=cell.value,row.get(key)
                equal=a==b or (a in (None,'') and b in (None,''))
                if not equal and key in {'CER','WER'} and isinstance(a,(int,float)) and isinstance(b,(int,float)):equal=math.isclose(a,b,rel_tol=1e-14,abs_tol=1e-12)
                if not equal:raise ReviewError('EXPORTED_OCR_VALUE_MISMATCH: '+key)
                compared+=1
        return dict(status='VERIFIED',run_id=config['run_id'],documents=len(snapshot['rows']),cells_compared=compared,
                    evidence_files_checked=sum(v is not None for v in snapshot['evidence_hashes'].values()),excel=str(Path(excel).resolve()))
    finally:wb.close()

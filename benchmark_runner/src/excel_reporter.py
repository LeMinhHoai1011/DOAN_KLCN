import json
import statistics
from pathlib import Path
from datetime import datetime, timezone
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill
from .checkpoint_manager import read, write_new, digest
from .ground_truth_manager import latest
from .ocr_evaluator import evaluate_ocr
from .ai_evaluator import evaluate_ai, scores
from .dataset_loader import classify_duplicates
from .ocr_ground_truth import latest_ocr


def export_report(run_dir, gt_dir, out_dir, output_path=None):
    root=Path(run_dir)
    config=read(root/'config.json')
    samples=classify_duplicates(config['samples'])
    canonical=[s for s in samples if not s['duplicate_of']]
    duplicates=[s for s in samples if s['duplicate_of']]
    checkpoints=read(root/'checkpoint.json') if (root/'checkpoint.json').exists() else {}
    tables={name:[] for name in ('Summary','Documents','OCR_Detail','AI_Fields','AI_Items','Errors_Logs','Run_Config')}
    if duplicates:
        tables['Duplicates']=[dict(sample_id=s['sample_id'],original_filename=s['original_filename'],
            relative_path=s['relative_path'],file_hash=s['file_hash'],duplicate_of=s['duplicate_of'],
            status='SKIPPED_DUPLICATE') for s in duplicates]
    core_counts=[0,0,0]; macro=[]; ocr_counts=[0,0,0,0]; seconds=[]
    confirmed=0; success=0; core_documents=0; exact=[]; snapshots=[]
    for sample in canonical:
        common={k:sample[k] for k in ('sample_id','original_filename')}
        cp=checkpoints.get(sample['sample_id'])
        output=read(root/cp['relative_result']) if cp else {}
        ocr=output.get('ocr') or {}
        gt=latest(gt_dir,sample)
        ocr_gt=latest_ocr(gt_dir,sample)
        if gt:
            confirmed+=1;snapshots.append(gt)
        result=(output.get('normalized') or {}).get('result') or {}
        ai=evaluate_ai(result,gt) if config['signature']['mode'] in {'ai','both'} else None
        ocr_metrics=evaluate_ocr(ocr.get('rawText'),ocr_gt['ocr'] if ocr_gt else None)
        if cp:
            seconds.append(cp['seconds']);success+=cp['status']=='SUCCESS'
        if ai:
            for row in ai['fields']:
                tables['AI_Fields'].append(dict(**common,**row))
            for row in ai['items']:
                tables['AI_Items'].append(dict(**common,**row))
            if ai['core']:
                core_documents+=1
                for i,k in enumerate(('TP','FP','FN')):core_counts[i]+=ai['core'][k]
                if ai['core']['F1'] is not None:macro.append(ai['core']['F1'])
            if ai['field_exact_accuracy'] is not None:exact.append(ai['field_exact_accuracy'])
        if ocr_metrics:
            for i,k in enumerate(('char_errors','char_count','word_errors','word_count')):ocr_counts[i]+=ocr_metrics[k]
        ocr_status='SUCCESS' if ocr.get('rawText') and all(p.get('text') for p in ocr.get('pages',[])) else 'FAILED' if cp else 'NOT_RUN'
        metrics=ai['overall'] if ai else {}
        tables['Documents'].append(dict(run_id=config['run_id'],**common,document_type=gt['document_type'] if gt else result.get('documentType'),
            file_hash=sample['file_hash'],original_file=str(Path(config['dataset_root'])/sample['relative_path']),
            OCR_status=ocr_status,AI_status=(cp['status'] if cp else 'NOT_RUN') if config['signature']['mode'] in {'ai','both'} else 'NOT_APPLICABLE',
            GT_status=gt['status'] if gt else 'UNCONFIRMED',GT_version=gt['version'] if gt else None,
            **metrics,Field_Exact_Accuracy=ai['field_exact_accuracy'] if ai else None,Core_eligible=ai['core_complete'] if ai else False,
            unreadable_fields=ai['unreadable'] if ai else None,processing_seconds=cp['seconds'] if cp else None,
            raw_reference=str(root/cp['relative_result']) if cp else None))
        engines=sorted({'PDF_TEXT_LAYER' if p.get('language')=='pdf-text-layer' else 'TESSERACT' for p in ocr.get('pages',[])})
        tables['OCR_Detail'].append(dict(**common,engine='+'.join(engines),raw_text=ocr.get('rawText'),
            ground_truth_text=ocr_gt['ocr']['text'] if ocr_gt else None,
            **(ocr_metrics or {}),processing_ms=output.get('ocrMs'),source_reference=str(root/cp['relative_result']) if cp else None))
    names={s['sample_id']:s['original_filename'] for s in canonical}
    for sid,name in names.items():
        for path in sorted((root/'raw'/sid).glob('*/entry.json')):
            event=read(path)
            if event['status']=='FAILED':
                tables['Errors_Logs'].append(dict(sample_id=sid,original_filename=name,
                    stage=config['signature']['mode'],error_code=event['error_code'],error_message='Processing failed; consult restricted raw evidence.',
                    processing_time=event['seconds'],retry_count=event['attempt']-1))
    core=scores(*core_counts)
    completed=[checkpoints[s['sample_id']] for s in canonical if s['sample_id'] in checkpoints]
    summary=dict(total_documents=len(samples),unique_documents=len(canonical),skipped_duplicates=len(duplicates),
        processed_success=success,processed_failed=sum(c['status']=='FAILED' for c in completed),
        not_run=len(canonical)-len(completed),confirmed_ground_truth=confirmed,core_eligible_documents=core_documents,
        **{'Core_Micro_'+k:v for k,v in core.items()},Document_Macro_F1=statistics.mean(macro) if macro else None,
        Mean_Field_Exact_Accuracy=statistics.mean(exact) if exact else None,
        OCR_CER=ocr_counts[0]/ocr_counts[1] if ocr_counts[1] else None,OCR_WER=ocr_counts[2]/ocr_counts[3] if ocr_counts[3] else None,
        OCR_runtime_error_rate=sum(d['OCR_status']=='FAILED' for d in tables['Documents'])/len(completed) if completed else None,
        average_seconds=statistics.mean(seconds) if seconds else None)
    tables['Summary']=[dict(metric=k,value=v) for k,v in summary.items()]
    tables['Run_Config']=[dict(key=k,value=v) for k,v in config.items() if k!='samples']
    tables['Run_Config']+= [dict(key='ground_truth_snapshot_hash',value=digest(snapshots)),dict(key='zero_denominator_policy',value='Undefined ratios are blank; no-positive documents excluded from macro F1'),dict(key='AI_failure_policy',value='Confirmed readable GT evaluated against empty prediction on runtime failure')]
    wb=Workbook();wb.remove(wb.active)
    for name,rows in tables.items():
        ws=wb.create_sheet(name)
        headers=list(dict.fromkeys(k for row in rows for k in row)) or ['No_data']
        ws.append(headers)
        for row in rows:
            cells=[]
            for key in headers:
                v=row.get(key)
                if isinstance(v,(dict,list)):v=json.dumps(v,ensure_ascii=False)
                # Long text is preserved in raw JSON; Excel cells have a 32767 character limit.
                if isinstance(v,str):v=v[:32000]
                cells.append(v)
            ws.append(cells)
            for cell in ws[ws.max_row]:
                if isinstance(cell.value,str):cell.data_type='s'  # prevent formula injection from OCR/AI
        ws.freeze_panes='A2';ws.auto_filter.ref=ws.dimensions
        for cell in ws[1]:cell.font=Font(bold=True,color='FFFFFF');cell.fill=PatternFill('solid',fgColor='183B56')
        for column in ws.columns:
            key=column[0].value
            ws.column_dimensions[column[0].column_letter].width=min(60,max(16,len(key)+3))
            for cell in column[1:]:
                if key in {'Precision','Recall','F1','CER','WER','Field_Exact_Accuracy'}:cell.number_format='0.00%'
                if cell.value in ('FAILED','MISMATCH','MISSING','EXTRA'):cell.fill=PatternFill('solid',fgColor='FCE4D6')
                elif cell.value in ('SUCCESS','MATCH','CONFIRMED'):cell.fill=PatternFill('solid',fgColor='E2EFDA')
        if name=='Summary':
            for row in range(2,ws.max_row+1):
                metric=ws.cell(row,1).value
                if any(k in metric for k in ('Precision','Recall','F1','Accuracy','CER','WER','rate')):
                    ws.cell(row,2).number_format='0.00%'
        if name=='Documents':
            col=headers.index('original_file')+1
            for row in range(2,ws.max_row+1):ws.cell(row,col).hyperlink=Path(ws.cell(row,col).value).as_uri()
    stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    folder=Path(out_dir);folder.mkdir(parents=True,exist_ok=True)
    path=Path(output_path) if output_path is not None else folder/f"benchmark_report_{config['run_id']}_{stamp}.xlsx"
    path.parent.mkdir(parents=True,exist_ok=True)
    write_new(path.with_suffix('.gt.json'),snapshots)
    with path.open('xb') as f:wb.save(f)
    return path

from datetime import datetime,timezone
from pathlib import Path
from .checkpoint_manager import read,write_new,digest
from .dataset_loader import load_dataset
from .review_workbook import ReviewError,resolve_run
from .ocr_workbook import export_ocr
from .ai_workbook import export_ai


def dataset_identity(samples):
    return sorted((s['sample_id'],s['file_hash'],s['relative_path']) for s in samples)


def export_pair(results,ocr_run,ai_run,gt_dir,reports_dir,dataset=None):
    ocr_root=resolve_run(results,ocr_run);ai_root=resolve_run(results,ai_run)
    a,b=read(ocr_root/'config.json'),read(ai_root/'config.json')
    if dataset_identity(a['samples'])!=dataset_identity(b['samples']):raise ReviewError('DATASET_MISMATCH: sample_id/hash/path sets must be identical')
    if b['signature']['mode'] not in {'ai','both'}:raise ReviewError('AI_RUN_REQUIRED')
    identity=dataset_identity(a['samples'])
    current_error=None
    try:current=load_dataset(dataset) if dataset else None
    except ValueError as error:current=None;current_error=str(error)
    cached_by_path={s['relative_path']:s for s in a['samples']}
    current_by_path={s['relative_path']:s for s in current} if current else {}
    coverage=None
    if current is not None:
        cached_hashes={s['file_hash'] for s in a['samples']}
        coverage=dict(files=len(current),unique_contents=len({s['file_hash'] for s in current}),
            added=[s for p,s in current_by_path.items() if p not in cached_by_path],
            removed=[s for p,s in cached_by_path.items() if p not in current_by_path],
            changed=[current_by_path[p] for p in cached_by_path.keys() & current_by_path.keys() if cached_by_path[p]['file_hash']!=current_by_path[p]['file_hash']],
            content_hashes_not_in_cached_runs=len({s['file_hash'] for s in current}-cached_hashes))
    folder=Path(reports_dir)/('paired-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'));folder.mkdir(parents=True)
    op,os=export_ocr(ocr_root,gt_dir,folder);ap,ais=export_ai(ai_root,gt_dir,folder)
    report=dict(ocr_run=a['run_id'],ai_run=b['run_id'],same_dataset=True,samples=len(identity),identity_hash=digest(identity),
                current_dataset_matches=dataset_identity(current)==identity if current is not None else None,current_dataset=coverage,current_dataset_error=current_error,
                files={'OCR.xlsx':str(op),'Ai.xlsx':str(ap)},ocr=os,ai=ais)
    write_new(folder/'dataset_comparison.json',report)
    return report

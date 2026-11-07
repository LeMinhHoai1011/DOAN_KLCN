import json
import re
import time
import os
from pathlib import Path
from datetime import datetime, timezone
from .checkpoint_manager import digest, read, write_new, atomic_checkpoint
from .dataset_loader import load_dataset
from .api_client import ApiError


def run(config, client, run_id, mode='ai', resume=False, retry_failed=False, rerun_success=False, sample_id=None):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,99}',run_id):
        raise ValueError('Unsafe run_id')
    if mode not in {'ocr','ai','both'}:raise ValueError('MODE_MUST_BE_OCR_AI_OR_BOTH')
    api_stage='ocr' if mode=='ocr' else 'ai'
    samples = load_dataset(config['dataset'])
    if sample_id is not None:
        selected=next((s for s in samples if s['sample_id']==sample_id),None)
        if selected is None:raise ValueError('SAMPLE_NOT_FOUND: '+sample_id)
        if selected['duplicate_of']:raise ValueError('SKIPPED_DUPLICATE: use '+selected['duplicate_of'])
        samples=[s for s in samples if s['sample_id']==sample_id or s['duplicate_of']==sample_id]
    health = client.health()
    if health.get('provider') != 'ollama':
        raise ValueError('Benchmark requires the configured Ollama provider')
    if api_stage == 'ai' and not health.get('aiReady'):
        raise ValueError('Ollama not ready; use health command first')
    if api_stage == 'ai' and not health.get('modelDigest'):
        raise ValueError('Cannot pin Ollama model digest; health must return modelDigest')
    source=Path(__file__).parent
    runner_hash=digest({p.name:p.read_text(encoding='utf-8') for p in sorted(source.glob('*.py'))})
    signature = dict(dataset_hash=digest(samples), api=health, options=config['options'], mode=mode,
                     api_url=config['api_url'], schema_version='1.0',runner_hash=runner_hash,selected_sample_id=sample_id)
    root = Path(config['results']) / run_id
    if root.exists():
        if not resume:
            raise ValueError('Run exists; use --resume or a new run-id')
        if read(root/'config.json')['signature'] != signature:
            raise ValueError('Resume rejected: dataset, API versions, model or config changed')
    else:
        root.mkdir(parents=True)
        write_new(root/'config.json', dict(run_id=run_id, signature=signature, dataset_root=str(Path(config['dataset']).resolve()),
                                         started_at=datetime.now(timezone.utc).isoformat(), samples=samples))
    checkpoint_path = root/'checkpoint.json'
    duplicate_list = [s for s in samples if s['duplicate_of']]
    if not (root/'duplicates.json').exists():
        write_new(root/'duplicates.json',duplicate_list)
    checkpoint = read(checkpoint_path) if checkpoint_path.exists() else {}
    run_started_at=read(root/'config.json')['started_at']
    # Recover fully committed attempt evidence after a crash between its write and the checkpoint.
    for sample in samples:
        sid=sample['sample_id']
        if sample['duplicate_of']:
            if sid not in checkpoint:
                checkpoint[sid]=dict(status='SKIPPED_DUPLICATE',duplicate_of=sample['duplicate_of'],attempt=0,
                                     seconds=0,timestamp=run_started_at)
            continue
        committed=list((root/'raw'/sid).glob('*/entry.json'))
        if committed:
            last=max((read(p) for p in committed),key=lambda e:e['attempt'])
            if last['attempt'] > checkpoint.get(sid,{}).get('attempt',0):
                checkpoint[sid]=last
    atomic_checkpoint(checkpoint_path,checkpoint)
    for sample in samples:
        sid = sample['sample_id']
        if sample['duplicate_of']:
            continue
        old = checkpoint.get(sid)
        if old and ((old['status']=='SUCCESS' and not rerun_success) or (old['status']=='FAILED' and not retry_failed)):
            continue
        attempts = root/'raw'/sid
        attempts.mkdir(parents=True,exist_ok=True)
        number = max([int(p.name) for p in attempts.iterdir() if p.is_dir()],default=0)+1
        attempt = attempts/str(number)
        attempt.mkdir()
        start=time.monotonic()
        try:
            output = client.process(Path(config['dataset'])/sample['relative_path'],api_stage,config['options'])
            error_code=output.get('errorCode')
        except ApiError as error:
            output={'status':'FAILED','errorCode':error.code}
            error_code=error.code
        elapsed=time.monotonic()-start
        write_new(attempt/'response.json',output)
        ocr=output.get('ocr')
        write_new(attempt/'ocr.json', dict(status='SUCCESS' if ocr and ocr.get('rawText') and all(p.get('text') for p in ocr.get('pages',[])) else 'FAILED',
                                         result=ocr, processing_ms=output.get('ocrMs')))
        write_new(attempt/'ai.json',dict(status=output['status'] if api_stage=='ai' else 'NOT_APPLICABLE',
                                       parsed=output.get('parsed'), normalized=output.get('normalized'),calls=output.get('calls',[])))
        entry=dict(status=output['status'],error_code=error_code,attempt=number,seconds=elapsed,
                   relative_result=str((attempt/'response.json').relative_to(root)),timestamp=datetime.now(timezone.utc).isoformat())
        write_new(attempt/'entry.json',entry)
        with (root/'attempts.jsonl').open('a',encoding='utf-8') as log:
            log.write(json.dumps(dict(sample_id=sid,**entry))+'\n')
            log.flush()
            os.fsync(log.fileno())
        checkpoint[sid]=entry
        atomic_checkpoint(checkpoint_path,checkpoint)
    from .review_workbook import export_review
    export_review(root, config.get('reports', str(Path(config['results']).parent/'reports')))
    if mode in {'ocr','both'}:
        from .ocr_workbook import export_ocr
        export_ocr(root,config.get('ground_truth',str(Path(config['results']).parent/'ground_truth')),config.get('reports',str(Path(config['results']).parent/'reports')))
    if mode=='both':
        from .ai_workbook import export_ai
        export_ai(root,config.get('ground_truth',str(Path(config['results']).parent/'ground_truth')),config.get('reports',str(Path(config['results']).parent/'reports')))
    return root

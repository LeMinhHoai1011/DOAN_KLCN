"""Independent OCR GT versions: confirmation of text never confirms structured AI fields."""
import copy
from datetime import datetime, timezone
from pathlib import Path
from .checkpoint_manager import digest, read, write_new
from .ground_truth_manager import latest


def latest_ocr(directory,sample):
    paths=sorted((Path(directory)/'ocr'/sample['sample_id']).glob('v*.json'))
    if paths:
        value=read(paths[-1])
        if value.get('schema_version')!='ocr-1.0' or value.get('status')!='CONFIRMED' or not value.get('confirmed_by') or not value.get('confirmed_at'):
            raise ValueError('INVALID_OCR_GROUND_TRUTH')
        if value.get('sample_id')!=sample['sample_id'] or value.get('file_hash')!=sample['file_hash']:
            raise ValueError('OCR_GT_IDENTITY_MISMATCH')
        if value.get('ocr',{}).get('checked') is not True or not isinstance(value['ocr'].get('text'),str):
            raise ValueError('INVALID_OCR_TEXT')
        return value
    legacy=latest(directory,sample)
    if legacy and legacy.get('ocr',{}).get('checked') is True:
        value=copy.deepcopy(legacy);value['source_format']='LEGACY_STRUCTURED_GT';return value
    return None


def confirm_ocr(directory,sample,text,identity,source):
    if not isinstance(text,str) or not identity.get('id') or not identity.get('username'):
        raise ValueError('VERIFIED_IDENTITY_AND_TEXT_REQUIRED')
    old=latest_ocr(directory,sample)
    if old and old.get('schema_version')=='ocr-1.0' and old['ocr']['text']==text and old['confirmed_by']==identity and old.get('review_source')==source:
        return None,old
    root=Path(directory)/'ocr'/sample['sample_id'];root.mkdir(parents=True,exist_ok=True)
    files=sorted(root.glob('v*.json'));previous=read(files[-1]) if files else None
    number=previous['version']+1 if previous else 1
    value=dict(schema_version='ocr-1.0',sample_id=sample['sample_id'],original_filename=sample['original_filename'],
               file_hash=sample['file_hash'],status='CONFIRMED',ocr={'checked':True,'text':text},
               confirmed_by=identity,confirmed_at=datetime.now(timezone.utc).isoformat(),version=number,
               previous_hash=digest(previous) if previous else None,review_source=source)
    path=root/f'v{number:06d}.json';write_new(path,value);return path,value

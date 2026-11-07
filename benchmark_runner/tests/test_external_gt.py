import hashlib
import json
import sys
from pathlib import Path
import pytest
from openpyxl import Workbook, load_workbook
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from src.external_gt import import_external, GOODS
from src.ground_truth_manager import latest
from src.review_workbook import ReviewError

@pytest.fixture
def external(tmp_path):
    dataset=tmp_path/'dataset';dataset.mkdir();(dataset/'a.pdf').write_bytes(b'original')
    sample=dict(sample_id='S_test',original_filename='a.pdf',relative_path='a.pdf',file_hash=hashlib.sha256(b'original').hexdigest())
    run=tmp_path/'run';run.mkdir()
    (run/'config.json').write_text(json.dumps(dict(run_id='test',dataset_root=str(dataset),samples=[sample])),encoding='utf-8')
    wb=Workbook();ws=wb.active;ws.title='GT_Chung_tu'
    ws.append(['Mã mẫu','Tên file','Loại chứng từ','Số hóa đơn','Trạng thái GT','Người xác nhận'])
    ws.append(['S_test','a.pdf','VAT_INVOICE','000149','CONFIRMED','accountant'])
    ws=wb.create_sheet('GT_Hang_hoa');ws.append(['Mã mẫu','Tên file','STT',*GOODS]);ws.append(['S_test','a.pdf',1,'Widget',2,'Cái',100,0.08,16,200])
    path=tmp_path/'GT.xlsx';wb.save(path);wb.close()
    return path,run,tmp_path/'gt',sample

def test_preview_commit_repeat_and_excluded_scope(external):
    path,run,gt,sample=external
    assert import_external(path,run,gt)['samples'][0]['status']=='READY'
    assert not gt.exists()
    identity={'id':1,'username':'accountant'}
    assert import_external(path,run,gt,identity,reviewed=True)['samples'][0]['status']=='IMPORTED'
    value=latest(gt,sample)
    assert value['fields']['invoiceNumber']['value']=='000149'
    assert value['fields']['subtotal']['state']=='UNREADABLE'
    assert value['items'][0]['amount']['value']==200
    assert import_external(path,run,gt,identity,reviewed=True)['samples'][0]['status']=='UNCHANGED'

def test_authentication_and_original_hash(external):
    path,run,gt,sample=external
    with pytest.raises(ReviewError,match='REVIEWED_ORIGINAL_REQUIRED'):
        import_external(path,run,gt,{'id':1,'username':'accountant'})
    with pytest.raises(ReviewError,match='CONFIRMER_MISMATCH'):
        import_external(path,run,gt,{'id':1,'username':'other'},reviewed=True)
    (run.parent/'dataset'/'a.pdf').write_bytes(b'changed')
    with pytest.raises(ReviewError,match='FILE_HASH_MISMATCH'): import_external(path,run,gt)
    assert not gt.exists()

def test_duplicate_item_rejected(external):
    path,run,gt,_=external
    wb=load_workbook(path);ws=wb['GT_Hang_hoa'];ws.append([c.value for c in ws[2]]);wb.save(path);wb.close()
    with pytest.raises(ReviewError,match='INVALID_ITEM_ORDER'): import_external(path,run,gt)

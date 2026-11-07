import copy
import json
import sys
from pathlib import Path
import pytest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from src.ai_evaluator import compare, evaluate_ai, evaluate_items, assignment
from src.ocr_evaluator import evaluate_ocr
from src.field_normalizer import normalize, prediction
from src.ground_truth_manager import make_draft, confirm, latest, validate
from src.checkpoint_manager import write_new, read
from src.dataset_loader import load_dataset
from src.benchmark_runner import run
from src.excel_reporter import export_report
from src.api_client import ApiError, ApiClient


def cell(v):return {'state':'PRESENT' if v is not None else 'NOT_PRESENT','value':v}
def gt(sample):
    value=make_draft(sample,{},'RETAIL_INVOICE')
    value['items_reviewed']=True
    for k in value['fields']:value['fields'][k]=cell(None)
    for k,v in dict(documentType='RETAIL_INVOICE',sellerName='Mẫu',invoiceDate='2026-11-05',totalAmount=1100).items():value['fields'][k]=cell(v)
    return value


def test_wrong_value_missing_and_hallucinated():
    assert compare('invoiceNumber','02',cell('01'))==dict(match_status='MISMATCH',TP=0,FP=1,FN=1)
    assert compare('invoiceNumber',None,cell('01'))['FN']==1
    assert compare('invoiceNumber','01',cell(None))['FP']==1
    assert compare('invoiceNumber','01',{'state':'UNREADABLE'})['FP']==0


def test_normalization_is_strict():
    assert normalize('totalAmount','1.100.000')==1100000
    assert normalize('vatAmount','1.234,50')==normalize('vatAmount',1234.5)
    assert normalize('taxRate','10%')==normalize('taxRate',0.1)
    assert normalize('taxRate','0.100')==normalize('taxRate','0,100')==normalize('taxRate',0.1)
    assert normalize('invoiceDate','05/11/2026')=='2026-11-05'
    assert normalize('invoiceNumber','0001')!='1'
    assert normalize('sellerName','Công ty A')!=normalize('sellerName','Cong ty A')
    assert prediction({'extraFields':[{'name':'bankName','value':'Bank'}]})[0]['bankName']=='Bank'


def test_duplicates_reordering_and_one_to_one():
    from src.ground_truth_manager import ITEM
    a={k:None for k in ITEM};a.update(productName='A',quantity=1,amount=10)
    b={**a,'productName':'B'}
    truth=[{k:cell(v) for k,v in a.items()},{k:cell(v) for k,v in a.items()},{k:cell(v) for k,v in b.items()}]
    rows=evaluate_items([b,a],truth)
    missing={r['truth_row'] for r in rows if r['row_status']=='MISSING'}
    assert len(missing)==1
    assert sum(r['FN'] for r in rows)==3
    assert not any(r['row_status']=='EXTRA' for r in rows)
    assert len(assignment([[8,7],[8,1]]))==2
    assert evaluate_items([a],[])[0]['row_status']=='EXTRA'


def test_ocr_checked_only_and_empty_policy():
    assert evaluate_ocr('abc',{'checked':False,'text':'abc'}) is None
    assert evaluate_ocr('adc',{'checked':True,'text':'abc'})['CER']==pytest.approx(1/3)
    assert evaluate_ocr('x',{'checked':True,'text':''})['CER'] is None


def test_ground_truth_versioning_and_incomplete_rejection(tmp_path):
    sample=dict(sample_id='S_1',original_filename='a.png',file_hash='hash')
    draft=gt(sample);path=tmp_path/'draft.json';write_new(path,draft)
    p1=confirm(path,tmp_path/'gt',{'id':1,'username':'accountant'})
    p2=confirm(path,tmp_path/'gt',{'id':1,'username':'accountant'})
    assert read(p1)['version']==1 and read(p2)['version']==2
    assert latest(tmp_path/'gt',sample)['version']==2
    with pytest.raises(FileExistsError):write_new(path,{})
    draft['fields']['sellerName']['state']='UNCHECKED'
    with pytest.raises(ValueError):validate(draft)


def test_unconfirmed_and_unreadable_core():
    sample=dict(sample_id='S_1',original_filename='a.png',file_hash='hash')
    truth=gt(sample)
    assert evaluate_ai({},truth) is None
    truth['status']='CONFIRMED'
    assert evaluate_ai({},truth)['core']['FN']==3
    hallucinated=evaluate_ai({'extraFields':[{'name':'inventedField','value':'fake'}]},truth)
    assert next(r for r in hallucinated['fields'] if r['field_name']=='inventedField')['FP']==1
    truth['fields']['sellerName']={'state':'UNREADABLE','value':None}
    assert evaluate_ai({},truth)['core'] is None


class FakeClient:
    def __init__(self):self.calls=[];self.fail=True
    def health(self):return dict(provider='ollama',model='qwen3-vl:8b',aiReady=True,promptVersion='a',modelDigest='digest')
    def process(self,path,stage,options):
        self.calls.append(path)
        if self.fail and path.suffix=='.jpg':raise ApiError('API_TIMEOUT')
        return dict(status='SUCCESS',errorCode=None,ocr={'rawText':'sample','pages':[{'text':'sample','language':'vie'}]},
                    normalized={'result':{'documentType':'RETAIL_INVOICE','invoice':{'sellerName':'Mẫu','invoiceDate':'2026-11-05','totalAmount':1100,'items':[]}}},ocrMs=5)


def test_runner_resume_failure_history_and_excel(tmp_path):
    data=tmp_path/'data';data.mkdir();(data/'a.jpg').write_bytes(b'jpg');(data/'b.png').write_bytes(b'png')
    nested=data/'nested';nested.mkdir();(nested/'b.png').write_bytes(b'png')
    config=dict(dataset=str(data),results=str(tmp_path/'results'),options={},api_url='http://localhost')
    client=FakeClient();root=run(config,client,'test')
    assert len(client.calls)==2
    assert len({s['sample_id'] for s in load_dataset(data)})==3
    run(config,client,'test',resume=True);assert len(client.calls)==2
    # Simulate lost checkpoint after durable attempt entry; resume must not repeat successes.
    (root/'checkpoint.json').unlink()
    run(config,client,'test',resume=True);assert len(client.calls)==2
    client.fail=False;run(config,client,'test',resume=True,retry_failed=True);assert len(client.calls)==3
    assert 'API_TIMEOUT' in (root/'attempts.jsonl').read_text()
    with pytest.raises(ValueError):run(config,client,'test')
    sample=load_dataset(data)[0];draft=tmp_path/'draft.json';write_new(draft,gt(sample));confirm(draft,tmp_path/'gt',{'id':1,'username':'acc'})
    report=export_report(root,tmp_path/'gt',tmp_path/'reports')
    report2=export_report(root,tmp_path/'gt',tmp_path/'reports');assert report!=report2
    from openpyxl import load_workbook
    wb=load_workbook(report)
    assert len(wb.sheetnames)==8 and wb['Documents'].max_row==3
    assert wb['Duplicates'].max_row==2
    assert wb['Errors_Logs'].max_row==2
    assert wb['Documents'].freeze_panes=='A2'
    (data/'a.jpg').write_bytes(b'changed')
    with pytest.raises(ValueError):run(config,client,'test',resume=True)


def test_api_connection_timeout_schema_and_identity(monkeypatch,tmp_path):
    import requests
    monkeypatch.setenv('BENCHMARK_JWT','test-only')
    client=ApiClient({'api_url':'http://127.0.0.1:1','timeout_seconds':1})
    def unavailable(*a,**kw):raise requests.ConnectionError('sensitive message')
    monkeypatch.setattr(client.session,'request',unavailable)
    with pytest.raises(ApiError,match='API_UNAVAILABLE'):client.health()
    def timeout(*a,**kw):raise requests.Timeout('secret')
    monkeypatch.setattr(client.session,'request',timeout)
    with pytest.raises(ApiError,match='API_TIMEOUT'):client.health()
    monkeypatch.setattr(client,'request',lambda *a,**kw:{'status':'SUCCESS','ocr':{}})
    path=tmp_path/'x.png';path.write_bytes(b'x')
    with pytest.raises(ApiError,match='API_SCHEMA_ERROR'):client.process(path,'ai',{})
    monkeypatch.setattr(client,'request',lambda *a,**kw:{'id':1,'username':'employee','role':'EMPLOYEE'})
    with pytest.raises(ApiError,match='CONFIRMER_ROLE_REQUIRED'):client.identity()

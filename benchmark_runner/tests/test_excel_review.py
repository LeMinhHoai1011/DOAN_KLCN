import copy
import hashlib
import importlib.util
import json
import sys
from datetime import datetime
from pathlib import Path

import pytest
from openpyxl import load_workbook

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from src.benchmark_runner import run
from src.checkpoint_manager import read
from src.ground_truth_manager import latest, ITEM
from src.review_workbook import HEADERS, ReviewError, import_review, resolve_run, export_review
from src.excel_reporter import export_report

IDENTITY={'id':12,'username':'accountant_01'}


class FixtureApi:
    def __init__(self,raw='ORIGINAL OCR',item_count=2):self.raw=raw;self.item_count=item_count;self.calls=0
    def health(self):return dict(provider='ollama',model='qwen3-vl:8b',aiReady=True,modelDigest='fixture-only')
    def process(self,path,mode,options):
        self.calls+=1
        item=dict(productName='Duplicate item',quantity=1,unit='box',unitPrice=100,taxRate=.1,taxAmount=10,amount=100)
        return dict(status='SUCCESS',ocr={'rawText':self.raw,'pages':[{'text':self.raw,'language':'eng'}]},ocrMs=1,
                    normalized={'result':{'documentType':'RETAIL_INVOICE','invoice':{
                        'sellerName':'AI Shop','invoiceDate':'2026-11-05','totalAmount':9999,
                        'items':[dict(item) for _ in range(self.item_count)]}}})


@pytest.fixture
def environment(tmp_path):
    dataset=tmp_path/'dataset';dataset.mkdir()
    (dataset/'a.png').write_bytes(b'fixture image A')
    (dataset/'b.png').write_bytes(b'fixture image B')
    config=dict(dataset=str(dataset),results=str(tmp_path/'results'),reports=str(tmp_path/'reports'),
                ground_truth=str(tmp_path/'gt'),options={},api_url='http://not-contacted.invalid')
    client=FixtureApi();root=run(config,client,'review-001')
    manifest=read(root/'config.json')
    return dict(config=config,root=root,manifest=manifest,excel=tmp_path/'reports/initial_results.xlsx',client=client)


def rows(ws):
    keys=[c.value for c in ws[1]]
    return [{**dict(zip(keys,[c.value for c in cells])),'_row':cells[0].row} for cells in ws.iter_rows(min_row=2)]


def set_cell(ws,row,key,value):
    keys=[c.value for c in ws[1]]
    ws.cell(row,keys.index(key)+1,value) if value is not None else setattr(ws.cell(row,keys.index(key)+1),'value',None)


def reviewed(env,selected=None):
    wb=load_workbook(env['excel'])
    for record in rows(wb['Documents_Review']):
        if selected is not None and record['sample_id'] not in selected:continue
        for key,value in dict(confirmation_status='CONFIRMED',confirmed_by=IDENTITY['username'],reviewed_original='YES',items_reviewed='YES').items():
            set_cell(wb['Documents_Review'],record['_row'],key,value)
    values=dict(documentType='RETAIL_INVOICE',sellerName='Corrected Shop',invoiceDate='2026-11-05',totalAmount=1100,invoiceNumber='00001234')
    for record in rows(wb['AI_Fields_Review']):
        key=record['field_name'];value=values.get(key)
        set_cell(wb['AI_Fields_Review'],record['_row'],'confirmed_value',value)
        set_cell(wb['AI_Fields_Review'],record['_row'],'field_status','PRESENT' if value is not None else 'ABSENT')
    for record in rows(wb['AI_Items_Review']):
        for key in ITEM:
            value=record['ai_'+key]
            set_cell(wb['AI_Items_Review'],record['_row'],'confirmed_'+key,value)
            set_cell(wb['AI_Items_Review'],record['_row'],key+'_status','PRESENT' if value is not None else 'ABSENT')
    for record in rows(wb['OCR_Review']):
        set_cell(wb['OCR_Review'],record['_row'],'confirmed_text','CORRECTED OCR')
        set_cell(wb['OCR_Review'],record['_row'],'gt_checked','YES')
    return wb


def save(wb,env,name='reviewed_results.xlsx'):
    path=env['excel'].parent/name;wb.save(path);wb.close();return path


def do_import(env,path):return import_review(path,env['config']['results'],env['config']['ground_truth'],IDENTITY)


def test_export_protects_ai_and_never_prefills_confirmation(environment):
    env=environment;wb=load_workbook(env['excel'])
    assert set(wb.sheetnames)==set(HEADERS)
    assert env['client'].calls==2
    fields=wb['AI_Fields_Review'];headers=HEADERS[fields.title]
    assert fields.cell(2,headers.index('ai_value')+1).protection.locked
    assert not fields.cell(2,headers.index('confirmed_value')+1).protection.locked
    assert all(r['confirmed_value'] is None and r['field_status']=='UNCHECKED' for r in rows(fields))
    assert all(r['confirmation_status']=='DRAFT' for r in rows(wb['Documents_Review']))
    assert all(ws.protection.sheet and ws.freeze_panes=='C2' for ws in wb)
    wb.close()


def test_valid_import_preserves_ai_versions_duplicates_and_metrics(environment):
    env=environment
    original={p:p.read_bytes() for p in env['root'].glob('raw/*/*/response.json')}
    path=save(reviewed(env),env)
    result=do_import(env,path)
    assert {s['status'] for s in result['samples']}=={'IMPORTED'}
    for sample in env['manifest']['samples']:
        gt=latest(env['config']['ground_truth'],sample)
        assert gt['fields']['invoiceNumber']['value']=='00001234'
        assert gt['fields']['totalAmount']['value']==1100
        assert gt['confirmed_by']==IDENTITY and gt['confirmed_at']
        assert len(gt['items'])==2 and gt['items'][0]==gt['items'][1]
        assert gt['ocr']=={'checked':True,'text':'CORRECTED OCR'}
    assert all(p.read_bytes()==data for p,data in original.items())
    assert {s['status'] for s in do_import(env,path)['samples']}=={'UNCHANGED'}
    wb=load_workbook(path)
    first=next(r for r in rows(wb['AI_Fields_Review']) if r['field_name']=='totalAmount')
    set_cell(wb['AI_Fields_Review'],first['_row'],'confirmed_value',1200)
    changed=save(wb,env,'reviewed_v2.xlsx');do_import(env,changed)
    sample=env['manifest']['samples'][0]
    assert latest(env['config']['ground_truth'],sample)['version']==2
    assert read(Path(env['config']['ground_truth'])/sample['sample_id']/'v000001.json')['fields']['totalAmount']['value']==1100
    report=export_report(env['root'],env['config']['ground_truth'],env['config']['reports'])
    wb=load_workbook(report)
    summary={r[0].value:r[1].value for r in list(wb['Summary'])[1:]}
    assert summary['confirmed_ground_truth']==2 and summary['Core_Micro_FN']>0 and summary['OCR_CER']>0
    wb.close();assert env['client'].calls==2


@pytest.mark.parametrize('sheet,column,newvalue',[
    ('AI_Fields_Review','ai_value','tampered'),('AI_Items_Review','ai_productName','tampered'),
    ('OCR_Review','ocr_text','tampered'),('Documents_Review','original_filename','other.png'),
    ('Run_Info','file_hash','0'*64),('Run_Info','model','other-model'),
    ('AI_Items_Review','sample_id','S_unknown'),
])
def test_rejects_immutable_and_identity_changes(environment,sheet,column,newvalue):
    env=environment;wb=reviewed(env);set_cell(wb[sheet],2,column,newvalue)
    with pytest.raises(ReviewError):do_import(env,save(wb,env))
    assert not Path(env['config']['ground_truth']).exists()


@pytest.mark.parametrize('sheet',['Documents_Review','AI_Fields_Review','AI_Items_Review','Run_Info'])
def test_duplicate_rows_are_rejected(environment,sheet):
    wb=reviewed(environment);wb[sheet].append([c.value for c in wb[sheet][2]])
    with pytest.raises(ReviewError,match='DUPLICATE_ROW'):do_import(environment,save(wb,environment))


@pytest.mark.parametrize('sheet',['AI_Fields_Review','AI_Items_Review','OCR_Review'])
def test_cannot_hide_predictions_by_deleting_rows(environment,sheet):
    wb=reviewed(environment);wb[sheet].delete_rows(2)
    with pytest.raises(ReviewError,match='ORIGINAL_ROW_REMOVED'):do_import(environment,save(wb,environment))


def test_partial_import_has_specific_errors_and_only_valid_confirmed_gt(environment):
    env=environment;selected={env['manifest']['samples'][0]['sample_id']}
    wb=reviewed(env,selected);path=save(wb,env)
    result=do_import(env,path)
    assert sorted(s['status'] for s in result['samples'])==['IMPORTED','UNCONFIRMED']
    assert latest(env['config']['ground_truth'],env['manifest']['samples'][1]) is None
    wb=reviewed(env)
    first=next(r for r in rows(wb['AI_Fields_Review']) if r['sample_id'] in selected and r['field_name']=='sellerName')
    set_cell(wb['AI_Fields_Review'],first['_row'],'field_status','UNCHECKED')
    result=do_import(env,save(wb,env,'invalid.xlsx'))
    invalid=next(s for s in result['samples'] if s['status']=='INVALID')
    assert 'FIELD_NOT_REVIEWED' in invalid['error'] and 'sellerName' in invalid['error']


def test_add_extra_and_duplicate_new_item_rows(environment):
    env=environment;wb=reviewed(env);ws=wb['AI_Items_Review'];existing=rows(ws)[0]
    set_cell(ws,existing['_row'],'row_action','EXTRA')
    for key in ITEM:set_cell(ws,existing['_row'],'confirmed_'+key,None)
    for index in (3,4):
        record=copy.deepcopy(existing);record.update(item_id='new_'+str(index),row_action='ADD',gt_order=index)
        for key in ITEM:record['ai_'+key]=None
        ws.append([record.get(key) for key in HEADERS[ws.title]])
    # Added rows can have identical contents and must remain two independent GT rows.
    result=do_import(env,save(wb,env))
    assert all(s['status']=='IMPORTED' for s in result['samples'])
    first=latest(env['config']['ground_truth'],env['manifest']['samples'][0])
    assert len(first['items'])==3 and first['items'][1]==first['items'][2]


@pytest.mark.parametrize('field,value,state,code',[
    ('totalAmount','not-a-number','PRESENT','INVALID_NUMBER'),
    ('invoiceDate','2026-99-99','PRESENT','INVALID_DATE'),
    ('invoiceNumber',1234,'PRESENT','TEXT_REQUIRED'),
    ('sellerName','has value','ABSENT','VALUE_MUST_BE_EMPTY'),
    ('sellerName',None,'PRESENT','PRESENT_REQUIRES_VALUE'),
])
def test_invalid_confirmed_values(environment,field,value,state,code):
    env=environment;wb=reviewed(env)
    for record in rows(wb['AI_Fields_Review']):
        if record['field_name']==field:
            set_cell(wb['AI_Fields_Review'],record['_row'],'confirmed_value',value)
            set_cell(wb['AI_Fields_Review'],record['_row'],'field_status',state)
    result=do_import(env,save(wb,env))
    assert all(s['status']=='INVALID' and code in s['error'] for s in result['samples'])


def test_bad_workbook_formula_confirmer_and_changed_source(environment):
    env=environment;wb=reviewed(env)
    set_cell(wb['Documents_Review'],2,'confirmed_by','someone_else')
    result=do_import(env,save(wb,env));assert 'CONFIRMER_MISMATCH' in result['samples'][0]['error']
    wb=reviewed(env);set_cell(wb['AI_Fields_Review'],2,'confirmed_value','=1+1')
    with pytest.raises(ReviewError,match='FORMULA_OR_ERROR'):do_import(env,save(wb,env,'formula.xlsx'))
    wb=reviewed(env);wb['OCR_Review']['A1']='changed'
    with pytest.raises(ReviewError,match='INVALID_HEADERS'):do_import(env,save(wb,env,'headers.xlsx'))
    (Path(env['config']['dataset'])/'a.png').write_bytes(b'changed')
    with pytest.raises(ReviewError,match='ORIGINAL_FILE_HASH_CHANGED'):do_import(env,save(reviewed(env),env,'hash.xlsx'))


def test_original_ai_json_tampering_rejected(environment):
    env=environment;path=save(reviewed(env),env)
    response=next(env['root'].glob('raw/*/*/response.json'))
    output=read(response);output['normalized']['result']['invoice']['totalAmount']=42
    response.write_text(json.dumps(output),encoding='utf-8')
    with pytest.raises(ReviewError,match='ORIGINAL_AI_CHANGED'):do_import(env,path)


def test_changed_type_requires_missing_core_and_new_fields_can_be_added(environment):
    env=environment;wb=reviewed(env)
    for r in rows(wb['Documents_Review']):set_cell(wb['Documents_Review'],r['_row'],'document_type','PAYMENT_VOUCHER')
    for r in rows(wb['AI_Fields_Review']):
        if r['field_name']=='documentType':set_cell(wb['AI_Fields_Review'],r['_row'],'confirmed_value','PAYMENT_VOUCHER')
    result=do_import(env,save(wb,env));assert all('MISSING_REQUIRED_FIELDS' in s['error'] for s in result['samples'])
    wb=reviewed(env);ws=wb['AI_Fields_Review'];record=rows(ws)[0]
    record.update(field_name='bankName',field_group='Extended',ai_value=None,confirmed_value='Reviewed bank',field_status='PRESENT')
    ws.append([record.get(key) for key in HEADERS[ws.title]])
    result=do_import(env,save(wb,env,'extended.xlsx'));assert all(s['status']=='IMPORTED' for s in result['samples'])


def test_long_ocr_is_split_without_loss_and_exports_do_not_overwrite(environment):
    env=environment;response=next(env['root'].glob('raw/*/*/response.json'));output=read(response)
    output['ocr']['rawText']='x'*65000;response.write_text(json.dumps(output),encoding='utf-8')
    exported=export_review(env['root'],env['config']['reports'])
    assert exported.name=='initial_results.xlsx' and exported!=env['excel'] and env['excel'].exists()
    env['excel']=exported;wb=load_workbook(exported)
    sample=response.parent.parent.name
    parts=[r for r in rows(wb['OCR_Review']) if r['sample_id']==sample]
    assert len(parts)==3 and ''.join(r['ocr_text'] for r in parts)=='x'*65000
    wb.close()
    assert resolve_run(env['config']['results'],'latest')==env['root']


def test_cli_evaluate_is_offline_and_import_reports_rejections(environment,monkeypatch,capsys):
    import yaml
    env=environment;config=Path(env['config']['results']).parent/'config.yaml'
    config.write_text(yaml.safe_dump(env['config']),encoding='utf-8')
    spec=importlib.util.spec_from_file_location('benchmark_cli',Path(__file__).resolve().parents[1]/'main.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    class IdentityOnly:
        def __init__(self,*args):pass
        def identity(self):return IDENTITY
    monkeypatch.setattr(module,'ApiClient',IdentityOnly)
    reviewed_path=save(reviewed(env),env)
    monkeypatch.setattr(sys,'argv',['main.py','--config',str(config),'import-ground-truth','--excel',str(reviewed_path)])
    module.main();assert 'IMPORTED' in capsys.readouterr().out
    def no_api(*args):raise AssertionError('evaluate must not call Java')
    monkeypatch.setattr(module,'ApiClient',no_api)
    monkeypatch.setattr(sys,'argv',['main.py','--config',str(config),'evaluate','--run','latest'])
    module.main();path=Path(capsys.readouterr().out.strip())
    assert path.name=='evaluation_report.xlsx' and path.is_file()
    module.main();archived=Path(capsys.readouterr().out.strip());assert archived!=path and path.is_file()
    assert env['client'].calls==2


def test_unreadable_core_is_excluded_and_unchecked_ocr_not_scored(environment):
    env=environment;wb=reviewed(env)
    for record in rows(wb['AI_Fields_Review']):
        if record['field_name']=='sellerName':
            set_cell(wb['AI_Fields_Review'],record['_row'],'confirmed_value',None)
            set_cell(wb['AI_Fields_Review'],record['_row'],'field_status','UNREADABLE')
    for record in rows(wb['OCR_Review']):set_cell(wb['OCR_Review'],record['_row'],'gt_checked','NO')
    assert all(s['status']=='IMPORTED' for s in do_import(env,save(wb,env))['samples'])
    path=export_report(env['root'],env['config']['ground_truth'],env['config']['reports'])
    wb=load_workbook(path);summary={r[0].value:r[1].value for r in list(wb['Summary'])[1:]}
    assert summary['core_eligible_documents']==0 and summary['Core_Micro_F1'] is None and summary['OCR_CER'] is None
    wb.close()


def test_duplicate_gt_order_and_new_row_ai_values_are_rejected(environment):
    env=environment;wb=reviewed(env);ws=wb['AI_Items_Review']
    set_cell(ws,3,'gt_order',1)
    outcomes=do_import(env,save(wb,env))['samples']
    assert any(s['status']=='INVALID' and 'DUPLICATE_GT_ORDER' in s['error'] for s in outcomes)
    wb=reviewed(env);ws=wb['AI_Items_Review'];record=rows(ws)[0]
    record.update(item_id='new_invalid',row_action='ADD',gt_order=3)
    ws.append([record.get(k) for k in HEADERS[ws.title]])
    with pytest.raises(ReviewError,match='NEW_ITEM_MUST_HAVE_ADD_AND_BLANK_AI'):do_import(env,save(wb,env,'bad-new.xlsx'))


def test_excel_dates_and_ocr_additional_part(environment):
    env=environment;wb=reviewed(env)
    for record in rows(wb['AI_Fields_Review']):
        if record['field_name']=='invoiceDate':set_cell(wb['AI_Fields_Review'],record['_row'],'confirmed_value',datetime(2026,11,5))
    ws=wb['OCR_Review'];record=rows(ws)[0]
    record.update(part=2,ocr_text=None,confirmed_text=' appended',gt_checked='YES')
    ws.append([record.get(k) for k in HEADERS[ws.title]])
    assert all(s['status']=='IMPORTED' for s in do_import(env,save(wb,env))['samples'])
    gt=latest(env['config']['ground_truth'],env['manifest']['samples'][0])
    assert gt['fields']['invoiceDate']['value']=='2026-11-05'
    assert gt['ocr']['text']=='CORRECTED OCR appended'


def test_ai_formula_like_text_is_stored_as_text(environment):
    env=environment;response=next(env['root'].glob('raw/*/*/response.json'));value=read(response)
    value['normalized']['result']['invoice']['sellerName']='=1+1'
    response.write_text(json.dumps(value),encoding='utf-8')
    path=export_review(env['root'],env['config']['reports']);wb=load_workbook(path,data_only=False)
    matches=[c for row in wb['AI_Fields_Review'] for c in row if c.value=='=1+1']
    assert len(matches)==1 and matches[0].data_type=='s'
    wb.close()


def test_cli_partial_import_returns_error_report(environment,monkeypatch,capsys):
    import yaml
    env=environment;config=Path(env['config']['results']).parent/'config.yaml'
    config.write_text(yaml.safe_dump(env['config']),encoding='utf-8')
    spec=importlib.util.spec_from_file_location('benchmark_cli_partial',Path(__file__).resolve().parents[1]/'main.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    class IdentityOnly:
        def __init__(self,*args):pass
        def identity(self):return IDENTITY
    monkeypatch.setattr(module,'ApiClient',IdentityOnly)
    monkeypatch.setattr(sys,'argv',['main.py','--config',str(config),'import-ground-truth','--excel',str(env['excel'])])
    with pytest.raises(SystemExit) as exit_info:module.main()
    assert exit_info.value.code==2
    output=json.loads(capsys.readouterr().out);report=read(output['report'])
    assert all(s['status']=='UNCONFIRMED' for s in report['samples'])
    assert not Path(env['config']['ground_truth']).exists()

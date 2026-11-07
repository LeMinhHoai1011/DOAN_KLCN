import copy
import json
from pathlib import Path
import sys
import pytest
from openpyxl import load_workbook
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from src.ai_workbook import export_ai, import_ai, CHECK_HEADERS, DOC, GOODS, display_headers, projection
from src.checkpoint_manager import read
from src.ground_truth_manager import latest, confirm_value
from src.review_workbook import ReviewError
from test_excel_review import environment, IDENTITY
from test_benchmark import gt as legacy_gt


def table(ws,keys):return [dict(zip(keys,[c.value for c in cells]),_row=cells[0].row) for cells in ws.iter_rows(min_row=2)]
def set_value(ws,row,key,value):ws.cell(row,CHECK_HEADERS.index(key)+1).value=value


def completed(path):
    wb=load_workbook(path);ws=wb['Kiem_tra_GT']
    for row in table(ws,CHECK_HEADERS):
        n=row['_row']
        if row['field_status']=='UNCHECKED':
            value=row['ai_prediction'];set_value(ws,n,'gt_confirmed',value)
            set_value(ws,n,'field_status','ABSENT' if value in (None,'') else 'PRESENT')
        if row['kind']=='DOC' and row['field_key']=='documentType':
            for key,value in dict(document_status='CONFIRMED',confirmed_by=IDENTITY['username'],reviewed_original='YES').items():set_value(ws,n,key,value)
    return wb


def save(wb,path):wb.save(path);wb.close();return path


def test_three_sheets_candidate_never_confirmed_and_sample_filter(environment):
    env=environment;path,summary=export_ai(env['root'],env['config']['ground_truth'],env['config']['reports'])
    assert summary['documents']==2 and summary['goods']==4 and summary['Core_Micro']['F1'] is None
    wb=load_workbook(path)
    assert wb.sheetnames==['Ai','Hang_hoa','Kiem_tra_GT']
    assert wb['Ai'].max_row==3 and wb['Hang_hoa'].max_row==5
    records=table(wb['Kiem_tra_GT'],CHECK_HEADERS)
    assert all(r['gt_confirmed'] is None for r in records)
    assert all(r['ai_prediction']==r['gt_candidate'] for r in records)
    assert all(r['TP'] is None and r['F1'] is None for r in records)
    assert all(r['field_status']=='NOT_SUPPORTED' for r in records if r['field_key'] in {'debitAccount','creditAccount','discountRate','lineTotal'})
    wb.close()
    sid=env['manifest']['samples'][0]['sample_id']
    second,sub=export_ai(env['root'],env['config']['ground_truth'],env['config']['reports'],sid)
    assert sub['documents']==1 and second!=path
    assert env['client'].calls==2


def test_confirm_then_reexport_metrics_money_difference_and_original_unchanged(environment):
    env=environment;path,_=export_ai(env['root'],env['config']['ground_truth'],env['config']['reports'])
    originals={p:p.read_bytes() for p in env['root'].glob('raw/*/*/response.json')}
    wb=completed(path);ws=wb['Kiem_tra_GT']
    for row in table(ws,CHECK_HEADERS):
        if row['field_key']=='totalAmount':set_value(ws,row['_row'],'gt_confirmed',1100)
    reviewed=save(wb,path.parent/'Ai_reviewed.xlsx')
    result=import_ai(reviewed,env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert all(s['status']=='IMPORTED' for s in result['samples'])
    assert all(s['status']=='UNCHANGED' for s in import_ai(reviewed,env['config']['results'],env['config']['ground_truth'],IDENTITY)['samples'])
    updated,summary=export_ai(env['root'],env['config']['ground_truth'],env['config']['reports'])
    assert summary['confirmed_gt_documents']==2 and summary['Core_Micro']['FP']==2 and summary['Core_Micro']['FN']==2
    wb=load_workbook(updated);rows=table(wb['Kiem_tra_GT'],CHECK_HEADERS)
    totals=[r for r in rows if r['field_key']=='totalAmount']
    assert all(r['AI_minus_GT']==8899 and r['assessment']=='VALUE_MISMATCH' for r in totals)
    assert originals=={p:p.read_bytes() for p in originals}
    assert env['client'].calls==2;wb.close()


@pytest.mark.parametrize('sheet,key',[('Ai','totalAmount'),('Hang_hoa','amount'),('Kiem_tra_GT','ai_prediction'),('Kiem_tra_GT','gt_candidate')])
def test_immutable_predictions_and_candidates_checked(environment,sheet,key):
    env=environment;path,_=export_ai(env['root'],env['config']['ground_truth'],env['config']['reports'])
    wb=completed(path);keys=CHECK_HEADERS if sheet=='Kiem_tra_GT' else list(read(env['root']/'ai_reviews'/(table(wb['Kiem_tra_GT'],CHECK_HEADERS)[0]['review_id']+'.json'))['headers'][sheet])
    wb[sheet].cell(2,keys.index(key)+1).value='tampered'
    with pytest.raises(ReviewError,match='IMMUTABLE_VALUE_CHANGED'):import_ai(save(wb,path.parent/'bad.xlsx'),env['config']['results'],env['config']['ground_truth'],IDENTITY)


def test_candidate_values_cannot_be_confirmed_without_field_review(environment):
    env=environment;path,_=export_ai(env['root'],env['config']['ground_truth'],env['config']['reports'])
    wb=load_workbook(path);ws=wb['Kiem_tra_GT']
    for row in table(ws,CHECK_HEADERS):
        if row['kind']=='DOC' and row['field_key']=='documentType':
            for key,value in dict(document_status='CONFIRMED',confirmed_by=IDENTITY['username'],reviewed_original='YES').items():set_value(ws,row['_row'],key,value)
    result=import_ai(save(wb,path.parent/'not-reviewed.xlsx'),env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert all(s['status']=='INVALID' for s in result['samples'])
    assert not Path(env['config']['ground_truth']).exists()


def test_append_missing_duplicate_item_as_gt_only_and_preserve_original_rows(environment):
    env=environment;path,_=export_ai(env['root'],env['config']['ground_truth'],env['config']['reports'])
    wb=completed(path);ws=wb['Kiem_tra_GT'];records=table(ws,CHECK_HEADERS)
    first=next(r for r in records if r['kind']=='ITEM')
    original=[r for r in records if r['sample_id']==first['sample_id'] and r['kind']=='ITEM' and r['row_id']==first['row_id']]
    for row in original:
        added=dict(row,row_id='new_missing')
        for k in ('ai_prediction','gt_candidate','TP','FP','FN','Precision','Recall','F1','AI_minus_GT','absolute_difference','matched_gt_row','gt_version'):added[k]=None
        added.update(source='MANUAL_ADDITION')
        if row['field_key']=='productName':added.update(row_action='ADD',gt_order=3)
        ws.append([added.get(k) for k in CHECK_HEADERS])
    result=import_ai(save(wb,path.parent/'added.xlsx'),env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert all(r['status']=='IMPORTED' for r in result['samples'])
    sample=next(s for s in env['manifest']['samples'] if s['sample_id']==first['sample_id'])
    assert len(latest(env['config']['ground_truth'],sample)['items'])==3
    updated,summary=export_ai(env['root'],env['config']['ground_truth'],env['config']['reports'])
    assert summary['goods']==4 and summary['gt_only_goods']==1 and summary['groups']['Hàng hóa']['FN']>0
    wb=load_workbook(updated);assert wb['Hang_hoa'].max_row==6;wb.close()


def test_reuses_old_gt_and_keeps_verified_ocr(environment):
    env=environment;sample=env['manifest']['samples'][0];old=legacy_gt(sample);old['ocr']={'checked':True,'text':'verified old text'}
    confirm_value(old,env['config']['ground_truth'],IDENTITY)
    path,summary=export_ai(env['root'],env['config']['ground_truth'],env['config']['reports'])
    assert summary['confirmed_gt_documents']==1 and summary['Core_Micro']['F1'] is not None
    wb=completed(path);result=import_ai(save(wb,path.parent/'old.xlsx'),env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert all(r['status']=='IMPORTED' for r in result['samples'])
    assert latest(env['config']['ground_truth'],sample)['ocr']==old['ocr']


def test_projection_no_invented_settlement_accounts_or_line_totals():
    values,sources,support,items=projection({'documentType':'RETAIL_INVOICE','accountingAccount':'642','transactionAssessment':{'type':'EXPENSE'},
        'invoice':{'totalAmount':100,'items':[{'productName':'A','amount':100}], 'retailDetails':{
            'tenderedAmount':200,'changeAmount':100,'billDiscount':{'amount':10,'treatment':'SEPARATE'},'lineDiscounts':[{'rowNumber':1,'amount':5}]}},
        'extraFields':[{'name':'invoiceCode','value':'CODE123'}]})
    assert values['accountingAccountSuggestion']=='642' and values['transactionType']=='EXPENSE'
    assert not any(values.get(k) is not None for k in ('debitAccount','creditAccount','paidAmount','outstandingAmount'))
    assert values['lookupCode']=='CODE123' and values['invoiceDiscountAmount']==10
    assert items[0]['values']['discountAmount']==5 and items[0]['values'].get('lineTotal') is None
    assert items[0]['supported']['lineTotal'] is False


def test_failed_attempt_never_exports_partial_ai_as_success(environment):
    env=environment;cp=read(env['root']/'checkpoint.json');sid=env['manifest']['samples'][0]['sample_id']
    cp[sid]['status']='FAILED';(env['root']/'checkpoint.json').write_text(json.dumps(cp),encoding='utf-8')
    path,summary=export_ai(env['root'],env['config']['ground_truth'],env['config']['reports'])
    assert summary['failed']==1
    wb=load_workbook(path);index=[c.value for c in wb['Ai'][1]].index('sample_id')+1
    total_column=display_headers('Ai',['totalAmount'])[0]
    column=[c.value for c in wb['Ai'][1]].index(total_column)+1
    for row in range(2,wb['Ai'].max_row+1):
        if wb['Ai'].cell(row,index).value==sid:assert wb['Ai'].cell(row,column).value is None
    wb.close()


def test_run_single_sample_without_changing_original_dataset(environment):
    from src.benchmark_runner import run
    env=environment;sid=env['manifest']['samples'][0]['sample_id'];before=env['client'].calls
    root=run(env['config'],env['client'],'one-sample',sample_id=sid)
    assert env['client'].calls==before+1
    assert [s['sample_id'] for s in read(root/'config.json')['samples']]==[sid]
    assert len(list(Path(env['config']['dataset']).glob('*.png')))==2

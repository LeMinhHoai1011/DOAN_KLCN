import json
from pathlib import Path
import sys
import pytest
from openpyxl import load_workbook
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from src.ocr_workbook import export_ocr,import_ocr,display
from src.ocr_ground_truth import latest_ocr
from src.ocr_evaluator import evaluate_ocr
from src.ground_truth_manager import latest,confirm_value
from src.ai_workbook import export_ai
from src.paired_export import export_pair
from src.benchmark_runner import run
from src.checkpoint_manager import read
from src.review_workbook import ReviewError
from test_excel_review import environment,IDENTITY,FixtureApi
from test_benchmark import gt as legacy_gt


def keys_for(env,wb):
    first=dict(zip([c.value for c in wb['OCR'][1]],[c.value for c in wb['OCR'][2]]))
    return read(env['root']/'ocr_reviews'/(first['review_id']+'.json'))['headers']


def reviewed(env,path,text='ORIGINAL OCR'):
    wb=load_workbook(path);keys=keys_for(env,wb);ws=wb['OCR']
    for n in range(2,ws.max_row+1):
        for key,value in dict(confirmation_status='CONFIRMED',confirmed_by=IDENTITY['username'],reviewed_original='YES',confirmed_text=text).items():
            ws.cell(n,keys.index(key)+1).value=value
    return wb,keys


def save(wb,path):wb.save(path);wb.close();return path


def test_ocr_candidate_not_gt_then_independent_confirmation_and_weighted_metrics(environment):
    env=environment;path,summary=export_ocr(env['root'],env['config']['ground_truth'],env['config']['reports'])
    assert summary['documents']==2 and summary['CER'] is None and summary['confirmed_gt']==0
    wb=load_workbook(path);keys=keys_for(env,wb);ws=wb['OCR']
    assert ws.max_row==3 and all(ws.cell(n,keys.index('confirmed_text')+1).value is None for n in (2,3))
    assert ws.cell(2,keys.index('raw_text')+1).protection.locked
    assert not ws.cell(2,keys.index('confirmed_text')+1).protection.locked;wb.close()
    wb,keys=reviewed(env,path);wb['OCR'].cell(3,keys.index('confirmed_text')+1).value='SHORT'
    reviewed_path=save(wb,path.parent/'OCR_reviewed.xlsx')
    result=import_ocr(reviewed_path,env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert all(s['status']=='IMPORTED' for s in result['samples'])
    assert all(s['status']=='UNCHANGED' for s in import_ocr(reviewed_path,env['config']['results'],env['config']['ground_truth'],IDENTITY)['samples'])
    assert all(latest(env['config']['ground_truth'],s) is None for s in env['manifest']['samples'])
    _,summary=export_ocr(env['root'],env['config']['ground_truth'],env['config']['reports'])
    first=evaluate_ocr('ORIGINAL OCR',{'checked':True,'text':'ORIGINAL OCR'})
    second=evaluate_ocr('ORIGINAL OCR',{'checked':True,'text':'SHORT'})
    assert summary['CER']==pytest.approx((first['char_errors']+second['char_errors'])/(first['char_count']+second['char_count']))
    assert summary['WER']==pytest.approx((first['word_errors']+second['word_errors'])/(first['word_count']+second['word_count']))
    _,ai=export_ai(env['root'],env['config']['ground_truth'],env['config']['reports'])
    assert ai['Core_Micro']['F1'] is None  # OCR confirmation must not imply AI GT confirmation


@pytest.mark.parametrize('column',['raw_text','gt_candidate','source_type','file_hash'])
def test_ocr_source_and_candidate_integrity(environment,column):
    env=environment;path,_=export_ocr(env['root'],env['config']['ground_truth'],env['config']['reports'])
    wb,keys=reviewed(env,path);wb['OCR'].cell(2,keys.index(column)+1).value='tampered'
    with pytest.raises(ReviewError,match='IMMUTABLE_VALUE_CHANGED'):
        import_ocr(save(wb,path.parent/'bad.xlsx'),env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert not Path(env['config']['ground_truth']).exists()


def test_unconfirmed_ocr_has_no_official_metrics_and_wrong_identity_rejected(environment):
    env=environment;path,_=export_ocr(env['root'],env['config']['ground_truth'],env['config']['reports'])
    result=import_ocr(path,env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert all(s['status']=='UNCONFIRMED' for s in result['samples'])
    wb,keys=reviewed(env,path);wb['OCR'].cell(2,keys.index('confirmed_by')+1).value='different-account'
    outcome=import_ocr(save(wb,path.parent/'wrong.xlsx'),env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert any(s['status']=='INVALID' and 'CONFIRMER_MISMATCH' in s['error'] for s in outcome['samples'])


def test_legacy_excel_gt_conversion_works_and_original_ai_gt_is_unchanged(environment):
    env=environment;sample=env['manifest']['samples'][0];value=legacy_gt(sample);value['ocr']={'checked':True,'text':'legacy verified text'}
    path=confirm_value(value,env['config']['ground_truth'],IDENTITY);original=path.read_bytes()
    assert latest_ocr(env['config']['ground_truth'],sample)['source_format']=='LEGACY_STRUCTURED_GT'
    excel,_=export_ocr(env['root'],env['config']['ground_truth'],env['config']['reports'])
    wb,keys=reviewed(env,excel,'new verified text')
    result=import_ocr(save(wb,excel.parent/'converted.xlsx'),env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert all(s['status']=='IMPORTED' for s in result['samples'])
    assert latest_ocr(env['config']['ground_truth'],sample)['ocr']['text']=='new verified text'
    assert path.read_bytes()==original and latest(env['config']['ground_truth'],sample)['ocr']['text']=='legacy verified text'
    # New structured versions must not overwrite independent OCR GT.
    value['ocr']['checked']=False;confirm_value(value,env['config']['ground_truth'],IDENTITY)
    assert latest_ocr(env['config']['ground_truth'],sample)['ocr']['text']=='new verified text'


def test_long_ocr_stays_one_document_row_and_no_truncation(environment):
    env=environment;sample=env['manifest']['samples'][0];cp=read(env['root']/'checkpoint.json')
    response=env['root']/cp[sample['sample_id']]['relative_result'];output=read(response);raw='á'*65000
    output['ocr']['rawText']=raw;response.write_text(json.dumps(output,ensure_ascii=False),encoding='utf-8')
    path,_=export_ocr(env['root'],env['config']['ground_truth'],env['config']['reports'])
    wb=load_workbook(path);keys=keys_for(env,wb);ws=wb['OCR']
    assert ws.max_row==3
    assert ''.join(ws.cell(2,keys.index(k)+1).value or '' for k in ['raw_text','raw_text_2','raw_text_3'])==raw
    for k in ['confirmed_text','confirmed_text_2','confirmed_text_3']:
        ws.cell(2,keys.index(k)+1).value=raw[:30000] if k in {'confirmed_text','confirmed_text_2'} else raw[60000:]
    for key,value in dict(confirmation_status='CONFIRMED',confirmed_by=IDENTITY['username'],reviewed_original='YES').items():ws.cell(2,keys.index(key)+1).value=value
    import_ocr(save(wb,path.parent/'long.xlsx'),env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert latest_ocr(env['config']['ground_truth'],sample)['ocr']['text']==raw


def test_ai_failure_does_not_change_ocr_success(environment):
    env=environment;cp=read(env['root']/'checkpoint.json');sid=env['manifest']['samples'][0]['sample_id']
    cp[sid]['status']='FAILED';(env['root']/'checkpoint.json').write_text(json.dumps(cp),encoding='utf-8')
    response=env['root']/cp[sid]['relative_result'];out=read(response);out.update(status='FAILED',errorCode='OLLAMA_TIMEOUT');response.write_text(json.dumps(out),encoding='utf-8')
    _,summary=export_ocr(env['root'],env['config']['ground_truth'],env['config']['reports'])
    assert summary['success']==2 and summary['failed']==0


def test_both_mode_is_one_system_api_call_per_sample_and_exports_two_files(environment):
    env=environment
    class RecordingApi(FixtureApi):
        def __init__(self):super().__init__();self.stages=[]
        def process(self,path,stage,options):self.stages.append(stage);return super().process(path,stage,options)
    client=RecordingApi();root=run(env['config'],client,'both-run',mode='both')
    assert client.stages==['ai','ai'] and client.calls==2
    assert read(root/'config.json')['signature']['mode']=='both'
    assert all(read(p)['status']=='SUCCESS' for p in root.glob('raw/*/*/ai.json'))
    assert (Path(env['config']['reports'])/'OCR.xlsx').exists() and (Path(env['config']['reports'])/'Ai.xlsx').exists()


def test_paired_export_rejects_different_datasets_before_output(environment):
    env=environment;other=run(env['config'],FixtureApi(),'other-run')
    report=export_pair(env['config']['results'],'review-001','other-run',env['config']['ground_truth'],env['config']['reports'],env['config']['dataset'])
    assert report['same_dataset'] and report['current_dataset_matches'] and report['samples']==2
    assert Path(report['files']['OCR.xlsx']).is_file() and Path(report['files']['Ai.xlsx']).is_file()
    changed=read(other/'config.json');changed['samples'][0]['file_hash']='wrong'
    (other/'config.json').write_text(json.dumps(changed),encoding='utf-8')
    before=list(Path(env['config']['reports']).glob('paired-*'))
    with pytest.raises(ReviewError,match='DATASET_MISMATCH'):export_pair(env['config']['results'],'review-001','other-run',env['config']['ground_truth'],env['config']['reports'])
    assert list(Path(env['config']['reports']).glob('paired-*'))==before


def test_official_group_metrics_wait_for_complete_scope(environment):
    env=environment;sample=env['manifest']['samples'][0];value=legacy_gt(sample)
    confirm_value(value,env['config']['ground_truth'],IDENTITY)
    _,summary=export_ai(env['root'],env['config']['ground_truth'],env['config']['reports'])
    assert summary['Core_Micro']['F1'] is not None
    # Legacy GT does not cover new transactionType/accountingAccountSuggestion fields.
    assert summary['groups']['Kế toán - đề xuất']['F1'] is None


def test_business_discount_vat_checks_do_not_create_f1_without_gt(environment):
    env=environment;cp=read(env['root']/'checkpoint.json');sid=env['manifest']['samples'][0]['sample_id']
    path=env['root']/cp[sid]['relative_result'];out=read(path);result=out['normalized']['result']
    result['invoice'].update(subtotal=200,vatAmount=20,totalAmount=210,retailDetails={'billDiscount':{'amount':10,'treatment':'SEPARATE'},
        'lineDiscounts':[{'rowNumber':1,'amount':5}]})
    result['invoice']['items'][0]['discountRate']=.05
    result['extraFields']=[{'name':'allowanceAmount','value':0}]
    path.write_text(json.dumps(out),encoding='utf-8')
    excel,summary=export_ai(env['root'],env['config']['ground_truth'],env['config']['reports'])
    assert summary['Core_Micro']['F1'] is None
    wb=load_workbook(excel)
    def column(sheet,key):
        first=dict(zip([c.value for c in wb['Ai'][1]],[c.value for c in wb['Ai'][2]]))
        snap=read(env['root']/'ai_reviews'/(first['review_id']+'.json'));return snap['headers'][sheet].index(key)+1
    assert wb['Hang_hoa'].cell(2,column('Hang_hoa','VAT_arithmetic_delta')).value==0
    assert wb['Hang_hoa'].cell(2,column('Hang_hoa','discount_arithmetic_delta')).value==0
    assert wb['Ai'].cell(2,column('Ai','adjusted_header_arithmetic_delta')).value==0
    wb.close()


def test_cached_pair_does_not_claim_new_dataset_files_were_processed(environment):
    env=environment;run(env['config'],FixtureApi(),'second')
    (Path(env['config']['dataset'])/'new.png').write_bytes(b'new distinct contents')
    report=export_pair(env['config']['results'],'review-001','second',env['config']['ground_truth'],env['config']['reports'],env['config']['dataset'])
    assert report['same_dataset'] is True and report['current_dataset_matches'] is False
    assert report['samples']==2 and report['current_dataset']['files']==3 and len(report['current_dataset']['added'])==1
    assert report['current_dataset']['content_hashes_not_in_cached_runs']==1


def test_import_ocr_directly_from_old_excel_does_not_confirm_ai(environment):
    from test_excel_review import rows,set_cell
    env=environment;wb=load_workbook(env['excel'])
    for row in rows(wb['Documents_Review']):
        set_cell(wb['Documents_Review'],row['_row'],'confirmed_by',IDENTITY['username'])
        set_cell(wb['Documents_Review'],row['_row'],'reviewed_original','YES')
        assert row['confirmation_status']=='DRAFT'
    for row in rows(wb['OCR_Review']):
        set_cell(wb['OCR_Review'],row['_row'],'gt_checked','YES')
        set_cell(wb['OCR_Review'],row['_row'],'confirmed_text','legacy Excel verified text')
    path=save(wb,env['excel'].parent/'legacy_ocr.xlsx')
    result=import_ocr(path,env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert result['format']=='LEGACY_EXCEL_OCR_ONLY' and all(r['status']=='IMPORTED' for r in result['samples'])
    for sample in env['manifest']['samples']:
        assert latest_ocr(env['config']['ground_truth'],sample)['ocr']['text']=='legacy Excel verified text'
        assert latest(env['config']['ground_truth'],sample) is None


def test_export_old_pair_without_current_dataset_is_explicitly_historical(environment):
    env=environment;run(env['config'],FixtureApi(),'another')
    report=export_pair(env['config']['results'],'review-001','another',env['config']['ground_truth'],env['config']['reports'],Path(env['config']['dataset']).parent/'missing')
    assert report['same_dataset'] and report['current_dataset_matches'] is None
    assert report['current_dataset_error'] is not None


def test_excel_timing_rounding_is_not_treated_as_prediction_tampering(environment):
    from src.review_workbook import export_review,import_review
    from test_excel_review import reviewed as old_reviewed,save as old_save
    from src.ai_workbook import verify_ai_export
    env=environment;cp=read(env['root']/'checkpoint.json')
    for entry in cp.values():entry['seconds']=.12345678901234567
    (env['root']/'checkpoint.json').write_text(json.dumps(cp),encoding='utf-8')
    env['excel']=export_review(env['root'],env['config']['reports'])
    result=import_review(old_save(old_reviewed(env),env),env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert all(row['status']=='IMPORTED' for row in result['samples'])
    path,_=export_ai(env['root'],env['config']['ground_truth'],env['config']['reports'])
    assert verify_ai_export(env['root'],path)['status']=='VERIFIED'


def test_windows_crlf_roundtrip_does_not_double_newlines_or_distort_cer(environment):
    from src.ocr_workbook import verify_ocr_export
    env=environment;cp=read(env['root']/'checkpoint.json');sample=env['manifest']['samples'][0]
    raw_path=env['root']/cp[sample['sample_id']]['relative_result'];out=read(raw_path)
    out['ocr']['rawText']='First\r\nSecond\r\n';raw_path.write_text(json.dumps(out),encoding='utf-8');before=raw_path.read_bytes()
    path,_=export_ocr(env['root'],env['config']['ground_truth'],env['config']['reports'])
    wb=load_workbook(path);keys=keys_for(env,wb)
    assert wb['OCR'].cell(2,keys.index('raw_text')+1).value=='First\nSecond\n';wb.close()
    assert verify_ocr_export(env['root'],path)['status']=='VERIFIED'
    wb,keys=reviewed(env,path,'First\nSecond\n')
    import_ocr(save(wb,path.parent/'crlf-reviewed.xlsx'),env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert evaluate_ocr(out['ocr']['rawText'],latest_ocr(env['config']['ground_truth'],sample)['ocr'])['CER']==0
    assert raw_path.read_bytes()==before


def test_legacy_excel_windows_newlines_can_be_migrated_without_weakening_source_check(environment):
    from src.review_workbook import export_review
    from test_excel_review import rows,set_cell
    env=environment;cp=read(env['root']/'checkpoint.json');sample=env['manifest']['samples'][0]
    raw_path=env['root']/cp[sample['sample_id']]['relative_result'];out=read(raw_path);out['ocr']['rawText']='First\r\nSecond'
    raw_path.write_text(json.dumps(out),encoding='utf-8')
    path=export_review(env['root'],env['config']['reports']);wb=load_workbook(path)
    for row in rows(wb['Documents_Review']):
        set_cell(wb['Documents_Review'],row['_row'],'reviewed_original','YES');set_cell(wb['Documents_Review'],row['_row'],'confirmed_by',IDENTITY['username'])
    for row in rows(wb['OCR_Review']):
        set_cell(wb['OCR_Review'],row['_row'],'gt_checked','YES');set_cell(wb['OCR_Review'],row['_row'],'confirmed_text','First\nSecond')
    result=import_ocr(save(wb,path.parent/'legacy-crlf.xlsx'),env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert all(r['status']=='IMPORTED' for r in result['samples'])
    assert evaluate_ocr(out['ocr']['rawText'],latest_ocr(env['config']['ground_truth'],sample)['ocr'])['CER']==0

import json
import statistics
import sys
from pathlib import Path

import pytest
from openpyxl import load_workbook

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from src.dataset_loader import load_dataset
from src.benchmark_runner import run
from src.checkpoint_manager import read
from src.ground_truth_manager import make_draft, confirm_value
from src.excel_reporter import export_report
from src.review_workbook import import_review, ReviewError
from test_excel_review import FixtureApi, IDENTITY, reviewed, save, rows, set_cell


@pytest.fixture
def duplicates(tmp_path):
    dataset=tmp_path/'dataset';dataset.mkdir();(dataset/'nested').mkdir()
    for relative,content in [('a.png',b'one'),('b.pdf',b'one'),('nested/a.png',b'two'),('z.jpg',b'one')]:
        (dataset/relative).write_bytes(content)
    config=dict(dataset=str(dataset),results=str(tmp_path/'results'),reports=str(tmp_path/'reports'),
                ground_truth=str(tmp_path/'gt'),options={},api_url='http://unused.invalid')
    client=FixtureApi();root=run(config,client,'dedup-001')
    return dict(config=config,root=root,client=client,manifest=read(root/'config.json'),excel=tmp_path/'reports/initial_results.xlsx')


def test_hashes_all_files_and_keeps_same_name_different_bytes(duplicates):
    env=duplicates;samples=load_dataset(env['config']['dataset'])
    assert len(samples)==4 and len({s['sample_id'] for s in samples})==4
    by_path={s['relative_path']:s for s in samples}
    assert by_path['a.png']['duplicate_of'] is None
    assert by_path['nested/a.png']['duplicate_of'] is None
    assert by_path['a.png']['original_filename']==by_path['nested/a.png']['original_filename']
    assert by_path['a.png']['file_hash']!=by_path['nested/a.png']['file_hash']
    assert by_path['b.pdf']['duplicate_of']==by_path['z.jpg']['duplicate_of']==by_path['a.png']['sample_id']
    assert by_path['a.png']['file_hash']==by_path['b.pdf']['file_hash']
    assert env['client'].calls==2  # same AI predictions never cause different image bytes to be merged
    assert len(read(env['root']/'duplicates.json'))==2


def test_no_duplicate_api_calls_or_raw_evidence_and_files_untouched(duplicates):
    env=duplicates;sample_dir=Path(env['config']['dataset'])
    before={p.relative_to(sample_dir):(p.read_bytes(),p.stat().st_mtime_ns) for p in sample_dir.rglob('*') if p.is_file()}
    cp=read(env['root']/'checkpoint.json')
    for sample in env['manifest']['samples']:
        if sample['duplicate_of']:
            assert cp[sample['sample_id']]['status']=='SKIPPED_DUPLICATE'
            assert cp[sample['sample_id']]['duplicate_of']==sample['duplicate_of']
            assert not (env['root']/'raw'/sample['sample_id']).exists()
    run(env['config'],env['client'],'dedup-001',resume=True)
    assert env['client'].calls==2
    before_checkpoint=read(env['root']/'checkpoint.json')
    (env['root']/'checkpoint.json').unlink()
    run(env['config'],env['client'],'dedup-001',resume=True)
    assert env['client'].calls==2
    assert read(env['root']/'checkpoint.json')==before_checkpoint
    run(env['config'],env['client'],'dedup-001',resume=True,rerun_success=True)
    assert env['client'].calls==4
    after={p.relative_to(sample_dir):(p.read_bytes(),p.stat().st_mtime_ns) for p in sample_dir.rglob('*') if p.is_file()}
    assert before==after


def test_new_run_processes_same_content_again(duplicates):
    env=duplicates;new=run(env['config'],env['client'],'dedup-002')
    assert env['client'].calls==4
    assert new!=env['root'] and (env['root']/'config.json').exists()
    assert [s['sample_id'] for s in read(new/'config.json')['samples']]==[s['sample_id'] for s in env['manifest']['samples']]


def test_metric_totals_exclude_duplicate_gt_and_runtime(duplicates):
    env=duplicates
    for sample in env['manifest']['samples']:
        gt=make_draft(sample,{},'RETAIL_INVOICE');gt['items_reviewed']=True
        for key in gt['fields']:gt['fields'][key]={'state':'NOT_PRESENT','value':None}
        for key,value in dict(documentType='RETAIL_INVOICE',sellerName='AI Shop',invoiceDate='2026-11-05',totalAmount=9999).items():
            gt['fields'][key]={'state':'PRESENT','value':value}
        if sample['duplicate_of']:gt['fields']['totalAmount']['value']=1  # must not affect metrics
        gt['ocr']={'checked':True,'text':'ORIGINAL OCR'}
        confirm_value(gt,env['config']['ground_truth'],IDENTITY)
    path=export_report(env['root'],env['config']['ground_truth'],env['config']['reports'])
    wb=load_workbook(path);summary={r[0].value:r[1].value for r in list(wb['Summary'])[1:]}
    assert summary['total_documents']==4 and summary['unique_documents']==2 and summary['skipped_duplicates']==2
    assert summary['confirmed_ground_truth']==2 and summary['core_eligible_documents']==2
    assert summary['Core_Micro_TP']==6 and summary['Core_Micro_FN']==0
    assert summary['processed_success']==2 and summary['not_run']==0
    cp=read(env['root']/'checkpoint.json')
    expected=statistics.mean(cp[s['sample_id']]['seconds'] for s in env['manifest']['samples'] if not s['duplicate_of'])
    assert summary['average_seconds']==pytest.approx(expected)
    assert wb['Documents'].max_row==3 and wb['OCR_Detail'].max_row==3
    records=rows(wb['Duplicates'])
    assert len(records)==2 and all(r['status']=='SKIPPED_DUPLICATE' and r['duplicate_of'] for r in records)
    wb.close()


def test_excel_review_lists_duplicates_without_duplicate_gt(duplicates):
    env=duplicates;wb=load_workbook(env['excel'])
    assert wb['Documents_Review'].max_row==3 and wb['Duplicates'].max_row==3
    assert all(r['status']=='SKIPPED_DUPLICATE' for r in rows(wb['Duplicates']))
    wb.close()
    result=import_review(save(reviewed(env),env),env['config']['results'],env['config']['ground_truth'],IDENTITY)
    assert sorted(s['status'] for s in result['samples'])==['IMPORTED','IMPORTED','SKIPPED_DUPLICATE','SKIPPED_DUPLICATE']
    for sample in env['manifest']['samples']:
        exists=(Path(env['config']['ground_truth'])/sample['sample_id']).exists()
        assert exists==(not bool(sample['duplicate_of']))


def test_duplicate_sheet_cannot_be_removed_or_reassigned(duplicates):
    env=duplicates;wb=reviewed(env)
    set_cell(wb['Duplicates'],2,'duplicate_of','S_fake')
    with pytest.raises(ReviewError,match='IMMUTABLE_VALUE_CHANGED'):
        import_review(save(wb,env),env['config']['results'],env['config']['ground_truth'],IDENTITY)
    wb=reviewed(env);wb.remove(wb['Duplicates'])
    with pytest.raises(ReviewError,match='REVIEW_SHEETS_CHANGED'):
        import_review(save(wb,env,'removed.xlsx'),env['config']['results'],env['config']['ground_truth'],IDENTITY)


def test_old_manifest_without_flags_is_also_deduplicated_when_reporting(duplicates):
    env=duplicates;config=env['manifest']
    for sample in config['samples']:
        sample.pop('duplicate_of');sample.pop('dataset_status')
    (env['root']/'config.json').write_text(json.dumps(config),encoding='utf-8')
    path=export_report(env['root'],env['config']['ground_truth'],env['config']['reports'])
    wb=load_workbook(path);assert wb['Documents'].max_row==3 and wb['Duplicates'].max_row==3;wb.close()


def test_failed_canonical_does_not_try_identical_alias(tmp_path):
    from src.api_client import ApiError
    dataset=tmp_path/'dataset';dataset.mkdir();(dataset/'a.png').write_bytes(b'same');(dataset/'b.png').write_bytes(b'same')
    class FailedApi(FixtureApi):
        def process(self,*args):self.calls+=1;raise ApiError('API_TIMEOUT')
    config=dict(dataset=str(dataset),results=str(tmp_path/'results'),options={},api_url='http://unused.invalid')
    client=FailedApi();root=run(config,client,'failed')
    assert client.calls==1
    statuses=[entry['status'] for entry in read(root/'checkpoint.json').values()]
    assert sorted(statuses)==['FAILED','SKIPPED_DUPLICATE']
    run(config,client,'failed',resume=True);assert client.calls==1
    run(config,client,'failed',resume=True,retry_failed=True);assert client.calls==2

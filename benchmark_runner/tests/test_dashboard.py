import json
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from src.dashboard import dashboard_data


def test_empty_reports_and_latest_snapshot(tmp_path):
    root=tmp_path/'results'/'ai-test';root.mkdir(parents=True)
    reports=tmp_path/'reports';reports.mkdir()
    (root/'config.json').write_text(json.dumps({'run_id':'ai-test','started_at':'2026-01-01','samples':[{'sample_id':'S_a','original_filename':'invoice.pdf'}]}))
    (root/'checkpoint.json').write_text(json.dumps({'S_a':{'status':'FAILED','error_code':'LIMIT'}}))
    config={'results':str(root.parent),'reports':str(reports),'ground_truth':str(tmp_path/'gt')}
    data=dashboard_data(config,'ai-test')
    assert data['summary'] is None and data['checks']==[]
    assert data['documents'][0]['error_code']=='LIMIT'
    (root/'ai_reviews').mkdir()
    (root/'ai_reviews'/'review.json').write_text(json.dumps({'review_id':'r1','tables':{'Kiem_tra_GT':[{'FP':1}]}}))
    (reports/'Ai.summary.json').write_text(json.dumps({'run_id':'ai-test','Core_Micro':{'F1':None}}))
    data=dashboard_data(config,'ai-test')
    assert data['summary']['Core_Micro']['F1'] is None
    assert data['checks']==[{'FP':1}]

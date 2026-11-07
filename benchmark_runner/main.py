import argparse
import json
from pathlib import Path
import yaml
from src.api_client import ApiClient, ApiError
from src.benchmark_runner import run
from src.checkpoint_manager import read, write_new
from src.excel_reporter import export_report
from src.ground_truth_manager import make_draft, confirm
from src.review_workbook import export_review, import_review, resolve_run, target_file, ReviewError
from src.ai_workbook import export_ai, import_ai
from src.ocr_workbook import export_ocr, import_ocr
from src.paired_export import export_pair


def main():
    parser=argparse.ArgumentParser(description='File-only Java OCR/AI benchmark orchestration')
    parser.add_argument('--config',default=str(Path(__file__).parent/'config/config.yaml'))
    sub=parser.add_subparsers(dest='command',required=True)
    sub.add_parser('health')
    p=sub.add_parser('serve');p.add_argument('--port',type=int,default=8765)
    p=sub.add_parser('import-external-gt');p.add_argument('--excel',required=True);p.add_argument('--run',required=True)
    p.add_argument('--commit',action='store_true');p.add_argument('--reviewed-original',action='store_true')
    p.add_argument('--blank-policy',choices=['exclude','absent'],default='exclude')
    p=sub.add_parser('run');p.add_argument('--run-id',required=True);p.add_argument('--mode',choices=['ocr','ai','both'],default='ai')
    p.add_argument('--sample-id')
    p.add_argument('--resume',action='store_true');p.add_argument('--retry-failed',action='store_true');p.add_argument('--rerun-success',action='store_true')
    p=sub.add_parser('draft');p.add_argument('--run-id',required=True);p.add_argument('--sample-id',required=True);p.add_argument('--document-type',required=True);p.add_argument('--out',required=True)
    p=sub.add_parser('confirm');p.add_argument('--draft',required=True);p.add_argument('--reviewed-original',action='store_true',required=True)
    p=sub.add_parser('report');p.add_argument('--run-id',required=True)
    p=sub.add_parser('export-review');p.add_argument('--run',default='latest')
    p=sub.add_parser('import-ground-truth');p.add_argument('--excel',required=True)
    p=sub.add_parser('evaluate');p.add_argument('--run',default='latest')
    p=sub.add_parser('export-ai');p.add_argument('--run',default='invoice-001');p.add_argument('--sample-id')
    p=sub.add_parser('import-ai');p.add_argument('--excel',required=True)
    p=sub.add_parser('export-ocr');p.add_argument('--run',default='ocr-001');p.add_argument('--sample-id')
    p=sub.add_parser('import-ocr');p.add_argument('--excel',required=True)
    p=sub.add_parser('export-pair');p.add_argument('--ocr-run',default='ocr-001');p.add_argument('--ai-run',default='invoice-001')
    args=parser.parse_args()
    config_path=Path(args.config).resolve();config=yaml.safe_load(config_path.read_text(encoding='utf-8'))
    base=config_path.parent.parent
    for key in ('dataset','ground_truth','results','reports'):
        path=Path(config[key]);config[key]=str(path if path.is_absolute() else base/path)
    if getattr(args,'run_id',None):
        import re
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,99}',args.run_id):raise ValueError('Unsafe run_id')
    if args.command=='serve':
        from src.dashboard import serve
        serve(config,args.port)
    elif args.command=='import-external-gt':
        from src.external_gt import import_external
        try:
            if args.commit and not args.reviewed_original: raise ReviewError('REVIEWED_ORIGINAL_REQUIRED: add --reviewed-original after checking original documents')
            result=import_external(args.excel,resolve_run(config['results'],args.run),config['ground_truth'],
                ApiClient(config).identity() if args.commit else None,args.blank_policy,args.reviewed_original)
        except (ReviewError,ValueError,FileNotFoundError,ApiError) as error:
            print(json.dumps({'status':'REJECTED','error':str(error)},ensure_ascii=True,indent=2));raise SystemExit(2)
        print(json.dumps(result,ensure_ascii=True,indent=2))
        if any(s['status'] in {'UNCONFIRMED','INVALID'} for s in result['samples']): raise SystemExit(2)
    elif args.command=='health':print(json.dumps(ApiClient(config).health(),indent=2))
    elif args.command=='run':
        root=run(config,ApiClient(config),args.run_id,args.mode,args.resume,args.retry_failed,args.rerun_success,sample_id=args.sample_id)
        exported=json.loads((root/'review_exports.jsonl').read_text(encoding='utf-8').splitlines()[-1])
        print(json.dumps({'run_dir':str(root),'review_excel':exported['path']},ensure_ascii=False,indent=2))
    elif args.command=='report':print(export_report(Path(config['results'])/args.run_id,config['ground_truth'],config['reports']))
    elif args.command=='export-review':print(export_review(resolve_run(config['results'],args.run),config['reports']))
    elif args.command=='export-ai':
        path,summary=export_ai(resolve_run(config['results'],args.run),config['ground_truth'],config['reports'],args.sample_id)
        print(json.dumps({'excel':str(path),'summary':summary},ensure_ascii=True,indent=2))
    elif args.command=='export-ocr':
        path,summary=export_ocr(resolve_run(config['results'],args.run),config['ground_truth'],config['reports'],args.sample_id)
        print(json.dumps({'excel':str(path),'summary':summary},ensure_ascii=True,indent=2))
    elif args.command=='export-pair':
        print(json.dumps(export_pair(config['results'],args.ocr_run,args.ai_run,config['ground_truth'],config['reports'],config['dataset']),ensure_ascii=True,indent=2))
    elif args.command=='import-ocr':
        try:
            result=import_ocr(args.excel,config['results'],config['ground_truth'],ApiClient(config).identity())
        except (ReviewError,FileNotFoundError,ApiError) as error:
            result={'status':'REJECTED','error':str(error)}
        from datetime import datetime,timezone
        report=Path(config['reports'])/('OCR_import_'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'.json')
        write_new(report,result);print(json.dumps({'report':str(report),**result},ensure_ascii=True,indent=2))
        if result.get('status')=='REJECTED' or any(s['status'] in {'UNCONFIRMED','INVALID'} for s in result.get('samples',[])):
            raise SystemExit(2)
    elif args.command=='import-ai':
        try:
            result=import_ai(args.excel,config['results'],config['ground_truth'],ApiClient(config).identity())
        except (ReviewError,FileNotFoundError,ApiError) as error:
            result={'status':'REJECTED','error':str(error)}
        from datetime import datetime,timezone
        report=Path(config['reports'])/('Ai_import_'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'.json')
        write_new(report,result);print(json.dumps({'report':str(report),**result},ensure_ascii=True,indent=2))
        if result.get('status')=='REJECTED' or any(s['status'] in {'UNCONFIRMED','INVALID'} for s in result.get('samples',[])):
            raise SystemExit(2)
    elif args.command=='evaluate':
        root=resolve_run(config['results'],args.run)
        output=target_file(config['reports'],'evaluation_report.xlsx',root.name)
        print(export_report(root,config['ground_truth'],config['reports'],output_path=output))
    elif args.command=='import-ground-truth':
        try:
            result=import_review(args.excel,config['results'],config['ground_truth'],ApiClient(config).identity())
        except (ReviewError,FileNotFoundError,ApiError) as error:
            result={'status':'REJECTED','error':str(error)}
        from datetime import datetime,timezone
        report=Path(config['reports'])/('ground_truth_import_'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'.json')
        write_new(report,result)
        print(json.dumps({'report':str(report),**result},ensure_ascii=False,indent=2))
        if result.get('status')=='REJECTED' or any(s['status'] in {'UNCONFIRMED','INVALID'} for s in result.get('samples',[])):
            raise SystemExit(2)
    elif args.command=='confirm':print(confirm(args.draft,config['ground_truth'],ApiClient(config).identity()))
    elif args.command=='draft':
        root=Path(config['results'])/args.run_id;manifest=read(root/'config.json')
        sample=next(s for s in manifest['samples'] if s['sample_id']==args.sample_id)
        cp=read(root/'checkpoint.json')[args.sample_id]
        if cp['status']=='SKIPPED_DUPLICATE':
            raise ValueError('SKIPPED_DUPLICATE: use canonical sample '+cp['duplicate_of'])
        result=read(root/cp['relative_result']).get('normalized') or {}
        write_new(args.out,make_draft(sample,result.get('result',{}),args.document_type))
        print(args.out)


if __name__=='__main__':
    try:
        main()
    except ApiError as error:
        import sys
        hints={
            'JWT_MISSING':'Chưa đặt biến môi trường BENCHMARK_JWT trong phiên PowerShell này.',
            'JWT_INVALID_VALUE':'BENCHMARK_JWT phải chứa token thật, không dùng chữ mẫu <JWT của tài khoản ADMIN>.',
            'HTTP_401':'JWT thiếu, không hợp lệ hoặc đã hết hạn. Đăng nhập lại và lấy token mới.',
            'HTTP_403':'Endpoint benchmark yêu cầu tài khoản ADMIN.',
            'HTTP_404':'Kiểm tra api_url; bật BENCHMARK_ENABLED=true rồi khởi động lại backend.',
            'API_UNAVAILABLE':'Không kết nối được Java API. Kiểm tra backend và api_url.',
            'API_TIMEOUT':'Java API không trả lời trong thời gian chờ.',
            'API_NON_JSON_RESPONSE':'API không trả JSON. Kiểm tra api_url có trỏ đúng backend, không phải frontend/trang đăng nhập.',
        }
        print(error.code+': '+hints.get(error.code,'Kiểm tra endpoint và cấu hình Java API.'),file=sys.stderr)
        raise SystemExit(1)

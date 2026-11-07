"""Loopback-only dashboard over benchmark evidence; no database or model calls."""
import json
import threading
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs
from .checkpoint_manager import read, digest
from .review_workbook import resolve_run
from .ai_workbook import export_ai


def dashboard_data(config, run_id):
    root=resolve_run(config['results'],run_id)
    manifest=read(root/'config.json'); checkpoint=read(root/'checkpoint.json')
    snapshots=list((root/'ai_reviews').glob('*.json'))
    snapshot=read(max(snapshots,key=lambda p:p.stat().st_mtime_ns)) if snapshots else None
    summary_files=list(Path(config['reports']).rglob('Ai.summary.json'))
    summaries=[]
    for path in summary_files:
        value=read(path)
        if value.get('run_id')==root.name: summaries.append((path.stat().st_mtime_ns,value))
    summary=max(summaries,key=lambda pair:pair[0])[1] if summaries else None
    ocr=[]
    for path in sorted(Path(config['reports']).rglob('OCR.summary.json'),key=lambda p:p.stat().st_mtime_ns,reverse=True):
        value=read(path)
        if not any(x['run_id']==value['run_id'] for x in ocr):ocr.append(value)
    gt_files=[p for s in manifest['samples'] for p in (Path(config['ground_truth'])/s['sample_id']).glob('v*.json')]
    confirmed=len({p.parent.name for p in gt_files})
    snapshot_time=max((p.stat().st_mtime_ns for p in snapshots),default=0)
    stale=not snapshot or snapshot.get('checkpoint_hash')!=digest(checkpoint) or any(p.stat().st_mtime_ns>snapshot_time for p in gt_files)
    return dict(run_id=root.name,summary=summary,ocr=ocr,confirmed_on_disk=confirmed,
        documents=[dict(sample_id=s['sample_id'],filename=s['original_filename'],**checkpoint.get(s['sample_id'],{})) for s in manifest['samples']],
        checks=(snapshot or {}).get('tables',{}).get('Kiem_tra_GT',[]),
        exported_review_id=(snapshot or {}).get('review_id'),
        note=('Dữ liệu báo cáo chưa cập nhật theo GT/checkpoint hiện tại. Bấm Cập nhật báo cáo. ' if stale else '')+
            'Điểm lấy từ lần export-ai gần nhất. Sau khi import GT hoặc retry, bấm Cập nhật báo cáo.')


def serve(config, port=8765):
    lock=threading.Lock()
    class Handler(BaseHTTPRequestHandler):
        def reply(self,value,status=200):
            body=json.dumps(value,ensure_ascii=True,default=str).encode()
            self.send_response(status);self.send_header('Content-Type','application/json; charset=utf-8')
            self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(body)
        def do_GET(self):
            parsed=urlparse(self.path)
            try:
                if parsed.path=='/':
                    body=(Path(__file__).parent/'dashboard.html').read_bytes()
                    self.send_response(200);self.send_header('Content-Type','text/html; charset=utf-8');self.end_headers();self.wfile.write(body)
                elif parsed.path=='/api/runs':
                    self.reply(sorted([p.parent.name for p in Path(config['results']).glob('*/config.json')],reverse=True))
                elif parsed.path=='/api/data':
                    with lock:self.reply(dashboard_data(config,parse_qs(parsed.query).get('run',['latest'])[0]))
                else:self.reply({'error':'Not found'},404)
            except Exception as error:self.reply({'error':str(error)},400)
        def do_POST(self):
            # Require same-origin browser requests and reject cross-site requests.
            if self.headers.get('Origin')!=f'http://127.0.0.1:{port}' or self.headers.get('X-Benchmark')!='dashboard':
                self.reply({'error':'Same-origin request required'},403);return
            if urlparse(self.path).path!='/api/refresh':self.reply({'error':'Not found'},404);return
            try:
                run_id=parse_qs(urlparse(self.path).query).get('run',['latest'])[0]
                with lock:
                    path,summary=export_ai(resolve_run(config['results'],run_id),config['ground_truth'],config['reports'])
                self.reply({'excel':str(path),'summary':summary})
            except Exception as error:self.reply({'error':str(error)},400)
        def log_message(self,*args):pass
    server=ThreadingHTTPServer(('127.0.0.1',port),Handler)
    print(f'Benchmark dashboard: http://127.0.0.1:{port}',flush=True)
    try:server.serve_forever()
    except KeyboardInterrupt:pass
    finally:server.server_close()

import { PdfSurface } from './OcrDocumentPreview'
import { useCallback, useEffect, useRef, useState } from 'react'
import service, { type FileVersion, type DocumentStorage, type DocumentHistory, type DocumentSnapshot, type ArchiveEvent } from '../services/documentArchiveService'
import { getErrorMessage } from '../services/authService'
import { archiveActionLabel, archiveActorLabel, archiveBytes, archiveTimeLabel, snapshotChanges, snapshotFieldLabel, snapshotValueLabel, storageStatusLabel } from '../utils/documentArchivePresentation'
import LoadingState from './ui/LoadingState'
import ErrorState from './ui/ErrorState'
import EmptyState from './ui/EmptyState'

export default function DocumentArchivePanel({ documentId, mode }: { documentId:number; mode:'storage'|'versions'|'history' }) {
  const [versions,setVersions]=useState<FileVersion[]>([]),[storage,setStorage]=useState<DocumentStorage|null>(null),[history,setHistory]=useState<DocumentHistory|null>(null)
  const [loading,setLoading]=useState(true),[error,setError]=useState(''),[busy,setBusy]=useState(false),[refresh,setRefresh]=useState(0)
  const [snapshot,setSnapshot]=useState<DocumentSnapshot|null>(null),[preview,setPreview]=useState<{url:string; mime:string; name:string}|null>(null)
  const [pdfPage,setPdfPage]=useState(1),[pdfPages,setPdfPages]=useState(1)
  const pdfMetadata=useCallback((_width:number,_height:number,pages:number)=>setPdfPages(pages),[])
  const pdfError=useCallback(()=>setError('Không thể xem PDF của phiên bản này. Có thể tải xuống nếu được cấp quyền.'),[])
  const mounted=useRef(true)
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false}},[])
  useEffect(()=>{let active=true;setLoading(true);setError('');setSnapshot(null);setVersions([]);setStorage(null);setHistory(null)
    const load=async()=>{try{
      const [v,s,h]=await Promise.all([mode==='history'?Promise.resolve([]):service.versions(documentId),mode==='history'?Promise.resolve(null):service.storage(documentId),mode==='storage'?Promise.resolve(null):service.history(documentId)])
      if(active){setVersions(v);setStorage(s);setHistory(h)}
    }catch(e){if(active)setError(getErrorMessage(e,'Không thể tải file, phiên bản hoặc lịch sử.'))}finally{if(active)setLoading(false)}}
    void load();return()=>{active=false}
  },[documentId,mode,refresh])
  useEffect(()=>()=>{if(preview)URL.revokeObjectURL(preview.url)},[preview])
  const openFile=async(v:FileVersion,view:boolean)=>{setBusy(true);setError('');try{
    const blob=await service.file(documentId,v.id,view);if(!mounted.current)return
    const url=URL.createObjectURL(blob)
    if(view){setPdfPage(1);setPdfPages(1);setPreview({url,mime:blob.type,name:v.name})}
    else{const link=document.createElement('a');link.href=url;link.download=v.name;link.click();URL.revokeObjectURL(url)}
  }catch(e){if(mounted.current)setError(getErrorMessage(e,'Không thể mở hoặc tải phiên bản file.'))}finally{if(mounted.current)setBusy(false)}}
  const compare=async(e:ArchiveEvent)=>{setBusy(true);setError('');setSnapshot(null);try{const s=await service.snapshot(documentId,e);if(mounted.current)setSnapshot(s)}catch(error){if(mounted.current)setError(getErrorMessage(error,'Không thể đọc dữ liệu trước/sau.'))}finally{if(mounted.current)setBusy(false)}}
  const fileButtons=(v:FileVersion)=><div className="flex gap-2"><button type="button" className="si-btn-secondary" disabled={busy} onClick={()=>void openFile(v,true)}>Xem file</button><button type="button" className="si-btn-secondary" disabled={busy||!storage?.canDownload} onClick={()=>void openFile(v,false)}>Tải xuống</button></div>
  return <div className="space-y-4">
    <div className="flex items-center justify-between gap-3"><h3 className="si-section-title">{mode==='storage'?'File & lưu trữ':mode==='versions'?'Phiên bản file và dữ liệu':'Lịch sử xử lý và chỉnh sửa'}</h3><button type="button" className="si-btn-secondary" disabled={busy||loading} onClick={()=>setRefresh(n=>n+1)}>Làm mới</button></div>
    {loading&&<LoadingState label="Đang tải lịch sử chứng từ..."/>}{error&&<ErrorState message={error} onRetry={()=>setRefresh(n=>n+1)}/>}
    {mode==='storage'&&storage&&<section className="si-card space-y-3 p-4 text-sm"><p className={storage.status==='AVAILABLE'?'text-parsed':'text-review'}>{storageStatusLabel(storage.status)}</p><p>Kiểm tra file hiện tại lúc {archiveTimeLabel(storage.checkedAt)}</p><p>Dung lượng thực tế: {archiveBytes(storage.actualSize)} · MIME: {storage.actualMimeType||'Chưa xác định'}</p>{storage.status==='AVAILABLE'&&!storage.metadataMatches&&<p className="text-review">Metadata trong hệ thống chưa khớp metadata kho lưu trữ. Cần kiểm tra, không tự sửa dữ liệu.</p>}<p className="break-all">SHA-256: {storage.checksum??'Chưa có checksum được lưu; không dùng ETag thay thế'}</p><p className="break-all">ETag (không phải SHA-256): {storage.etag??'Chưa xác định'}</p><p>Tham chiếu lưu trữ được hệ thống quản lý; quyền xem/tải được kiểm tra lại ở mỗi yêu cầu.</p>{storage.original?<div className="space-y-2 border-t border-hairline pt-3"><h4 className="font-semibold">File gốc đã ghi nhận · phiên bản {storage.original.number}</h4><p>{storage.original.name} · {archiveBytes(storage.original.size)} · {storage.original.mimeType}</p><p>{archiveActorLabel(storage.original.actorId)} · {archiveTimeLabel(storage.original.at)}</p>{fileButtons(storage.original)}</div>:<p className="text-review">Chưa có phiên bản số 1 để xác định file gốc lịch sử; không coi file hiện tại là file gốc một cách tự động.</p>}</section>}
    {mode!=='history'&&!loading&&<div className="space-y-3">{versions.length?versions.map(v=><section key={v.id} className="si-card space-y-2 p-4 text-sm"><h4 className="font-semibold">Phiên bản file {v.number}{v.original?' · Gốc':''}{v.current?' · Hiện tại':''}</h4><p>{v.name} · {v.mimeType} · {archiveBytes(v.size)}</p><p>{archiveActorLabel(v.actorId)} · {archiveTimeLabel(v.at)}</p>{fileButtons(v)}</section>):!error&&<EmptyState title="Chưa có lịch sử phiên bản file"/>}</div>}
    {history&&<>
      {history.legacyCoverageIncomplete&&<p className="rounded-control bg-review-surface p-3 text-sm text-review">Dữ liệu cũ chưa có snapshot đầy đủ. Chỉ hiển thị bằng chứng đã lưu, không dựng lại kết quả AI đã bị ghi đè trong quá khứ.</p>}
      <p className="text-sm text-ink-muted">Phiên bản file, snapshot dữ liệu, lượt xử lý và dữ liệu đã duyệt là các mốc riêng. Mã snapshot là ID lịch sử, không phải số phiên bản file. Không có thao tác khôi phục hoặc xóa lịch sử.</p>
      {history.events.length?<div className="space-y-3">{history.events.filter(e=>mode!=='versions'||e.source==='SNAPSHOT'||e.source==='FIELD_CORRECTION').map(e=><article className="si-card space-y-2 p-4 text-sm" key={`${e.source}-${e.id}`}><h4 className="font-semibold">{archiveActionLabel(e.action)} · #{e.id}</h4><p>{archiveActorLabel(e.actorId)} · {archiveTimeLabel(e.at)}</p>{e.note&&<p className="whitespace-pre-wrap">{e.note}</p>}{(e.hasAfter||e.hasBefore||e.source==='FIELD_CORRECTION')&&<button type="button" className="si-btn-secondary" disabled={busy} onClick={()=>void compare(e)}>Xem dữ liệu trước/sau</button>}</article>)}</div>:<EmptyState title="Chưa có lịch sử được ghi nhận"/>}
    </>}
    {snapshot&&<section className="si-card space-y-3 p-4"><h4 className="si-section-title">{archiveActionLabel(snapshot.action)} · snapshot #{snapshot.id}</h4><p className="text-sm">{archiveActorLabel(snapshot.actorId)} · {archiveTimeLabel(snapshot.at)}</p>{snapshot.before==null&&<p className="text-sm text-review">Chưa có snapshot trước mốc này.</p>}<div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Trường thay đổi</th><th className="p-2">Trước</th><th className="p-2">Sau</th></tr></thead><tbody>{snapshotChanges(snapshot.before,snapshot.after).map(change=><tr key={change.path} className="border-t border-hairline"><td className="p-2">{snapshotFieldLabel(change.path)}</td><td className="max-w-sm whitespace-pre-wrap break-words p-2">{snapshotValueLabel(change.path,change.before)}</td><td className="max-w-sm whitespace-pre-wrap break-words p-2">{snapshotValueLabel(change.path,change.after)}</td></tr>)}</tbody></table></div>{snapshotChanges(snapshot.before,snapshot.after).length===0&&<p className="text-sm text-ink-muted">Không có khác biệt dữ liệu trong các trường được hiển thị. Bố cục OCR vẫn được giữ trong snapshot.</p>}</section>}
    {preview&&<section className="si-card p-4"><div className="mb-3 flex justify-between"><h4 className="font-semibold">{preview.name}</h4><button type="button" className="si-btn-secondary" onClick={()=>setPreview(null)}>Đóng file</button></div>{preview.mime.startsWith('image/')?<img src={preview.url} alt={preview.name} className="mx-auto max-h-[700px] max-w-full object-contain"/>:preview.mime==='application/pdf'?<div><div className="mb-3 flex items-center gap-3 text-sm"><button type="button" className="si-btn-secondary" disabled={pdfPage<=1} onClick={()=>setPdfPage(p=>p-1)}>Trang trước</button><span>Trang {pdfPage}/{pdfPages}</span><button type="button" className="si-btn-secondary" disabled={pdfPage>=pdfPages} onClick={()=>setPdfPage(p=>p+1)}>Trang sau</button></div><div className="max-h-[700px] overflow-auto"><PdfSurface url={preview.url} pageNumber={pdfPage} width={600} onMetadata={pdfMetadata} onError={pdfError}/></div></div>:<p className="text-review">Không hỗ trợ xem trực tiếp định dạng này. Hãy tải xuống nếu được cấp quyền.</p>}</section>}
  </div>
}

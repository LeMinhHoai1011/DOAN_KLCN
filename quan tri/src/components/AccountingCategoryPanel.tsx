import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import axios from 'axios'
import { Pencil, Plus, X } from 'lucide-react'
import service, { type AccountingCategory, type AccountingCategoryRequest, type CategoryCompany } from '../services/accountingCategoryService'
import { getCurrentUser, getErrorMessage } from '../services/authService'
import { categoryFailureState, categoryTypeLabel, type CategoryLoadState } from '../utils/accountingCategoryPresentation'
import ContentCard from './ui/ContentCard'
import EmptyState from './ui/EmptyState'
import ErrorState from './ui/ErrorState'
import LoadingState from './ui/LoadingState'

const initialForm = {categoryCode:'',categoryName:'',description:'',categoryType:'' as ''|'INCOME'|'EXPENSE',active:true}
export default function AccountingCategoryPanel({onCount}: {onCount:(state:CategoryLoadState,count:number)=>void}) {
 const [companyId,setCompanyId]=useState<number|null>(()=>getCurrentUser()?.companyId || null)
 const [companies,setCompanies]=useState<CategoryCompany[]>([]),[companyError,setCompanyError]=useState('')
 const [items,setItems]=useState<AccountingCategory[]>([]),[state,setState]=useState<CategoryLoadState>('idle'),[error,setError]=useState(''),[success,setSuccess]=useState('')
 const [modal,setModal]=useState(false),[editing,setEditing]=useState<AccountingCategory|null>(null),[form,setForm]=useState(initialForm),[saving,setSaving]=useState(false)
 const sequence=useRef(0),selectedCompany=useRef(companyId)
 selectedCompany.current=companyId
 const loadCompanies=useCallback(async()=>{try{setCompanies(await service.getCompanies());setCompanyError('')}catch(e){setCompanyError(getErrorMessage(e,'Không thể tải danh sách doanh nghiệp.'))}},[])
 useEffect(()=>{void loadCompanies()},[loadCompanies])
 const load=useCallback(async()=>{
   const request=++sequence.current
   setItems([]);setError('');setState(companyId==null?'idle':'loading');onCount(companyId==null?'idle':'loading',0)
   if(companyId==null)return
   try {const data=await service.getCategories(companyId,true);if(request!==sequence.current || selectedCompany.current!==companyId)return
     const next=data.length?'success':'empty';setItems(data);setState(next);onCount(next,data.length)
   } catch(e){if(request!==sequence.current || selectedCompany.current!==companyId)return
     const next=categoryFailureState(axios.isAxiosError(e)?e.response?.status:undefined);setState(next);onCount(next,0)
     setError(next==='forbidden'?'Bạn không có quyền xem danh mục của doanh nghiệp này.':next==='unauthenticated'?'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.':getErrorMessage(e,'Không tải được danh mục kế toán. Vui lòng thử lại.'))
   }
 },[companyId,onCount])
 useEffect(()=>{void load();return()=>{sequence.current++}},[load])
 const show=(item?:AccountingCategory)=>{setEditing(item||null);setForm(item?{categoryCode:item.categoryCode,categoryName:item.categoryName,description:item.description||'',categoryType:item.categoryType||'',active:item.active}:initialForm);setModal(true);setError('');setSuccess('')}
 const requestBody=(item?:AccountingCategory):AccountingCategoryRequest=>({companyId:companyId!,categoryCode:item?.categoryCode||form.categoryCode,categoryName:item?.categoryName||form.categoryName,description:item?item.description:form.description,categoryType:item?item.categoryType:form.categoryType||null,active:item?!item.active:form.active})
 const save=async(event:FormEvent)=>{event.preventDefault();if(companyId==null)return;setSaving(true);setError('');try{
   if(editing)await service.update(editing.id,requestBody());else await service.create(requestBody())
   setModal(false);setSuccess('Đã lưu danh mục kế toán.');await load()
 }catch(e){setError(getErrorMessage(e,'Không thể lưu danh mục.'))}finally{setSaving(false)}}
 const toggle=async(item:AccountingCategory)=>{if(companyId==null)return;setSaving(true);setError('');try{await service.update(item.id,requestBody(item));setSuccess('Đã cập nhật trạng thái danh mục.');await load()}catch(e){setError(getErrorMessage(e,'Không thể cập nhật trạng thái.'))}finally{setSaving(false)}}
 return <div className="space-y-4">
 <div className="flex flex-wrap items-end justify-between gap-3"><label className="grid gap-1"><span className="si-field-label">Doanh nghiệp</span><select className="si-input" value={companyId??''} disabled={saving} onChange={e=>{setCompanyId(e.target.value?Number(e.target.value):null);setModal(false);setSuccess('')}}><option value="">Chọn doanh nghiệp</option>{companyId!=null&&!companies.some(c=>c.id===companyId)&&<option value={companyId}>Doanh nghiệp #{companyId}</option>}{companies.map(c=><option key={c.id} value={c.id}>{c.companyName} (#{c.id})</option>)}</select></label><button className="si-btn-primary" disabled={companyId==null||saving||state==='loading'||state==='forbidden'||state==='unauthenticated'} onClick={()=>show()}><Plus size={20}/>Thêm nhóm kế toán</button></div>
 {companyError&&<ErrorState message={companyError} onRetry={()=>void loadCompanies()}/>}
 {success&&<p className="rounded-control bg-parsed-surface p-3 text-parsed">{success}</p>}
 {error&&<ErrorState message={error} onRetry={()=>void load()}/>}
 {state==='idle'?<EmptyState title="Chọn doanh nghiệp để xem danh mục"/>:state==='loading'?<LoadingState label="Đang tải danh mục kế toán..."/>:state==='empty'?<EmptyState title="Doanh nghiệp này chưa có danh mục kế toán"/>:state==='success'?<ContentCard><div className="overflow-x-auto"><table className="si-table"><thead><tr>{['Mã danh mục','Tên danh mục','Thu/Chi','Mô tả','Trạng thái','Thao tác'].map(label=><th key={label}>{label}</th>)}</tr></thead><tbody>{items.map(item=><tr key={item.id}><td className="si-num font-semibold text-brand-700">{item.categoryCode}</td><td>{item.categoryName}</td><td>{categoryTypeLabel(item.categoryType)}</td><td>{item.description||'—'}</td><td>{item.active?'Hoạt động':'Ngừng sử dụng'}</td><td><div className="flex gap-2"><button className="si-btn-secondary" disabled={saving} onClick={()=>show(item)} aria-label={'Sửa '+item.categoryCode}><Pencil size={16}/>Sửa</button><button className="si-btn-secondary" disabled={saving} onClick={()=>void toggle(item)}>{item.active?'Ngừng sử dụng':'Kích hoạt'}</button></div></td></tr>)}</tbody></table></div></ContentCard>:null}
 {modal&&<div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 p-4"><form onSubmit={save} className="si-panel w-full max-w-lg"><div className="flex justify-between border-b border-hairline px-5 py-3.5"><h2 className="si-section-title">{editing?'Sửa':'Thêm'} danh mục kế toán</h2><button type="button" disabled={saving} onClick={()=>setModal(false)} aria-label="Đóng"><X size={22}/></button></div><div className="grid gap-4 p-5">
 <label className="grid gap-1">Mã danh mục<input required maxLength={50} pattern="[A-Z][A-Z0-9_]*" className="si-input" value={form.categoryCode} onChange={e=>setForm({...form,categoryCode:e.target.value.trim().toUpperCase()})}/></label>
 <label className="grid gap-1">Tên danh mục<input required maxLength={200} className="si-input" value={form.categoryName} onChange={e=>setForm({...form,categoryName:e.target.value})}/></label>
 <label className="grid gap-1">Thu/Chi<select required={!editing||editing.categoryType!=null} className="si-input" value={form.categoryType} onChange={e=>setForm({...form,categoryType:e.target.value as typeof form.categoryType})}><option value="" disabled={!editing||editing.categoryType!=null}>{editing?'Chưa xác định (giữ nguyên dữ liệu cũ)':'Chọn Thu hoặc Chi'}</option><option value="INCOME">Thu</option><option value="EXPENSE">Chi</option></select></label>
 <label className="grid gap-1">Mô tả<textarea maxLength={255} className="si-input min-h-20" value={form.description} onChange={e=>setForm({...form,description:e.target.value})}/></label><label className="flex gap-2"><input type="checkbox" checked={form.active} onChange={e=>setForm({...form,active:e.target.checked})}/>Đang hoạt động</label><p className="text-xs text-ink-muted">Thu/Chi là loại danh mục, không tự tạo giao dịch hoặc xác nhận thanh toán.</p>{error&&<p className="text-sm text-failed">{error}</p>}</div><div className="flex justify-end gap-2 border-t border-hairline px-5 py-3.5"><button type="button" disabled={saving} className="si-btn-secondary" onClick={()=>setModal(false)}>Hủy</button><button disabled={saving} className="si-btn-primary">{saving?'Đang lưu...':'Lưu'}</button></div></form></div>}
 </div>
}

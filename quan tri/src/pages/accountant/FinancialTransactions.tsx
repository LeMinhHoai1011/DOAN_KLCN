import {paymentFormError,paymentFieldErrors,paymentSaveError} from '../../utils/paymentAutofill'
import type {PaymentEdits} from '../../utils/paymentAutofill'
import {apiLoadError} from '../../utils/apiLoadError'
import {defaultFinancialFilter} from '../../utils/financialTimeFilter'
import {useFinancialTimeFilter} from '../../hooks/useFinancialTimeFilter'
import FinancialTimeFilter from '../../components/FinancialTimeFilter'
import { formatDate, businessToday } from '../../utils/dateTime'
import DateInput from '../../components/ui/DateInput'
import PaymentLinkPicker from '../../components/PaymentLinkPicker'
import PaymentHistoryModal from '../../components/PaymentHistoryModal'
import {paymentMethodLabel} from '../../utils/paymentPresentation'
import {useSearchParams} from 'react-router-dom'
import {getCurrentUser} from '../../services/authService'
import { categoryTypeLabel } from '../../utils/accountingCategoryPresentation'
import { useEffect, useMemo, useState, useRef } from 'react'
import type { FormEvent } from 'react'
import { AlertTriangle, ArrowDownLeft, ArrowUpRight, Download, FileText, Pencil, Plus, Receipt, RotateCcw, Trash2, Wallet, X } from 'lucide-react'
import financialTransactionService, { type AccountingCategory, type FinancialTransaction, type TransactionRequest, type TransactionType } from '../../services/financialTransactionService'
import dashboardService, { type FinancialDashboard } from '../../services/dashboardService'
import exportService from '../../services/exportService'
import { getErrorMessage } from '../../services/authService'
import ContentCard from '../../components/ui/ContentCard'
import EmptyState from '../../components/ui/EmptyState'
import ErrorState from '../../components/ui/ErrorState'
import FilterBar from '../../components/ui/FilterBar'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import StatCard from '../../components/StatCard'
import Toast, { type ToastTone } from '../../components/ui/Toast'


const emptyForm: TransactionRequest = { transactionType: 'EXPENSE', amount: '', transactionDate: businessToday(), paymentConfirmed: false }

/** Sổ quỹ phân trang theo cùng quy ước với danh sách chứng từ. */
const PAGE_SIZE = 20
const PAGE_SIZE_OPTIONS = [20, 50, 100]

/** Tab lọc nhanh theo loại giao dịch (Stitch: Tất cả / Khoản Thu / Khoản Chi). */
const TYPE_TABS: { key: 'ALL' | TransactionType; label: string }[] = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'INCOME', label: 'Khoản thu' },
  { key: 'EXPENSE', label: 'Khoản chi' },
]

const SORT_OPTIONS = [
  { value: 'transactionDate,desc', label: 'Ngày mới nhất' },
  { value: 'transactionDate,asc', label: 'Ngày cũ nhất' },
  { value: 'amount,desc', label: 'Số tiền giảm dần' },
  { value: 'amount,asc', label: 'Số tiền tăng dần' },
]

const money = (value: number) => `${Number(value || 0).toLocaleString('vi-VN')} đ`

const FinancialTransactions = () => {
  const [searchParams,setSearchParams]=useSearchParams()
  const [methods,setMethods]=useState<string[]>([]),[methodError,setMethodError]=useState('')
  const [linkReady,setLinkReady]=useState(true),[original,setOriginal]=useState<FinancialTransaction|undefined>()
  const [historyTarget,setHistoryTarget]=useState<FinancialTransaction|null>(null),[cancelReason,setCancelReason]=useState('')
  const [unverifiedOnly,setUnverifiedOnly]=useState(false)
  const companyId=Number(searchParams.get('companyId'))||getCurrentUser()?.companyId||undefined
  useEffect(()=>{void financialTransactionService.methods().then(setMethods).catch(e=>setMethodError(getErrorMessage(e,'Không tải được phương thức thanh toán.')))},[])

  const [transactions, setTransactions] = useState<FinancialTransaction[]>([])
  const [categories, setCategories] = useState<AccountingCategory[]>([])
  const [categoryError,setCategoryError] = useState('')
  const [categoriesLoading,setCategoriesLoading] = useState(true)
  /** Tổng thu / chi / dòng tiền lấy từ server nên thẻ KPI không phụ thuộc số bản ghi của trang hiện tại. */
  const [financial, setFinancial] = useState<FinancialDashboard | null>(null)
  const [financialError,setFinancialError]=useState('')
  const [refreshCounter,setRefreshCounter]=useState(0)
  const [transactionType, setTransactionType] = useState<'ALL' | TransactionType>('ALL')
  const time=useFinancialTimeFilter()
  const {dateFrom,dateTo,filterError}=time
  const [categoryId, setCategoryId] = useState('')
  const [sort, setSort] = useState(SORT_OPTIONS[0].value)
  const [pageNumber, setPageNumber] = useState(1)
  const [pageSize, setPageSize] = useState(PAGE_SIZE)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [exporting, setExporting] = useState(false)
  const [error, setError] = useState('')
  const [feedback, setFeedback] = useState<{ tone: ToastTone; message: string } | null>(null)

  /** Biểu mẫu thu / chi nằm trong hộp thoại để sổ quỹ luôn là vùng nội dung chính của trang. */
  const [formOpen, setFormOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<TransactionRequest>(emptyForm)
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const paymentEdits=useRef<PaymentEdits>({}),submitInFlight=useRef(false)
  const changePaymentField=<K extends keyof TransactionRequest>(field:K,value:TransactionRequest[K])=>{
    if(['amount','description','categoryId','transactionType'].includes(field))paymentEdits.current[field as keyof PaymentEdits]=true
    setForm(current=>{const next={...current,[field]:value,paymentConfirmed:field==='paymentConfirmed'?value: false} as TransactionRequest;
      if(field==='transactionType'&&next.categoryId){const category=categories.find(c=>c.id===next.categoryId);if(category?.categoryType&&category.categoryType!==next.transactionType)next.categoryId=undefined;}
      return next})
  }

  /** Xác nhận xóa ngay trong ứng dụng thay cho hộp thoại mặc định của trình duyệt. */
  const [deleteTarget, setDeleteTarget] = useState<FinancialTransaction | null>(null)
  const [deleting, setDeleting] = useState(false)

  const invalidRange = Boolean(filterError)
  const firstPage = () => setPageNumber(1)

  const loadSequence=useRef(0)
  const load = async () => {
    const request=++loadSequence.current
    if (filterError) {
      setError(filterError)
      setLoading(false)
      return
    }
    setLoading(true)
    setError('')
    try {
      const page = await (unverifiedOnly?financialTransactionService.unverified:financialTransactionService.list)({
        companyId,
        page: pageNumber - 1,
        size: pageSize,
        sort,
        transactionType: transactionType === 'ALL' ? undefined : transactionType,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        categoryId: categoryId || undefined,
      })
      if(request!==loadSequence.current)return
      setTransactions(page.content)
      setTotalPages(page.totalPages)
      setTotalElements(page.totalElements)
    } catch (requestError: unknown) {
      if(request===loadSequence.current)setError(apiLoadError(requestError, 'Không thể tải dữ liệu thu chi.'))
    } finally {
      if(request===loadSequence.current)setLoading(false)
    }
  }

  useEffect(() => { void load();return ()=>{loadSequence.current++} }, [transactionType, dateFrom, dateTo, categoryId, sort, pageNumber, pageSize,unverifiedOnly,companyId])

  const loadCategories = async () => {
    setCategoriesLoading(true);setCategoryError('')
    try {setCategories(await financialTransactionService.getCategories(companyId))}
    catch(e){setCategories([]);setCategoryError(getErrorMessage(e,'Không tải được danh mục kế toán; danh sách này chưa được xác định.'))}
    finally {setCategoriesLoading(false)}
  }
  useEffect(() => { void loadCategories() }, [companyId])

  useEffect(() => {
    let active = true
    setFinancial(null);setFinancialError('')
    if(filterError){setFinancialError(filterError);return;}
    // Cùng nguồn số liệu với trang Báo cáo tài chính để hai màn hình luôn khớp nhau.
    void dashboardService.getFinancialDashboard({ companyId, dateFrom: dateFrom || undefined, dateTo: dateTo || undefined })
      .then((data) => { if (active) setFinancial(data) })
      .catch(e => {if(active)setFinancialError(apiLoadError(e,'Không tải được tổng thu/chi đã xác nhận.'))})
    return () => { active = false }
  }, [dateFrom, dateTo,companyId,refreshCounter,filterError])

  const openCreate = (type: TransactionType) => {
    paymentEdits.current={}
    setEditingId(null)
    setOriginal(undefined)
    setLinkReady(true)
    setFormError('')
    setForm({ ...emptyForm, transactionDate: businessToday(), transactionType: type,companyId,requestKey:crypto.randomUUID() })
    setFormOpen(true)
  }

  const openEdit = (item: FinancialTransaction) => {
    paymentEdits.current={}
    if(item.paymentConfirmation!=='CONFIRMED'){setHistoryTarget(item);return}
    setOriginal(item);setLinkReady(!(item.documentId||item.invoiceId))
    setEditingId(item.id)
    setFormError('')
    setForm({
      transactionType: item.transactionType,
      amount: String(item.amount),
      companyId:item.companyId,
      expectedUpdatedAt:item.updatedAt,
      paymentConfirmed:false,
      transactionDate: item.transactionDate,
      description: item.description || undefined,
      categoryId: item.categoryId || undefined,
      documentId: item.documentId || undefined,
      invoiceId: item.invoiceId || undefined,
      paymentMethod: item.paymentMethod || undefined,
    })
    setFormOpen(true)
  }

  useEffect(()=>{
    const document=Number(searchParams.get('documentId')),invoice=Number(searchParams.get('invoiceId'))
    if(document>0||invoice>0){paymentEdits.current={};setEditingId(null);setOriginal(undefined);setLinkReady(false);setForm({...emptyForm,companyId,transactionType:searchParams.get('type')==='INCOME'?'INCOME':'EXPENSE',documentId:document||undefined,invoiceId:invoice||undefined,requestKey:crypto.randomUUID()});setFormOpen(true);const next=new URLSearchParams(searchParams);next.delete('documentId');next.delete('invoiceId');next.delete('type');setSearchParams(next,{replace:true})}
  },[searchParams,setSearchParams,companyId])
  const closeForm = () => { setFormOpen(false); setEditingId(null); setForm(emptyForm); setFormError('') }

  const setNumber = (key: 'categoryId', value: string) => changePaymentField(key,value ? Number(value) : undefined)

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if(submitInFlight.current)return
    setFormError('')
    const validation=paymentFormError(form,methods,categories);if(validation){setFormError(validation);return}
    if(!form.paymentConfirmed){setFormError('Cần xác nhận khoản tiền đã thực thu/chi.');return}
    if(!linkReady||!methods.includes(form.paymentMethod||'')){setFormError('Chọn hồ sơ và phương thức hợp lệ.');return}
    submitInFlight.current=true
    setSubmitting(true)
    try {
      const targetId = editingId
      const saved=targetId!=null?await financialTransactionService.update(targetId,form):await financialTransactionService.create(form)
      if(saved.paymentConfirmation!=='CONFIRMED'){setFeedback({tone:'warning',message:'Request trước đã bị hủy; không tạo thêm giao dịch.'});closeForm();await load();return}
      setFeedback({
        tone: 'success',
        message: targetId != null
          ? `Đã cập nhật giao dịch #${targetId}.`
          : `Đã ghi nhận ${form.transactionType === 'INCOME' ? 'phiếu thu' : 'phiếu chi'} ${money(Number(form.amount))}.`,
      })
      closeForm()
      await load();setRefreshCounter(v=>v+1)
    } catch (requestError: unknown) {
      setFormError(paymentSaveError(requestError))
    } finally { submitInFlight.current=false;setSubmitting(false) }
  }

  const removeTransaction = async () => {
    if (!deleteTarget) return
    const target = deleteTarget
    setDeleting(true)
    try {
      await financialTransactionService.cancel(target.id,cancelReason,target.updatedAt)
      setDeleteTarget(null)
      setFeedback({ tone: 'success', message: `Đã hủy giao dịch #${target.id}; lịch sử được giữ nguyên.` })
      await load();setRefreshCounter(v=>v+1)
    } catch (requestError: unknown) {
      setDeleteTarget(null)
      setError(getErrorMessage(requestError, 'Không thể hủy giao dịch.'))
    } finally { setDeleting(false) }
  }

  const exportReport = async () => {
    const rangeError=filterError;if(rangeError){setFeedback({tone:'error',message:rangeError});return;}
    setExporting(true)
    setFeedback(null)
    try {
      await exportService.exportFinancialReport({ companyId, fromDate: dateFrom || undefined, toDate: dateTo || undefined })
      setFeedback({ tone: 'success', message: 'Đã xuất sổ quỹ ra tệp Excel.' })
    } catch (requestError: unknown) {
      setFeedback({ tone: 'error', message: getErrorMessage(requestError, 'Không thể xuất sổ quỹ. Vui lòng thử lại.') })
    } finally { setExporting(false) }
  }

  const resetFilters = () => {
    setTransactionType('ALL')
    time.update(defaultFinancialFilter())
    setCategoryId('')
    setSort(SORT_OPTIONS[0].value)
    firstPage()
  }

  const hasFilters = transactionType !== 'ALL' || Boolean(dateFrom || dateTo || categoryId)

  const activeFilters = useMemo(() => {
    const chips: { key: string; label: string; clear: () => void }[] = []
    if (dateFrom) chips.push({ key: 'from', label: `Từ ${formatDate(dateFrom)}`, clear: () => { time.update({...time.filter,mode:'RANGE',from:defaultFinancialFilter().from}); firstPage() } })
    if (dateTo) chips.push({ key: 'to', label: `Đến ${formatDate(dateTo)}`, clear: () => { time.update({...time.filter,mode:'RANGE',to:defaultFinancialFilter().to}); firstPage() } })
    if (categoryId) chips.push({ key: 'category', label: `Nhóm: ${categories.find((item) => String(item.id) === categoryId)?.categoryName ?? categoryId}`, clear: () => { setCategoryId(''); firstPage() } })
    return chips
  }, [categories, categoryId, dateFrom, dateTo])

  const fieldErrors=paymentFieldErrors(form,methods,categories)
  const rangeFrom = totalElements === 0 ? 0 : (pageNumber - 1) * pageSize + 1
  const rangeTo = Math.min(pageNumber * pageSize, totalElements)

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        eyebrow="Phân hệ quản trị vốn & kế toán quỹ"
        title="Quản lý Thu / Chi & Sổ Quỹ Tiền tệ"
        description="Theo dõi dòng tiền thu chi thực tế, đối soát khớp nối với chứng từ hóa đơn và tài khoản thanh toán."
        actions={
          /*
            Nhóm nút nghiệp vụ căn phải: khi bề ngang không đủ, nút "Tạo phiếu Thu" xuống dòng vẫn
            thẳng lề phải thay vì nhảy về lề trái như trước.
          */
          <div className="flex flex-wrap items-center justify-end gap-3">
            <button type="button" onClick={() => void exportReport()} disabled={exporting} className="si-btn-secondary">
              <Download size={20} />{exporting ? 'Đang xuất...' : 'Xuất sổ quỹ (Excel)'}
            </button>
            <button type="button" onClick={() => openCreate('EXPENSE')} className="si-btn-secondary">
              <ArrowUpRight size={20} />Tạo phiếu Chi
            </button>
            <button type="button" onClick={() => openCreate('INCOME')} className="si-btn-primary">
              <ArrowDownLeft size={20} />Tạo phiếu Thu
            </button>
          </div>
        }
      />

      <label className="flex gap-2 text-sm"><input type="checkbox" checked={unverifiedOnly} onChange={e=>{setUnverifiedOnly(e.target.checked);setTransactionType('ALL');setCategoryId('');firstPage()}}/>Danh sách giao dịch cũ chưa xác minh (cần đối soát thủ công)</label>
      <p className="text-sm text-review">Giao dịch cũ chưa xác minh: {financial?.unverifiedTransactionCount??(financialError?'Lỗi tải dữ liệu':'Đang tải...')}. Không được tính là tiền thực thu/chi.</p>
      <p className="text-sm text-ink-muted">Tổng thu/chi chỉ tính phiếu xác nhận thanh toán; giao dịch cũ chưa xác minh và phiếu hủy không được tính.</p>
      {feedback && <Toast tone={feedback.tone}>{feedback.message}</Toast>}
      {financialError&&<ErrorState message={financialError}/>}
      {categoryError && <ErrorState message={categoryError} onRetry={() => void loadCategories()} />}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard title="Tổng thu trong kỳ" value={financial?Number(financial.totalRevenue):financialError?'Lỗi tải dữ liệu':'Đang tải...'} unit="đ" icon={ArrowDownLeft} type="success" hint="Tổng hợp theo khoảng ngày đang lọc" />
        <StatCard title="Tổng chi trong kỳ" value={financial?Number(financial.totalExpense):financialError?'Lỗi tải dữ liệu':'Đang tải...'} unit="đ" icon={ArrowUpRight} type="error" hint="Tổng hợp theo khoảng ngày đang lọc" />
        <StatCard title="Dòng tiền thuần (Net)" value={financial?Number(financial.cashFlow):financialError?'Lỗi tải dữ liệu':'Đang tải...'} unit="đ" icon={Wallet} type="primary" hint="Tổng thu trừ tổng chi trong kỳ" />
        <StatCard title="Giao dịch theo bộ lọc" value={loading?'Đang tải...':error?'Lỗi tải dữ liệu':totalElements} unit="giao dịch" icon={FileText} type="default" hint="Khớp loại giao dịch, khoảng ngày và nhóm khoản mục đang chọn" />
      </div>

      <ContentCard>
        <div className="border-hairline flex flex-col gap-3 border-b p-4 lg:flex-row lg:items-center lg:justify-between">
          {/* Tab loại giao dịch thay cho select trùng lặp trong bộ lọc (Stitch: Tất cả / Khoản Thu / Khoản Chi). */}
          <div className="border-hairline bg-canvas inline-flex shrink-0 items-center gap-1 self-start rounded-control border p-1" role="group" aria-label="Lọc theo loại giao dịch">
            {TYPE_TABS.map((tab) => (
              <button
                key={tab.key}
                type="button"
                disabled={unverifiedOnly}
                aria-pressed={transactionType === tab.key}
                onClick={() => { setTransactionType(tab.key); firstPage() }}
                className={`rounded-control px-4 py-2 text-[17px] font-semibold transition-colors ${transactionType === tab.key ? 'bg-brand-600 text-white shadow-elev1' : 'text-ink-soft hover:bg-subtle hover:text-ink'}`}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <span className="si-num text-[17px] text-ink-soft">{loading ? 'Đang tải...' : `${totalElements.toLocaleString('vi-VN')} giao dịch`}</span>
            <button type="button" onClick={() => void load()} disabled={loading} title="Tải lại sổ quỹ" aria-label="Tải lại sổ quỹ" className="si-btn-secondary !px-3 !py-2">
              <RotateCcw size={20} />
            </button>
          </div>
        </div>

        <div className="border-hairline border-b p-4">
          <FilterBar actions={<button type="button" onClick={resetFilters} disabled={!hasFilters} className="si-btn-ghost"><RotateCcw size={20} />Đặt lại</button>}>
            <FinancialTimeFilter value={time.filter} onChange={next=>{time.update(next);firstPage()}} error={filterError} disabled={unverifiedOnly}/>
            <label className="grid gap-1"><span className="si-field-label">Nhóm chi phí</span><select disabled={unverifiedOnly || categoriesLoading || Boolean(categoryError)} value={categoryId} onChange={(event) => { setCategoryId(event.target.value); firstPage() }} className="si-select"><option value="">Tất cả nhóm</option>{categories.map((item) => <option key={item.id} value={item.id}>{item.categoryName} · {categoryTypeLabel(item.categoryType)}</option>)}</select></label>
            <label className="grid gap-1"><span className="si-field-label">Sắp xếp</span><select value={sort} onChange={(event) => { setSort(event.target.value); firstPage() }} className="si-select">{SORT_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
          </FilterBar>

          {(invalidRange || activeFilters.length > 0) && (
            <div className="mt-3 flex flex-wrap items-center gap-2">
              {invalidRange && <span className="si-chip border-failed/30 bg-failed-surface text-failed"><AlertTriangle size={18} />{filterError}</span>}
              {activeFilters.length > 0 && <span className="text-2xs text-ink-muted">Đang lọc:</span>}
              {activeFilters.map((chip) => (
                <span key={chip.key} className="si-chip border-hairline bg-subtle text-ink-soft">
                  {chip.label}
                  <button type="button" onClick={chip.clear} title="Bỏ điều kiện này" className="text-ink-muted hover:text-ink"><X size={20} /></button>
                </span>
              ))}
            </div>
          )}
        </div>

        {error ? (
          <div className="p-4"><ErrorState message={error} onRetry={() => void load()} /></div>
        ) : loading ? (
          <LoadingState label="Đang tải giao dịch..." />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="si-table">
                <thead>
                  <tr>
                    <th>Ngày GD</th>
                    <th>Loại</th>
                    <th>Nhóm khoản mục</th>
                    <th>Nội dung diễn giải</th>
                    <th>Phương thức</th>
                    <th className="text-right">Số tiền (VND)</th>
                    <th>Chứng từ / hóa đơn</th>
                    <th className="text-center">Tùy chọn</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.length === 0 ? (
                    <tr>
                      <td colSpan={8}>
                        <EmptyState
                          title="Chưa có giao dịch"
                          description="Thay đổi bộ lọc hoặc ghi nhận phiếu thu / phiếu chi đầu tiên."
                          action={<button type="button" onClick={() => openCreate('INCOME')} className="si-btn-primary"><Plus size={20} />Tạo phiếu Thu</button>}
                        />
                      </td>
                    </tr>
                  ) : transactions.map((item) => {
                    const isIncome = item.transactionType === 'INCOME'
                    return (
                      <tr key={item.id} onClick={() => openEdit(item)} className="cursor-pointer">
                        <td>
                          <div className="flex flex-col">
                            <span className="si-num text-[17px] font-semibold text-ink">{formatDate(item.transactionDate)}</span>
                            <span className="si-num text-2xs text-ink-muted">GD #{item.id}</span><span className="text-xs text-review">{item.paymentConfirmation==='CONFIRMED'?'Đã xác nhận tiền':item.paymentConfirmation==='CANCELLED'?'Đã hủy':'Chưa xác minh · không tính thanh toán'}</span>
                          </div>
                        </td>
                        <td>
                          <span className={`si-chip whitespace-nowrap ${isIncome ? 'border-parsed/30 bg-parsed-surface text-parsed' : 'border-failed/30 bg-failed-surface text-failed'}`}>
                            {isIncome ? 'Thu (+)' : 'Chi (−)'}
                          </span>
                        </td>
                        <td className="text-ink-soft">{item.categoryName || 'Chưa phân nhóm'}</td>
                        <td><span className="block max-w-72 truncate text-ink" title={item.description || undefined}>{item.description || '—'}</span></td>
                        <td className="text-ink-soft">{item.paymentMethod || '—'}</td>
                        <td className={`si-num whitespace-nowrap text-right text-[17px] font-semibold ${isIncome ? 'text-parsed' : 'text-failed'}`}>{isIncome ? '+' : '−'} {money(Number(item.amount))}</td>
                        <td>
                          {item.documentId || item.invoiceId ? (
                            <div className="flex flex-wrap items-center gap-1.5">
                              {item.documentId ? <span className="si-chip border-hairline bg-subtle text-ink-soft whitespace-nowrap"><FileText size={18} />CT #{item.documentId}</span> : null}
                              {item.invoiceId ? <span className="si-chip border-hairline bg-subtle text-ink-soft whitespace-nowrap"><Receipt size={18} />HĐ #{item.invoiceId}</span> : null}
                            </div>
                          ) : (
                            <span className="si-chip border-review/30 bg-review-surface text-review whitespace-nowrap"><AlertTriangle size={18} />Chưa gắn chứng từ</span>
                          )}
                        </td>
                        <td>
                          <div className="flex items-center justify-center gap-1"><button type="button" className="si-btn-secondary" onClick={e=>{e.stopPropagation();setHistoryTarget(item)}}>Lịch sử</button>
                            <button type="button" onClick={(event) => { event.stopPropagation(); openEdit(item) }} disabled={item.paymentConfirmation!=='CONFIRMED'} title="Sửa giao dịch" aria-label={`Sửa giao dịch ${item.id}`} className="text-ink-muted hover:bg-subtle hover:text-brand-600 inline-flex items-center justify-center rounded p-1.5 transition-colors">
                              <Pencil size={20} />
                            </button>
                            <button type="button" onClick={(event) => { event.stopPropagation(); setCancelReason(''); setDeleteTarget(item) }} disabled={item.paymentConfirmation!=='CONFIRMED'} title="Hủy giao dịch" aria-label={`Hủy giao dịch ${item.id}`} className="text-ink-muted hover:bg-failed-surface hover:text-failed inline-flex items-center justify-center rounded p-1.5 transition-colors">
                              <Trash2 size={20} />
                            </button>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>

            <div className="border-hairline flex flex-col items-center justify-between gap-3 border-t px-4 py-3 sm:flex-row">
              <span className="si-num text-[17px] text-ink-soft">
                {totalElements === 0 ? 'Không có bản ghi' : `Hiển thị ${rangeFrom.toLocaleString('vi-VN')}–${rangeTo.toLocaleString('vi-VN')} trong số ${totalElements.toLocaleString('vi-VN')} giao dịch`}
              </span>
              <div className="flex flex-wrap items-center gap-3">
                <label className="flex items-center gap-2 text-[17px] text-ink-soft">
                  Mỗi trang
                  <select value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPageNumber(1) }} className="si-select w-auto" aria-label="Số dòng mỗi trang">
                    {PAGE_SIZE_OPTIONS.map((option) => <option key={option} value={option}>{option}</option>)}
                  </select>
                </label>
                <div className="flex items-center gap-2">
                  <button type="button" disabled={loading || pageNumber <= 1} onClick={() => setPageNumber((page) => page - 1)} className="si-btn-secondary">Trước</button>
                  <span className="si-num px-1 text-[17px] text-ink-soft">{pageNumber}/{Math.max(1, totalPages)}</span>
                  <button type="button" disabled={loading || totalPages === 0 || pageNumber >= totalPages} onClick={() => setPageNumber((page) => page + 1)} className="si-btn-secondary">Tiếp</button>
                </div>
              </div>
            </div>
          </>
        )}
      </ContentCard>

      {formOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 p-4" onMouseDown={()=>{if(!submitting)closeForm()}}>
          <form
            onSubmit={submit}
            role="dialog"
            aria-modal="true"
            aria-labelledby="transaction-form-title"
            onMouseDown={(event) => event.stopPropagation()}
            className="si-panel max-h-[calc(100vh/var(--ui-zoom)_-_4rem)] w-full max-w-2xl overflow-y-auto p-6 shadow-elev3"
          >
            <div className="mb-5 flex items-start justify-between gap-4">
              <div>
                <h2 id="transaction-form-title" className="si-section-title">
                  {editingId != null ? `Cập nhật giao dịch #${editingId}` : form.transactionType === 'INCOME' ? 'Ghi nhận phiếu Thu (+)' : 'Ghi nhận phiếu Chi (−)'}
                </h2>
                <p className="si-section-subtitle mt-1">Mỗi giao dịch chỉ liên kết một chứng từ hoặc một hóa đơn.</p>
              </div>
              <button type="button" disabled={submitting} onClick={closeForm} aria-label="Đóng hộp thoại" className="text-ink-muted hover:bg-subtle hover:text-ink rounded p-1.5 transition-colors"><X size={22} /></button>
            </div>

            {formError && <div className="mb-4"><Toast tone="error">{formError}</Toast></div>}

            <fieldset disabled={submitting} className="grid gap-4 sm:grid-cols-2">
              <label className="grid gap-1">
                <span className="si-field-label">Loại giao dịch</span>
                <select value={form.transactionType} onChange={(event) => changePaymentField('transactionType',event.target.value as TransactionType)} className="si-select">
                  <option value="INCOME">Thu (+) — tiền vào quỹ</option>
                  <option value="EXPENSE">Chi (−) — tiền ra khỏi quỹ</option>
                </select>
                {fieldErrors.transactionType&&<p role="alert" className="text-sm text-failed">{fieldErrors.transactionType}</p>}
              </label>
              <label className="grid gap-1">
                <span className="si-field-label">Ngày giao dịch</span>
                <DateInput  required value={form.transactionDate} onChange={(event) => changePaymentField('transactionDate',event.target.value)} className="si-input" />
                {fieldErrors.transactionDate&&<p role="alert" className="text-sm text-failed">{fieldErrors.transactionDate}</p>}
              </label>
              <label className="grid gap-1 sm:col-span-2">
                <span className="si-field-label">Số tiền (đ)</span>
                <input type="number" min="0.01" step="0.01" required value={form.amount || ''} onChange={(event) => changePaymentField('amount',event.target.value)} className="si-input si-num" placeholder="0" />
                <span className="si-num text-[17px] text-ink-muted">{Number(form.amount) > 0 ? `= ${money(Number(form.amount))}` : 'Nhập số tiền bằng đồng Việt Nam'}</span>
                {fieldErrors.amount&&<p role="alert" className="text-sm text-failed">{fieldErrors.amount}</p>}
              </label>
              <label className="grid gap-1">
                <span className="si-field-label">Nhóm chi phí</span>
                <select disabled={categoriesLoading || Boolean(categoryError)} value={form.categoryId || ''} onChange={(event) => setNumber('categoryId', event.target.value)} className="si-select">
                  <option value="">Chưa phân nhóm</option>
                  {categories.map((item) => <option key={item.id} value={item.id}>{item.categoryName} · {categoryTypeLabel(item.categoryType)}</option>)}
                </select>
                {fieldErrors.categoryId&&<p role="alert" className="text-sm text-failed">{fieldErrors.categoryId}</p>}
              </label>
              <label className="grid gap-1">
                <span className="si-field-label">Phương thức thanh toán</span>
                <select required className="si-select" value={form.paymentMethod||''} onChange={e=>changePaymentField('paymentMethod',e.target.value)}><option value="">Chọn phương thức</option>{methods.map(method=><option key={method} value={method}>{paymentMethodLabel(method)}</option>)}</select>
                {methodError&&<p className="text-sm text-failed">{methodError}</p>}
                {fieldErrors.paymentMethod&&<p role="alert" className="text-sm text-failed">{fieldErrors.paymentMethod}</p>}
              </label>
              <PaymentLinkPicker form={form} onChange={setForm} original={original} onReady={setLinkReady} editedFields={paymentEdits.current} onSelectionChange={()=>{paymentEdits.current={}}}/>
              <label className="grid gap-1 sm:col-span-2">
                <span className="si-field-label">Diễn giải</span>
                <textarea value={form.description || ''} onChange={(event) => changePaymentField('description',event.target.value)} className="si-input min-h-20" placeholder="Diễn giải nội dung thu / chi" />
              </label>
            </fieldset>

            <label className="mt-3 flex gap-2 text-sm"><input type="checkbox" disabled={submitting} checked={Boolean(form.paymentConfirmed)} onChange={e=>changePaymentField('paymentConfirmed',e.target.checked)}/>Tôi xác nhận đây là số tiền đã thực thu/thực chi, không chỉ là giá trị chứng từ đã duyệt.</label>
            {fieldErrors.paymentConfirmed&&<p role="alert" className="mt-1 text-sm text-failed">{fieldErrors.paymentConfirmed}</p>}
            <p className="text-2xs text-ink-muted mt-3">Liên kết chứng từ hoặc hóa đơn giúp đối soát sổ quỹ với hồ sơ gốc; để trống nếu giao dịch chưa có chứng từ.</p>

            <div className="border-hairline mt-5 flex flex-wrap items-center justify-end gap-3 border-t pt-4">
              <button type="button" onClick={closeForm} className="si-btn-secondary">Hủy</button>
              {!linkReady&&<p className="w-full text-sm text-review">Chưa thể lưu: kiểm tra lựa chọn hồ sơ, trạng thái preview, số dư và cảnh báo Thu/Chi ở trên.</p>}
              <button type="submit" disabled={submitting||!linkReady||Boolean(methodError)||Boolean(paymentFormError(form,methods,categories))||(Boolean(form.categoryId)&&(categoriesLoading||Boolean(categoryError)))} className="si-btn-primary">
                <Plus size={20} />{submitting ? 'Đang lưu...' : editingId != null ? 'Lưu thay đổi' : 'Ghi nhận giao dịch'}
              </button>
            </div>
          </form>
        </div>
      )}

      {historyTarget&&<PaymentHistoryModal transaction={historyTarget} onClose={()=>setHistoryTarget(null)}/>}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 p-4" onMouseDown={() => setDeleteTarget(null)}>
          <div role="dialog" aria-modal="true" aria-labelledby="delete-transaction-title" onMouseDown={(event) => event.stopPropagation()} className="si-panel w-full max-w-md p-6 shadow-elev3">
            <div className="mb-4 flex items-start justify-between gap-4">
              <div>
                <h2 id="delete-transaction-title" className="si-section-title">Hủy giao dịch #{deleteTarget.id}</h2>
                <p className="si-section-subtitle mt-1">Phiếu và lịch sử được giữ nguyên; khoản hủy không được tính thanh toán.</p>
              </div>
              <button type="button" onClick={() => setDeleteTarget(null)} aria-label="Đóng hộp thoại" className="text-ink-muted hover:bg-subtle hover:text-ink rounded p-1.5 transition-colors"><X size={22} /></button>
            </div>

            <label className="mb-3 grid gap-1">Lý do hủy<textarea required maxLength={1000} className="si-input" value={cancelReason} onChange={e=>setCancelReason(e.target.value)}/></label>
            <dl className="bg-canvas border-hairline grid gap-2.5 rounded-control border p-4 text-[17px]">
              <div className="flex items-center justify-between gap-4">
                <dt className="text-ink-soft">Loại giao dịch</dt>
                <dd className={deleteTarget.transactionType === 'INCOME' ? 'text-parsed font-semibold' : 'text-failed font-semibold'}>{deleteTarget.transactionType === 'INCOME' ? 'Phiếu thu (+)' : 'Phiếu chi (−)'}</dd>
              </div>
              <div className="flex items-center justify-between gap-4">
                <dt className="text-ink-soft">Ngày giao dịch</dt>
                <dd className="si-num text-ink">{formatDate(deleteTarget.transactionDate)}</dd>
              </div>
              <div className="flex items-center justify-between gap-4">
                <dt className="text-ink-soft">Số tiền</dt>
                <dd className="si-num font-semibold text-ink">{money(Number(deleteTarget.amount))}</dd>
              </div>
              <div className="flex items-center justify-between gap-4">
                <dt className="text-ink-soft">Diễn giải</dt>
                <dd className="max-w-64 truncate text-ink" title={deleteTarget.description || undefined}>{deleteTarget.description || '—'}</dd>
              </div>
            </dl>

            <div className="border-hairline mt-5 flex flex-wrap items-center justify-end gap-3 border-t pt-4">
              <button type="button" onClick={() => setDeleteTarget(null)} className="si-btn-secondary">Hủy</button>
              <button type="button" onClick={() => void removeTransaction()} disabled={deleting||!cancelReason.trim()} className="si-btn-danger">
                <Trash2 size={20} />{deleting ? 'Đang hủy...' : 'Hủy giao dịch'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default FinancialTransactions


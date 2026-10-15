import { useEffect, useMemo, useState } from 'react'
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

const today = new Date().toISOString().slice(0, 10)
const emptyForm: TransactionRequest = { transactionType: 'EXPENSE', amount: 0, transactionDate: today }

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
  const [transactions, setTransactions] = useState<FinancialTransaction[]>([])
  const [categories, setCategories] = useState<AccountingCategory[]>([])
  /** Tổng thu / chi / dòng tiền lấy từ server nên thẻ KPI không phụ thuộc số bản ghi của trang hiện tại. */
  const [financial, setFinancial] = useState<FinancialDashboard | null>(null)
  const [transactionType, setTransactionType] = useState<'ALL' | TransactionType>('ALL')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
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

  /** Xác nhận xóa ngay trong ứng dụng thay cho hộp thoại mặc định của trình duyệt. */
  const [deleteTarget, setDeleteTarget] = useState<FinancialTransaction | null>(null)
  const [deleting, setDeleting] = useState(false)

  const invalidRange = Boolean(dateFrom && dateTo && dateFrom > dateTo)
  const firstPage = () => setPageNumber(1)

  const load = async () => {
    if (dateFrom && dateTo && dateFrom > dateTo) {
      setError('Từ ngày không được sau đến ngày.')
      setLoading(false)
      return
    }
    setLoading(true)
    setError('')
    try {
      const page = await financialTransactionService.list({
        page: pageNumber - 1,
        size: pageSize,
        sort,
        transactionType: transactionType === 'ALL' ? undefined : transactionType,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        categoryId: categoryId || undefined,
      })
      setTransactions(page.content)
      setTotalPages(page.totalPages)
      setTotalElements(page.totalElements)
    } catch (requestError: unknown) {
      setError(getErrorMessage(requestError, 'Không thể tải dữ liệu thu chi.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [transactionType, dateFrom, dateTo, categoryId, sort, pageNumber, pageSize])

  useEffect(() => { void financialTransactionService.getCategories().then(setCategories).catch(() => undefined) }, [])

  useEffect(() => {
    let active = true
    // Cùng nguồn số liệu với trang Báo cáo tài chính để hai màn hình luôn khớp nhau.
    void dashboardService.getFinancialDashboard({ dateFrom: dateFrom || undefined, dateTo: dateTo || undefined })
      .then((data) => { if (active) setFinancial(data) })
      .catch(() => undefined)
    return () => { active = false }
  }, [dateFrom, dateTo])

  const openCreate = (type: TransactionType) => {
    setEditingId(null)
    setFormError('')
    setForm({ ...emptyForm, transactionType: type })
    setFormOpen(true)
  }

  const openEdit = (item: FinancialTransaction) => {
    setEditingId(item.id)
    setFormError('')
    setForm({
      transactionType: item.transactionType,
      amount: Number(item.amount),
      transactionDate: item.transactionDate,
      description: item.description || undefined,
      categoryId: item.categoryId || undefined,
      documentId: item.documentId || undefined,
      invoiceId: item.invoiceId || undefined,
      paymentMethod: item.paymentMethod || undefined,
    })
    setFormOpen(true)
  }

  const closeForm = () => { setFormOpen(false); setEditingId(null); setForm(emptyForm); setFormError('') }

  const setNumber = (key: 'categoryId' | 'documentId' | 'invoiceId', value: string) => setForm({ ...form, [key]: value ? Number(value) : undefined })

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setFormError('')
    if (!form.amount || Number(form.amount) <= 0) { setFormError('Số tiền phải lớn hơn 0.'); return }
    if (form.documentId && form.invoiceId) { setFormError('Chỉ liên kết một chứng từ hoặc một hóa đơn cho mỗi giao dịch.'); return }
    setSubmitting(true)
    try {
      const targetId = editingId
      if (targetId != null) await financialTransactionService.update(targetId, form)
      else await financialTransactionService.create(form)
      setFeedback({
        tone: 'success',
        message: targetId != null
          ? `Đã cập nhật giao dịch #${targetId}.`
          : `Đã ghi nhận ${form.transactionType === 'INCOME' ? 'phiếu thu' : 'phiếu chi'} ${money(Number(form.amount))}.`,
      })
      closeForm()
      await load()
    } catch (requestError: unknown) {
      setFormError(getErrorMessage(requestError, 'Không thể lưu giao dịch.'))
    } finally { setSubmitting(false) }
  }

  const removeTransaction = async () => {
    if (!deleteTarget) return
    const target = deleteTarget
    setDeleting(true)
    try {
      await financialTransactionService.remove(target.id)
      setDeleteTarget(null)
      setFeedback({ tone: 'success', message: `Đã xóa giao dịch #${target.id}.` })
      // Dòng cuối của trang có thể vừa bị xóa → lùi một trang thay vì hiện bảng rỗng.
      if (transactions.length === 1 && pageNumber > 1) setPageNumber((page) => page - 1)
      else await load()
    } catch (requestError: unknown) {
      setDeleteTarget(null)
      setError(getErrorMessage(requestError, 'Không thể xóa giao dịch.'))
    } finally { setDeleting(false) }
  }

  const exportReport = async () => {
    setExporting(true)
    setFeedback(null)
    try {
      await exportService.exportFinancialReport({ fromDate: dateFrom || undefined, toDate: dateTo || undefined })
      setFeedback({ tone: 'success', message: 'Đã xuất sổ quỹ ra tệp Excel.' })
    } catch (requestError: unknown) {
      setFeedback({ tone: 'error', message: getErrorMessage(requestError, 'Không thể xuất sổ quỹ. Vui lòng thử lại.') })
    } finally { setExporting(false) }
  }

  const resetFilters = () => {
    setTransactionType('ALL')
    setDateFrom('')
    setDateTo('')
    setCategoryId('')
    setSort(SORT_OPTIONS[0].value)
    firstPage()
  }

  const hasFilters = transactionType !== 'ALL' || Boolean(dateFrom || dateTo || categoryId)

  const activeFilters = useMemo(() => {
    const chips: { key: string; label: string; clear: () => void }[] = []
    if (dateFrom) chips.push({ key: 'from', label: `Từ ${dateFrom}`, clear: () => { setDateFrom(''); firstPage() } })
    if (dateTo) chips.push({ key: 'to', label: `Đến ${dateTo}`, clear: () => { setDateTo(''); firstPage() } })
    if (categoryId) chips.push({ key: 'category', label: `Nhóm: ${categories.find((item) => String(item.id) === categoryId)?.categoryName ?? categoryId}`, clear: () => { setCategoryId(''); firstPage() } })
    return chips
  }, [categories, categoryId, dateFrom, dateTo])

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

      {feedback && <Toast tone={feedback.tone}>{feedback.message}</Toast>}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard title="Tổng thu trong kỳ" value={Number(financial?.totalRevenue || 0)} unit="đ" icon={ArrowDownLeft} type="success" hint="Tổng hợp theo khoảng ngày đang lọc" />
        <StatCard title="Tổng chi trong kỳ" value={Number(financial?.totalExpense || 0)} unit="đ" icon={ArrowUpRight} type="error" hint="Tổng hợp theo khoảng ngày đang lọc" />
        <StatCard title="Dòng tiền thuần (Net)" value={Number(financial?.cashFlow || 0)} unit="đ" icon={Wallet} type="primary" hint="Tổng thu trừ tổng chi trong kỳ" />
        <StatCard title="Giao dịch theo bộ lọc" value={totalElements} unit="giao dịch" icon={FileText} type="default" hint="Khớp loại giao dịch, khoảng ngày và nhóm khoản mục đang chọn" />
      </div>

      <ContentCard>
        <div className="border-hairline flex flex-col gap-3 border-b p-4 lg:flex-row lg:items-center lg:justify-between">
          {/* Tab loại giao dịch thay cho select trùng lặp trong bộ lọc (Stitch: Tất cả / Khoản Thu / Khoản Chi). */}
          <div className="border-hairline bg-canvas inline-flex shrink-0 items-center gap-1 self-start rounded-control border p-1" role="group" aria-label="Lọc theo loại giao dịch">
            {TYPE_TABS.map((tab) => (
              <button
                key={tab.key}
                type="button"
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
            <label className="grid gap-1"><span className="si-field-label">Từ ngày</span><input type="date" value={dateFrom} onChange={(event) => { setDateFrom(event.target.value); firstPage() }} className="si-input" /></label>
            <label className="grid gap-1"><span className="si-field-label">Đến ngày</span><input type="date" value={dateTo} onChange={(event) => { setDateTo(event.target.value); firstPage() }} className="si-input" /></label>
            <label className="grid gap-1"><span className="si-field-label">Nhóm chi phí</span><select value={categoryId} onChange={(event) => { setCategoryId(event.target.value); firstPage() }} className="si-select"><option value="">Tất cả nhóm</option>{categories.map((item) => <option key={item.id} value={item.id}>{item.categoryName}</option>)}</select></label>
            <label className="grid gap-1"><span className="si-field-label">Sắp xếp</span><select value={sort} onChange={(event) => { setSort(event.target.value); firstPage() }} className="si-select">{SORT_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
          </FilterBar>

          {(invalidRange || activeFilters.length > 0) && (
            <div className="mt-3 flex flex-wrap items-center gap-2">
              {invalidRange && <span className="si-chip border-failed/30 bg-failed-surface text-failed"><AlertTriangle size={18} />Từ ngày không được sau đến ngày</span>}
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
                            <span className="si-num text-[17px] font-semibold text-ink">{new Date(item.transactionDate).toLocaleDateString('vi-VN')}</span>
                            <span className="si-num text-2xs text-ink-muted">GD #{item.id}</span>
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
                          <div className="flex items-center justify-center gap-1">
                            <button type="button" onClick={(event) => { event.stopPropagation(); openEdit(item) }} title="Sửa giao dịch" aria-label={`Sửa giao dịch ${item.id}`} className="text-ink-muted hover:bg-subtle hover:text-brand-600 inline-flex items-center justify-center rounded p-1.5 transition-colors">
                              <Pencil size={20} />
                            </button>
                            <button type="button" onClick={(event) => { event.stopPropagation(); setDeleteTarget(item) }} title="Xóa giao dịch" aria-label={`Xóa giao dịch ${item.id}`} className="text-ink-muted hover:bg-failed-surface hover:text-failed inline-flex items-center justify-center rounded p-1.5 transition-colors">
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 p-4" onMouseDown={closeForm}>
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
              <button type="button" onClick={closeForm} aria-label="Đóng hộp thoại" className="text-ink-muted hover:bg-subtle hover:text-ink rounded p-1.5 transition-colors"><X size={22} /></button>
            </div>

            {formError && <div className="mb-4"><Toast tone="error">{formError}</Toast></div>}

            <div className="grid gap-4 sm:grid-cols-2">
              <label className="grid gap-1">
                <span className="si-field-label">Loại giao dịch</span>
                <select value={form.transactionType} onChange={(event) => setForm({ ...form, transactionType: event.target.value as TransactionType })} className="si-select">
                  <option value="INCOME">Thu (+) — tiền vào quỹ</option>
                  <option value="EXPENSE">Chi (−) — tiền ra khỏi quỹ</option>
                </select>
              </label>
              <label className="grid gap-1">
                <span className="si-field-label">Ngày giao dịch</span>
                <input type="date" required value={form.transactionDate} onChange={(event) => setForm({ ...form, transactionDate: event.target.value })} className="si-input" />
              </label>
              <label className="grid gap-1 sm:col-span-2">
                <span className="si-field-label">Số tiền (đ)</span>
                <input type="number" min="0.01" step="0.01" required value={form.amount || ''} onChange={(event) => setForm({ ...form, amount: Number(event.target.value) })} className="si-input si-num" placeholder="0" />
                <span className="si-num text-[17px] text-ink-muted">{form.amount > 0 ? `= ${money(Number(form.amount))}` : 'Nhập số tiền bằng đồng Việt Nam'}</span>
              </label>
              <label className="grid gap-1">
                <span className="si-field-label">Nhóm chi phí</span>
                <select value={form.categoryId || ''} onChange={(event) => setNumber('categoryId', event.target.value)} className="si-select">
                  <option value="">Chưa phân nhóm</option>
                  {categories.map((item) => <option key={item.id} value={item.id}>{item.categoryName}</option>)}
                </select>
              </label>
              <label className="grid gap-1">
                <span className="si-field-label">Phương thức thanh toán</span>
                <input value={form.paymentMethod || ''} onChange={(event) => setForm({ ...form, paymentMethod: event.target.value })} className="si-input" placeholder="Chuyển khoản, tiền mặt..." />
              </label>
              <label className="grid gap-1">
                <span className="si-field-label">Mã chứng từ</span>
                <input type="number" min="1" value={form.documentId || ''} onChange={(event) => setNumber('documentId', event.target.value)} className="si-input si-num" placeholder="CT #" />
              </label>
              <label className="grid gap-1">
                <span className="si-field-label">Mã hóa đơn</span>
                <input type="number" min="1" value={form.invoiceId || ''} onChange={(event) => setNumber('invoiceId', event.target.value)} className="si-input si-num" placeholder="HĐ #" />
              </label>
              <label className="grid gap-1 sm:col-span-2">
                <span className="si-field-label">Diễn giải</span>
                <textarea value={form.description || ''} onChange={(event) => setForm({ ...form, description: event.target.value })} className="si-input min-h-20" placeholder="Diễn giải nội dung thu / chi" />
              </label>
            </div>

            <p className="text-2xs text-ink-muted mt-3">Liên kết chứng từ hoặc hóa đơn giúp đối soát sổ quỹ với hồ sơ gốc; để trống nếu giao dịch chưa có chứng từ.</p>

            <div className="border-hairline mt-5 flex flex-wrap items-center justify-end gap-3 border-t pt-4">
              <button type="button" onClick={closeForm} className="si-btn-secondary">Hủy</button>
              <button type="submit" disabled={submitting} className="si-btn-primary">
                <Plus size={20} />{submitting ? 'Đang lưu...' : editingId != null ? 'Lưu thay đổi' : 'Ghi nhận giao dịch'}
              </button>
            </div>
          </form>
        </div>
      )}

      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 p-4" onMouseDown={() => setDeleteTarget(null)}>
          <div role="dialog" aria-modal="true" aria-labelledby="delete-transaction-title" onMouseDown={(event) => event.stopPropagation()} className="si-panel w-full max-w-md p-6 shadow-elev3">
            <div className="mb-4 flex items-start justify-between gap-4">
              <div>
                <h2 id="delete-transaction-title" className="si-section-title">Xóa giao dịch #{deleteTarget.id}</h2>
                <p className="si-section-subtitle mt-1">Hành động này không thể hoàn tác.</p>
              </div>
              <button type="button" onClick={() => setDeleteTarget(null)} aria-label="Đóng hộp thoại" className="text-ink-muted hover:bg-subtle hover:text-ink rounded p-1.5 transition-colors"><X size={22} /></button>
            </div>

            <dl className="bg-canvas border-hairline grid gap-2.5 rounded-control border p-4 text-[17px]">
              <div className="flex items-center justify-between gap-4">
                <dt className="text-ink-soft">Loại giao dịch</dt>
                <dd className={deleteTarget.transactionType === 'INCOME' ? 'text-parsed font-semibold' : 'text-failed font-semibold'}>{deleteTarget.transactionType === 'INCOME' ? 'Phiếu thu (+)' : 'Phiếu chi (−)'}</dd>
              </div>
              <div className="flex items-center justify-between gap-4">
                <dt className="text-ink-soft">Ngày giao dịch</dt>
                <dd className="si-num text-ink">{new Date(deleteTarget.transactionDate).toLocaleDateString('vi-VN')}</dd>
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
              <button type="button" onClick={() => void removeTransaction()} disabled={deleting} className="si-btn-danger">
                <Trash2 size={20} />{deleting ? 'Đang xóa...' : 'Xóa giao dịch'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default FinancialTransactions


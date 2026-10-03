import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Download, Pencil, Plus, Trash2 } from 'lucide-react'
import financialTransactionService, { type AccountingCategory, type FinancialTransaction, type TransactionRequest, type TransactionType } from '../../services/financialTransactionService'
import { getErrorMessage } from '../../services/authService'
import ContentCard from '../../components/ui/ContentCard'
import EmptyState from '../../components/ui/EmptyState'
import ErrorState from '../../components/ui/ErrorState'
import FilterBar from '../../components/ui/FilterBar'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import exportService from '../../services/exportService'

const today = new Date().toISOString().slice(0, 10)
const emptyForm: TransactionRequest = { transactionType: 'EXPENSE', amount: 0, transactionDate: today }

const FinancialTransactions = () => {
  const [transactions, setTransactions] = useState<FinancialTransaction[]>([])
  const [categories, setCategories] = useState<AccountingCategory[]>([])
  const [form, setForm] = useState<TransactionRequest>(emptyForm)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [transactionType, setTransactionType] = useState<'ALL' | TransactionType>('ALL')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [error, setError] = useState('')

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const [page, categoryData] = await Promise.all([
        financialTransactionService.list({ page: 0, size: 50, sort: 'transactionDate,desc', transactionType: transactionType === 'ALL' ? undefined : transactionType, dateFrom: dateFrom || undefined, dateTo: dateTo || undefined, categoryId: categoryId || undefined }),
        financialTransactionService.getCategories(),
      ])
      setTransactions(page.content)
      setCategories(categoryData)
    } catch (requestError: unknown) {
      setError(getErrorMessage(requestError, 'Không thể tải dữ liệu thu chi.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [transactionType, dateFrom, dateTo, categoryId])

  const summary = useMemo(() => transactions.reduce((total, item) => ({
    income: total.income + (item.transactionType === 'INCOME' ? Number(item.amount) : 0),
    expense: total.expense + (item.transactionType === 'EXPENSE' ? Number(item.amount) : 0),
  }), { income: 0, expense: 0 }), [transactions])

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    if (form.documentId && form.invoiceId) { setError('Chỉ liên kết một chứng từ hoặc một hóa đơn.'); return }
    setSubmitting(true)
    try {
      if (editingId) await financialTransactionService.update(editingId, form)
      else await financialTransactionService.create(form)
      setForm(emptyForm)
      setEditingId(null)
      await load()
    } catch (requestError: unknown) {
      setError(getErrorMessage(requestError, 'Không thể lưu giao dịch.'))
    } finally { setSubmitting(false) }
  }

  const edit = (item: FinancialTransaction) => {
    setEditingId(item.id)
    setForm({ transactionType: item.transactionType, amount: Number(item.amount), transactionDate: item.transactionDate, description: item.description || undefined, categoryId: item.categoryId || undefined, documentId: item.documentId || undefined, invoiceId: item.invoiceId || undefined, paymentMethod: item.paymentMethod || undefined })
  }

  const deleteItem = async (id: number) => {
    if (!window.confirm('Xóa giao dịch này?')) return
    try { await financialTransactionService.remove(id); await load() }
    catch (requestError: unknown) { setError(getErrorMessage(requestError, 'Không thể xóa giao dịch.')) }
  }

  const money = (value: number) => `${value.toLocaleString('vi-VN')} đ`
  const setNumber = (key: 'categoryId' | 'documentId' | 'invoiceId', value: string) => setForm({ ...form, [key]: value ? Number(value) : undefined })

  const fieldClass = 'rounded-lg border border-slate-300 bg-white px-3 py-2 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100'

  const exportReport = async () => {
    setExporting(true)
    try { await exportService.exportFinancialReport({ fromDate: dateFrom || undefined, toDate: dateTo || undefined }) }
    catch (requestError: unknown) { setError(getErrorMessage(requestError, 'Không thể xuất dữ liệu.')) }
    finally { setExporting(false) }
  }

  return <div className="space-y-6">
    <div className="flex justify-end"><button onClick={() => void exportReport()} disabled={exporting} className="inline-flex items-center gap-2 rounded-md bg-emerald-600 px-4 py-2 text-sm font-medium text-white disabled:bg-emerald-300"><Download size={16} />{exporting ? 'Dang xuat...' : 'Xuat bao cao Excel'}</button></div>
    <PageHeader title="Quản lý thu chi" description="Ghi nhận và theo dõi các giao dịch thuộc phạm vi công ty của bạn." />
    <div className="grid gap-4 md:grid-cols-3">
      <ContentCard className="border-emerald-200 bg-emerald-50 p-5"><p className="text-sm font-medium text-emerald-800">Tổng thu</p><p className="mt-1 text-2xl font-bold text-emerald-900">{money(summary.income)}</p><p className="mt-1 text-xs text-emerald-700">Từ danh sách giao dịch đang lọc</p></ContentCard>
      <ContentCard className="border-rose-200 bg-rose-50 p-5"><p className="text-sm font-medium text-rose-800">Tổng chi</p><p className="mt-1 text-2xl font-bold text-rose-900">{money(summary.expense)}</p><p className="mt-1 text-xs text-rose-700">Từ danh sách giao dịch đang lọc</p></ContentCard>
      <ContentCard className="border-blue-200 bg-blue-50 p-5"><p className="text-sm font-medium text-blue-800">Số dư</p><p className="mt-1 text-2xl font-bold text-blue-900">{money(summary.income - summary.expense)}</p><p className="mt-1 text-xs text-blue-700">Tổng thu trừ tổng chi hiện tại</p></ContentCard>
    </div>
    <ContentCard className="p-5"><form onSubmit={submit} className="grid gap-4 md:grid-cols-2">
      <div className="md:col-span-2"><h2 className="text-lg font-semibold text-slate-800">{editingId ? 'Cập nhật giao dịch' : 'Tạo giao dịch'}</h2><p className="mt-1 text-sm text-slate-500">Chỉ liên kết một chứng từ hoặc một hóa đơn cho mỗi giao dịch.</p></div>
      <label className="grid gap-1 text-sm font-medium text-slate-700">Loại<select value={form.transactionType} onChange={(event) => setForm({ ...form, transactionType: event.target.value as TransactionType })} className={fieldClass}><option value="INCOME">Thu</option><option value="EXPENSE">Chi</option></select></label>
      <label className="grid gap-1 text-sm font-medium text-slate-700">Số tiền<input type="number" min="0.01" step="0.01" required value={form.amount || ''} onChange={(event) => setForm({ ...form, amount: Number(event.target.value) })} className={fieldClass} /></label>
      <label className="grid gap-1 text-sm font-medium text-slate-700">Ngày giao dịch<input type="date" required value={form.transactionDate} onChange={(event) => setForm({ ...form, transactionDate: event.target.value })} className={fieldClass} /></label>
      <label className="grid gap-1 text-sm font-medium text-slate-700">Nhóm chi phí<select value={form.categoryId || ''} onChange={(event) => setNumber('categoryId', event.target.value)} className={fieldClass}><option value="">Chưa phân nhóm</option>{categories.map((item) => <option key={item.id} value={item.id}>{item.categoryName}</option>)}</select></label>
      <label className="grid gap-1 text-sm font-medium text-slate-700">Mã chứng từ liên kết<input type="number" min="1" value={form.documentId || ''} onChange={(event) => setNumber('documentId', event.target.value)} className={fieldClass} /></label>
      <label className="grid gap-1 text-sm font-medium text-slate-700">Mã hóa đơn liên kết<input type="number" min="1" value={form.invoiceId || ''} onChange={(event) => setNumber('invoiceId', event.target.value)} className={fieldClass} /></label>
      <label className="grid gap-1 text-sm font-medium text-slate-700">Phương thức<input value={form.paymentMethod || ''} onChange={(event) => setForm({ ...form, paymentMethod: event.target.value })} className={fieldClass} /></label>
      <label className="grid gap-1 text-sm font-medium text-slate-700 md:col-span-2">Mô tả<textarea value={form.description || ''} onChange={(event) => setForm({ ...form, description: event.target.value })} className={`${fieldClass} min-h-20`} /></label>
      <div className="flex justify-end gap-2 md:col-span-2"><button type="button" onClick={() => { setEditingId(null); setForm(emptyForm) }} className="rounded-md border border-slate-300 px-4 py-2 text-sm">Hủy</button><button disabled={submitting} className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:bg-blue-300"><Plus size={16} />{submitting ? 'Đang lưu...' : editingId ? 'Lưu thay đổi' : 'Thêm giao dịch'}</button></div>
    </form></ContentCard>
    <ContentCard><FilterBar><select value={transactionType} onChange={(event) => setTransactionType(event.target.value as 'ALL' | TransactionType)} className={fieldClass}><option value="ALL">Tất cả thu chi</option><option value="INCOME">Thu</option><option value="EXPENSE">Chi</option></select><input type="date" value={dateFrom} onChange={(event) => setDateFrom(event.target.value)} className={fieldClass} /><input type="date" value={dateTo} onChange={(event) => setDateTo(event.target.value)} className={fieldClass} /><select value={categoryId} onChange={(event) => setCategoryId(event.target.value)} className={fieldClass}><option value="">Tất cả nhóm</option>{categories.map((item) => <option key={item.id} value={item.id}>{item.categoryName}</option>)}</select></FilterBar>
      {error && <div className="m-4"><ErrorState message={error} onRetry={() => void load()} /></div>}
      {loading ? <LoadingState label="Đang tải giao dịch..." /> : transactions.length === 0 ? <EmptyState title="Chưa có giao dịch" description="Thay đổi bộ lọc hoặc tạo giao dịch mới để bắt đầu theo dõi thu chi." /> : <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="bg-slate-50 text-slate-600"><tr><th className="p-3">Ngày</th><th className="p-3">Loại</th><th className="p-3">Nhóm</th><th className="p-3">Mô tả/liên kết</th><th className="p-3 text-right">Số tiền</th><th className="p-3"></th></tr></thead><tbody>{transactions.map((item) => <tr key={item.id} className="border-t border-slate-100 hover:bg-slate-50"><td className="p-3">{new Date(item.transactionDate).toLocaleDateString('vi-VN')}</td><td className="p-3 font-medium"><span className={item.transactionType === 'INCOME' ? 'rounded-full bg-emerald-50 px-2 py-1 text-xs text-emerald-700' : 'rounded-full bg-rose-50 px-2 py-1 text-xs text-rose-700'}>{item.transactionType === 'INCOME' ? 'Thu' : 'Chi'}</span></td><td className="p-3">{item.categoryName || '-'}</td><td className="p-3">{item.description || '-'} {item.documentId ? `(CT #${item.documentId})` : item.invoiceId ? `(HĐ #${item.invoiceId})` : ''}</td><td className="p-3 text-right font-medium">{money(Number(item.amount))}</td><td className="p-3"><div className="flex justify-end gap-1"><button title="Sửa" onClick={() => edit(item)} className="rounded p-1.5 text-blue-600 hover:bg-blue-50"><Pencil size={16} /></button><button title="Xóa" onClick={() => void deleteItem(item.id)} className="rounded p-1.5 text-red-600 hover:bg-red-50"><Trash2 size={16} /></button></div></td></tr>)}</tbody></table></div>}</ContentCard>
  </div>
}

export default FinancialTransactions

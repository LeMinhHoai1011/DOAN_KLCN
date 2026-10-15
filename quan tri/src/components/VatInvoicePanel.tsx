import type { InvoiceResponse, InvoiceUpdateRequest } from '../services/invoiceService'
import type { ReactNode } from 'react'

export interface InvoiceFormData {
  invoiceNumber: string; invoiceSeries: string; invoiceDate: string; signDate: string
  sellerName: string; sellerTaxCode: string; sellerAddress: string; sellerPhone: string
  buyerName: string; buyerTaxCode: string; buyerAddress: string
  subtotal: string; vatAmount: string; totalAmount: string
  paymentMethod: string; amountInWords: string; taxAuthorityCode: string
}

export const emptyInvoiceForm: InvoiceFormData = {
  invoiceNumber: '', invoiceSeries: '', invoiceDate: '', signDate: '', sellerName: '', sellerTaxCode: '',
  sellerAddress: '', sellerPhone: '', buyerName: '', buyerTaxCode: '', buyerAddress: '', subtotal: '',
  vatAmount: '', totalAmount: '', paymentMethod: '', amountInWords: '', taxAuthorityCode: '',
}

export const toInvoiceForm = (invoice: InvoiceResponse): InvoiceFormData => ({
  invoiceNumber: invoice.invoiceNumber || '', invoiceSeries: invoice.invoiceSeries || '', invoiceDate: invoice.invoiceDate || '',
  signDate: invoice.signDate || '', sellerName: invoice.sellerName || '', sellerTaxCode: invoice.sellerTaxCode || '',
  sellerAddress: invoice.sellerAddress || '', sellerPhone: invoice.sellerPhone || '', buyerName: invoice.buyerName || '',
  buyerTaxCode: invoice.buyerTaxCode || '', buyerAddress: invoice.buyerAddress || '',
  subtotal: invoice.subtotal == null ? '' : String(invoice.subtotal), vatAmount: invoice.vatAmount == null ? '' : String(invoice.vatAmount),
  totalAmount: invoice.totalAmount == null ? '' : String(invoice.totalAmount), paymentMethod: invoice.paymentMethod || '',
  amountInWords: invoice.amountInWords || '', taxAuthorityCode: invoice.taxAuthorityCode || '',
})

const nullable = (value: string) => value.trim() || null
const amount = (value: string) => value.trim() && Number.isFinite(Number(value)) ? Number(value) : null

export const toInvoiceUpdateRequest = (documentId: number, form: InvoiceFormData, invoice: InvoiceResponse): InvoiceUpdateRequest => ({
  documentId, invoiceNumber: nullable(form.invoiceNumber), invoiceSeries: nullable(form.invoiceSeries),
  invoiceDate: nullable(form.invoiceDate), signDate: nullable(form.signDate), sellerName: nullable(form.sellerName),
  sellerTaxCode: nullable(form.sellerTaxCode), sellerAddress: nullable(form.sellerAddress), sellerPhone: nullable(form.sellerPhone),
  buyerName: nullable(form.buyerName), buyerTaxCode: nullable(form.buyerTaxCode), buyerAddress: nullable(form.buyerAddress),
  subtotal: amount(form.subtotal), vatAmount: amount(form.vatAmount), totalAmount: amount(form.totalAmount),
  paymentMethod: nullable(form.paymentMethod), amountInWords: nullable(form.amountInWords),
  taxAuthorityCode: nullable(form.taxAuthorityCode), items: invoice.items,
})

export const documentTypeLabel = (value: string | null) => value === 'VAT_INVOICE' ? 'Hóa đơn giá trị gia tăng' : value || 'Chưa xác định'

const money = (value: number | null) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(value)} đ`
const date = (value: string) => value && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value.split('-').reverse().join('/') : '—'
const rate = (value: number | null) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(Math.abs(value) <= 1 ? value * 100 : value)}%`

const Section = ({ title, children }: { title: string; children: ReactNode }) => <section className="rounded-card border-hairline border p-4">
  <h4 className="si-label mb-4 text-brand-700">{title}</h4>{children}
</section>

export default function VatInvoicePanel({ invoice, form, editable, onChange, rawText }: {
  invoice: InvoiceResponse; form: InvoiceFormData; editable: boolean
  onChange: (field: keyof InvoiceFormData, value: string) => void; rawText?: string | null
}) {
  const Input = ({ field, label, type = 'text', multiline = false, emphasis = false }: { field: keyof InvoiceFormData; label: string; type?: string; multiline?: boolean; emphasis?: boolean }) => <label className="block min-w-0">
    <span className="si-field-label mb-1">{label}</span>
    {multiline ? <textarea value={form[field]} onChange={event => onChange(field, event.target.value)} disabled={!editable} rows={3} className="si-input disabled:bg-subtle" />
      : <input type={type} value={form[field]} onChange={event => onChange(field, event.target.value)} disabled={!editable} className={`si-input disabled:bg-subtle ${emphasis ? 'border-brand-500 font-semibold text-brand-700' : ''}`} />}
    {type === 'date' && form[field] && <span className="mt-1 block text-xs text-ink-muted">{date(form[field])}</span>}
    {type === 'number' && form[field] && <span className="mt-1 block text-xs text-ink-muted">{money(Number(form[field]))}</span>}
  </label>

  return <div className="space-y-4">
    <Section title="Thông tin hóa đơn"><div className="grid gap-4 sm:grid-cols-2"><Input label="Ký hiệu hóa đơn" field="invoiceSeries" /><Input label="Số hóa đơn" field="invoiceNumber" /><Input label="Ngày lập hóa đơn" field="invoiceDate" type="date" /><Input label="Ngày ký" field="signDate" type="date" /></div></Section>
    <Section title="Thông tin người bán"><div className="grid gap-4 sm:grid-cols-2"><Input label="Tên người bán" field="sellerName" /><Input label="Mã số thuế" field="sellerTaxCode" /><Input label="Địa chỉ" field="sellerAddress" /><Input label="Số điện thoại" field="sellerPhone" /></div></Section>
    <Section title="Thông tin người mua"><div className="grid gap-4 sm:grid-cols-2"><Input label="Tên người mua" field="buyerName" /><Input label="Mã số thuế" field="buyerTaxCode" /><div className="sm:col-span-2"><Input label="Địa chỉ" field="buyerAddress" /></div></div></Section>
    <Section title="Thông tin thanh toán"><div className="grid gap-4 sm:grid-cols-2"><Input label="Tổng tiền trước thuế" field="subtotal" type="number" /><Input label="Tiền thuế GTGT" field="vatAmount" type="number" /><Input label="Tổng tiền thanh toán" field="totalAmount" type="number" emphasis /><Input label="Hình thức thanh toán" field="paymentMethod" /><div className="sm:col-span-2"><Input label="Số tiền bằng chữ" field="amountInWords" multiline /></div></div></Section>
    {form.taxAuthorityCode && <Section title="Thông tin hóa đơn điện tử"><Input label="Mã cơ quan thuế" field="taxAuthorityCode" /></Section>}
    {invoice.items.length > 0 && <Section title="Hàng hóa / dịch vụ"><div className="overflow-x-auto"><table className="si-table min-w-[850px]"><thead><tr><th>Tên hàng hóa / dịch vụ</th><th>ĐVT</th><th className="text-right">Số lượng</th><th className="text-right">Đơn giá</th><th className="text-right">Thuế suất</th><th className="text-right">Tiền thuế</th><th className="text-right">Thành tiền</th></tr></thead><tbody>{invoice.items.map((item, index) => <tr key={item.id ?? index}><td className="text-ink-soft">{item.productName || '—'}</td><td className="text-ink-muted">{item.unit || '—'}</td><td className="si-num text-right">{item.quantity ?? '—'}</td><td className="si-num text-right">{money(item.unitPrice)}</td><td className="si-num text-right">{rate(item.taxRate)}</td><td className="si-num text-right">{money(item.taxAmount)}</td><td className="si-num text-right font-semibold">{money(item.amount)}</td></tr>)}</tbody></table></div></Section>}
    {rawText != null && <details className="rounded-card border-hairline border p-4"><summary className="cursor-pointer font-medium text-ink-soft">Văn bản OCR gốc</summary><pre className="mt-3 max-h-64 overflow-auto whitespace-pre-wrap break-words text-xs text-ink-soft">{rawText || 'Chưa có nội dung'}</pre></details>}
  </div>
}

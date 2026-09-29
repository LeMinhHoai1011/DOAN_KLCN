import api from './api'

type InvoiceFilters = { fromDate?: string; toDate?: string; search?: string }

const download = (blob: Blob, fallbackName: string, disposition?: string) => {
  const match = disposition?.match(/filename="?([^";]+)"?/i)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = match?.[1] || fallbackName
  link.click()
  URL.revokeObjectURL(url)
}

const exportInvoices = async (filters: InvoiceFilters) => {
  const response = await api.get('/api/v1/exports/invoices', { params: filters, responseType: 'blob' })
  download(response.data, 'invoices.xlsx', response.headers['content-disposition'])
}

const exportFinancialReport = async (filters: Pick<InvoiceFilters, 'fromDate' | 'toDate'>) => {
  const response = await api.get('/api/v1/exports/financial-report', { params: filters, responseType: 'blob' })
  download(response.data, 'financial_report.xlsx', response.headers['content-disposition'])
}

export default { exportInvoices, exportFinancialReport }

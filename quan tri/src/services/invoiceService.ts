import api from './api'

export interface InvoiceItem {
  id: number
  productName: string | null
  quantity: number | null
  unit: string | null
  unitPrice: number | null
  taxRate: number | null
  taxAmount: number | null
  amount: number | null
}

export interface InvoiceResponse {
  id: number
  documentId: number
  invoiceNumber: string | null
  invoiceSeries: string | null
  invoiceDate: string | null
  sellerName: string | null
  sellerTaxCode: string | null
  sellerAddress: string | null
  sellerPhone: string | null
  buyerName: string | null
  buyerTaxCode: string | null
  buyerAddress: string | null
  subtotal: number | null
  vatAmount: number | null
  totalAmount: number | null
  paymentMethod: string | null
  amountInWords: string | null
  taxAuthorityCode: string | null
  signDate: string | null
  items: InvoiceItem[]
}

export interface InvoiceUpdateRequest {
  documentId: number
  invoiceNumber?: string | null
  invoiceSeries?: string | null
  invoiceDate?: string | null
  sellerName?: string | null
  sellerTaxCode?: string | null
  sellerAddress?: string | null
  sellerPhone?: string | null
  buyerName?: string | null
  buyerTaxCode?: string | null
  buyerAddress?: string | null
  subtotal?: number | null
  vatAmount?: number | null
  totalAmount?: number | null
  paymentMethod?: string | null
  amountInWords?: string | null
  taxAuthorityCode?: string | null
  signDate?: string | null
  items?: InvoiceItem[]
}

const getInvoiceByDocumentId = async (documentId: number) => {
  const { data } = await api.get<InvoiceResponse>(`/api/v1/documents/${documentId}/invoice`)
  return data
}

const updateInvoice = async (id: number, request: InvoiceUpdateRequest) => {
  const { data } = await api.put<InvoiceResponse>(`/api/v1/invoices/${id}`, request)
  return data
}

export interface RetailInvoiceDetails {
  transactionTime: string | null
  lineAmountBasis: 'BEFORE_DISCOUNT' | 'AFTER_DISCOUNT' | 'UNKNOWN'
  lineDiscounts: { rowNumber: number; amount: number }[] | null
  billDiscount: RetailAdjustment
  tax: RetailAdjustment
  fees: RetailAdjustment
  tenderedAmount: number | null
  changeAmount: number | null
}

export interface RetailAdjustment {
  amount: number | null
  treatment: 'INCLUDED' | 'SEPARATE' | 'NOT_PRESENT' | 'UNKNOWN'
}

export interface RetailInvoiceDetailsView {
  details: RetailInvoiceDetails | null
  stale: boolean
}

const invoiceService = {
  getInvoiceByDocumentId,
  updateInvoice,
  getRetailDetails: async (id: number) =>
    (await api.get<RetailInvoiceDetailsView>(`/api/v1/invoices/${id}/retail-details`)).data,
  updateRetailDetails: async (id: number, details: RetailInvoiceDetails) =>
    (await api.put<RetailInvoiceDetailsView>(`/api/v1/invoices/${id}/retail-details`, details)).data,
  updateItems: async (id: number, items: Omit<InvoiceItem, 'id'>[]) =>
    (await api.put<InvoiceResponse>(`/api/v1/invoices/${id}/items`, { items })).data,
}

export default invoiceService

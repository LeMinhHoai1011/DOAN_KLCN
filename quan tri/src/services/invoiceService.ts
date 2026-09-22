import api from './api'

export interface InvoiceItem {
  id: number
  productName: string | null
  quantity: number | null
  unitPrice: number | null
  amount: number | null
}

export interface InvoiceResponse {
  id: number
  documentId: number
  invoiceNumber: string | null
  invoiceDate: string | null
  sellerName: string | null
  sellerTaxCode: string | null
  sellerAddress: string | null
  buyerName: string | null
  buyerTaxCode: string | null
  buyerAddress: string | null
  subtotal: number | null
  vatAmount: number | null
  totalAmount: number | null
  items: InvoiceItem[]
}

export interface InvoiceUpdateRequest {
  documentId: number
  invoiceNumber?: string | null
  invoiceDate?: string | null
  sellerName?: string | null
  sellerTaxCode?: string | null
  sellerAddress?: string | null
  buyerName?: string | null
  buyerTaxCode?: string | null
  buyerAddress?: string | null
  subtotal?: number | null
  vatAmount?: number | null
  totalAmount?: number | null
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

const invoiceService = {
  getInvoiceByDocumentId,
  updateInvoice,
}

export default invoiceService
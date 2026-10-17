import { assertDateRange } from '../utils/dateTime'
import accountingCategoryService from './accountingCategoryService'
export type { AccountingCategory } from './accountingCategoryService'
import api from './api'

export type TransactionType = 'INCOME' | 'EXPENSE'


export interface FinancialTransaction {
  id: number
  transactionType: TransactionType
  amount: number
  transactionDate: string
  description: string | null
  categoryId: number | null
  categoryName: string | null
  companyId: number
  documentId: number | null
  invoiceId: number | null
  paymentMethod: string | null
  createdAt: string
  updatedAt: string
  paymentConfirmation: 'CONFIRMED' | 'CANCELLED' | 'UNVERIFIED'
  status: string
}

export interface TransactionRequest {
  transactionType: TransactionType
  amount: number | string
  transactionDate: string
  description?: string
  categoryId?: number
  documentId?: number
  invoiceId?: number
  paymentMethod?: string
  companyId?: number
  requestKey?: string
  paymentConfirmed?: boolean
  expectedUpdatedAt?: string
}

interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
}

const list = async (params: Record<string, string | number | undefined>) => {
  assertDateRange(String(params.dateFrom||''),String(params.dateTo||''))
  const { data } = await api.get<PageResponse<FinancialTransaction>>('/api/v1/financial-transactions', { params })
  return data
}

const getCategories = accountingCategoryService.getCategories

const create = async (request: TransactionRequest) => {
  const { data } = await api.post<FinancialTransaction>('/api/v1/financial-transactions', request)
  return data
}

const update = async (id: number, request: TransactionRequest) => {
  const { data } = await api.put<FinancialTransaction>(`/api/v1/financial-transactions/${id}`, request)
  return data
}

const remove = async (id: number) => {
  await api.delete(`/api/v1/financial-transactions/${id}`)
}

export interface PaymentSummary {documentId:number|null;invoiceId:number|null;totalAmount:number|null;confirmedPaid:number;remaining:number|null;paymentStatus:string|null;verifiedPaymentStatus:string|null;unverifiedCount:number;currency:string;comparison:string}
export interface PaymentLinkOption {documentId:number;invoiceId:number|null;documentName:string;invoiceNumber:string|null;totalAmount:number|null;suggestedTransactionType:string|null;suggestedCategoryId:number|null;currency:string}
export interface PaymentPreview {link:PaymentLinkOption;summary:PaymentSummary;availableAmount:number|null;canCreate:boolean;blockedCode:string|null;userMessage:string|null;directionSource:string;suggestedDescription:string;suggestedCategoryType:TransactionType|null}
export interface PaymentHistoryEvent {id:number;action:string;actorId:number|null;at:string;oldValue:string|null;newValue:string}
export default {list,getCategories,create,update,remove,
 preview:async(params:{documentId?:number;invoiceId?:number;companyId?:number;editingId?:number})=>(await api.get<PaymentPreview>('/api/v1/financial-transactions/payment-preview',{params})).data,
 methods:async()=> (await api.get<string[]>('/api/v1/financial-transactions/payment-methods')).data,
 options:async(kind:'documents'|'invoices',params:{search?:string;companyId?:number;documentId?:number;page?:number})=>(await api.get<PageResponse<PaymentLinkOption>>(`/api/v1/financial-transactions/link-options/${kind}`,{params})).data,
 summary:async(params:{documentId?:number;invoiceId?:number;companyId?:number})=>(await api.get<PaymentSummary>('/api/v1/financial-transactions/payment-summary',{params})).data,
 cancel:async(id:number,reason:string,expectedUpdatedAt:string)=>(await api.post<FinancialTransaction>(`/api/v1/financial-transactions/${id}/cancel`,{reason,expectedUpdatedAt})).data,
 history:async(id:number)=>(await api.get<PaymentHistoryEvent[]>(`/api/v1/financial-transactions/${id}/history`)).data,
 reconcile:async(params:{documentId?:number;invoiceId?:number;companyId?:number})=>(await api.post<PaymentSummary>('/api/v1/financial-transactions/payment-reconciliation',null,{params})).data,
 reconciliationHistory:async(params:{documentId?:number;invoiceId?:number;companyId?:number})=>(await api.get<PaymentHistoryEvent[]>('/api/v1/financial-transactions/payment-reconciliation/history',{params})).data,
 unverified:async(params:Record<string,string|number|undefined>)=>(await api.get<PageResponse<FinancialTransaction>>('/api/v1/financial-transactions/unverified',{params})).data,
}

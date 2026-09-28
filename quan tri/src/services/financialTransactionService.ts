import api from './api'

export type TransactionType = 'INCOME' | 'EXPENSE'

export interface AccountingCategory {
  id: number
  categoryCode: string
  categoryName: string
  description: string | null
}

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
  status: string
}

export interface TransactionRequest {
  transactionType: TransactionType
  amount: number
  transactionDate: string
  description?: string
  categoryId?: number
  documentId?: number
  invoiceId?: number
  paymentMethod?: string
}

interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
}

const list = async (params: Record<string, string | number | undefined>) => {
  const { data } = await api.get<PageResponse<FinancialTransaction>>('/api/v1/financial-transactions', { params })
  return data
}

const getCategories = async () => {
  const { data } = await api.get<AccountingCategory[]>('/api/v1/accounting-categories')
  return data
}

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

export default { list, getCategories, create, update, remove }

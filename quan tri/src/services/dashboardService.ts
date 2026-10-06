import api from './api'

export interface DashboardStatistics {
  totalDocuments: number
  totalInvoices: number
  totalClassified: number
  totalReviewRequired: number
  totalFailed: number
  documentsByType: { code: string; name: string; count: number }[]
  documentsByStatus: { status: string; count: number }[]
}
export interface FinancialDashboard { totalRevenue: number; totalExpense: number; cashFlow: number; expensesByCategory: { category: string | null; amount: number }[] }
export interface FinancialSeriesPoint { period: string; income: number; expense: number; cashFlow: number }

const getDashboardStatistics = async () => {
  const { data } = await api.get<Partial<DashboardStatistics>>('/api/v1/dashboard/statistics')
  return {
    totalDocuments: Number(data.totalDocuments || 0),
    totalInvoices: Number(data.totalInvoices || 0),
    totalClassified: Number(data.totalClassified || 0),
    totalReviewRequired: Number(data.totalReviewRequired || 0),
    totalFailed: Number(data.totalFailed || 0),
    documentsByType: Array.isArray(data.documentsByType) ? data.documentsByType : [],
    documentsByStatus: Array.isArray(data.documentsByStatus) ? data.documentsByStatus : [],
  } satisfies DashboardStatistics
}
const getFinancialDashboard = async (params: { dateFrom?: string; dateTo?: string } = {}) => {
  const { data } = await api.get<FinancialDashboard>('/api/v1/dashboard/financial', { params })
  return data
}
const getFinancialTimeSeries = async (params: { dateFrom?: string; dateTo?: string; interval?: 'DAILY' | 'MONTHLY' } = {}) => (await api.get<FinancialSeriesPoint[]>('/api/v1/dashboard/financial/time-series', { params })).data

const dashboardService = {
  getDashboardStatistics,
  getFinancialDashboard,
  getFinancialTimeSeries,
}

export default dashboardService

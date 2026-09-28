import api from './api'

export interface DashboardStatistics {
  totalDocuments: number
  totalInvoices: number
  totalClassified: number
  totalReviewRequired: number
}
export interface FinancialDashboard { totalRevenue: number; totalExpense: number; cashFlow: number; expensesByCategory: { category: string | null; amount: number }[] }

const getDashboardStatistics = async () => {
  const { data } = await api.get<DashboardStatistics>('/api/v1/dashboard/statistics')
  return data
}
const getFinancialDashboard = async (params: { dateFrom?: string; dateTo?: string } = {}) => {
  const { data } = await api.get<FinancialDashboard>('/api/v1/dashboard/financial', { params })
  return data
}

const dashboardService = {
  getDashboardStatistics,
  getFinancialDashboard,
}

export default dashboardService

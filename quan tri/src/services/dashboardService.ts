import api from './api'

export interface DashboardStatistics {
  totalDocuments: number
  totalInvoices: number
  totalClassified: number
  totalReviewRequired: number
}

const getDashboardStatistics = async () => {
  const { data } = await api.get<DashboardStatistics>('/api/v1/dashboard/statistics')
  return data
}

const dashboardService = {
  getDashboardStatistics,
}

export default dashboardService
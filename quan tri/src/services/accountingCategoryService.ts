import api from './api'

export interface AccountingCategory {
  id: number
  categoryCode: string
  categoryName: string
  description: string | null
}

const getCategories = async (companyId?: number | null) => {
  const { data } = await api.get<AccountingCategory[]>('/api/v1/accounting-categories', {
    params: companyId ? { companyId } : undefined,
  })
  return data
}

export default { getCategories }

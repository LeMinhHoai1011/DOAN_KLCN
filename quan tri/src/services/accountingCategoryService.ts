import api from './api'
export type AccountingCategoryType = 'INCOME' | 'EXPENSE'
export interface AccountingCategory {
 id: number; categoryCode: string; categoryName: string; description: string | null;
 companyId: number | null; categoryType: AccountingCategoryType | null; active: boolean;
}
export interface AccountingCategoryRequest {
 categoryCode: string; categoryName: string; description: string | null;
 companyId: number; categoryType: AccountingCategoryType | null; active: boolean;
}
export interface CategoryCompany { id: number; companyName: string }
const getCategories = async (companyId?: number | null, includeInactive = false, type?: AccountingCategoryType) => {
 const {data}=await api.get<AccountingCategory[]>('/api/v1/accounting-categories',{params:{...(companyId != null ? {companyId} : {}),includeInactive,...(type ? {type} : {})}})
 if(!Array.isArray(data)) throw new Error('Invalid category response')
 // Older active-only responses omit these additive fields; never infer type from names/codes.
 return data.map(item=>({...item,companyId:item.companyId??null,categoryType:item.categoryType??null,active:item.active??true}))
}
export default {
 getCategories,
 getCompanies: async () => { const {data}=await api.get<CategoryCompany[]>('/api/v1/companies'); if(!Array.isArray(data)) throw new Error('Invalid company response'); return data },
 create: async (request: AccountingCategoryRequest) => (await api.post<AccountingCategory>('/api/v1/accounting-categories',request)).data,
 update: async (id: number,request: AccountingCategoryRequest) => (await api.put<AccountingCategory>(`/api/v1/accounting-categories/${id}`,request)).data,
}

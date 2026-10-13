import api from './api'

export type ReconciliationStatus = 'MATCHED' | 'MISMATCH' | 'INSUFFICIENT_DATA' | 'NEED_REVIEW'
export interface Reconciliation { id: number; type: string; expectedAmount: number | null; actualAmount: number | null; differenceAmount: number | null; status: ReconciliationStatus; comment: string | null }
interface ReconciliationApi extends Omit<Reconciliation, 'type'> { checkType: string }

const fromApi = ({ checkType, ...value }: ReconciliationApi): Reconciliation => ({ ...value, type: checkType })
const getByDocument = async (documentId: number) => (await api.get<ReconciliationApi[]>(`/api/v1/documents/${documentId}/reconciliations`)).data.map(fromApi)
const run = async (documentId: number) => (await api.post<ReconciliationApi[]>(`/api/v1/documents/${documentId}/reconciliations`)).data.map(fromApi)
const decide = async (documentId: number, id: number, status: string, comment?: string) => fromApi((await api.put<ReconciliationApi>(`/api/v1/documents/${documentId}/reconciliations/${id}/decision`, null, { params: { status, comment } })).data)

export default { getByDocument, run, decide }

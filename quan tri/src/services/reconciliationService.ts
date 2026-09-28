import api from './api'

export interface Reconciliation { id: number; type: string; expectedAmount: number; actualAmount: number; differenceAmount: number; status: string; comment: string | null }

const getByDocument = async (documentId: number) => (await api.get<Reconciliation[]>(`/api/v1/documents/${documentId}/reconciliations`)).data
const run = async (documentId: number) => (await api.post<Reconciliation[]>(`/api/v1/documents/${documentId}/reconciliations`)).data
const decide = async (documentId: number, id: number, status: string, comment?: string) => (await api.put<Reconciliation>(`/api/v1/documents/${documentId}/reconciliations/${id}/decision`, null, { params: { status, comment } })).data

export default { getByDocument, run, decide }

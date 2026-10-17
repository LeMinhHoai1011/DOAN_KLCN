import api from './api'
export interface FileVersion { id: number; number: number; name: string; mimeType: string; size: number; actorId: number | null; at: string | null; current: boolean; original: boolean }
export interface DocumentStorage { documentId: number; currentVersionId: number | null; original: FileVersion | null; status: string; actualSize: number | null; actualMimeType: string | null; checksum: string | null; checksumAlgorithm: string | null; etag: string | null; objectVersionId: string | null; metadataMatches: boolean; checkedAt: string; canDownload: boolean }
export interface ArchiveEvent { id: number; source: string; action: string; actorId: number | null; at: string | null; status: string | null; note: string | null; hasBefore: boolean; hasAfter: boolean }
export interface DocumentHistory { events: ArchiveEvent[]; legacyCoverageIncomplete: boolean }
export interface DocumentSnapshot { id: number; action: string; actorId: number | null; at: string; before: unknown; after: unknown }
export default {
  storage: async (id: number) => (await api.get<DocumentStorage>(`/api/v1/documents/${id}/storage`)).data,
  versions: async (id: number) => (await api.get<FileVersion[]>(`/api/v1/documents/${id}/versions`)).data,
  history: async (id: number) => (await api.get<DocumentHistory>(`/api/v1/documents/${id}/history`)).data,
  snapshot: async (id: number, event: ArchiveEvent) => (await api.get<DocumentSnapshot>(`/api/v1/documents/${id}/history/${event.id}`, { params: { source: event.source } })).data,
  file: async (id: number, versionId: number, preview: boolean) => (await api.get<Blob>(`/api/v1/documents/${id}/versions/${versionId}/${preview ? 'preview' : 'download'}`, { responseType: 'blob' })).data,
}

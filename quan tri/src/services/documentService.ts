import api from './api'

export type DocumentStatus =
  | 'UPLOADED'
  | 'PROCESSING'
  | 'PROCESSED'
  | 'NEED_REVIEW'
  | 'COMPLETED'
  | 'FAILED'

export interface DocumentResponse {
  id: number
  originalFileName: string
  fileType: string
  fileSize: number
  filePath: string
  status: DocumentStatus
  uploadedById: number | null
  createdAt: string
  updatedAt: string
}

export interface OCRResultResponse {
  id: number
  documentId: number
  rawText: string
  confidence: number | null
  processedAt: string
}

export interface DocumentListItem {
  id: number
  fileName: string
  fileType: string
  status: string
  date: string
  supplier?: string
  amount?: number
  aiConfidence?: number
}

const statusLabels: Record<DocumentStatus, string> = {
  UPLOADED: 'Đã tải lên',
  PROCESSING: 'Đang xử lý',
  PROCESSED: 'Đã xử lý',
  NEED_REVIEW: 'Cần kiểm tra',
  COMPLETED: 'Hoàn tất',
  FAILED: 'Lỗi',
}

const formatDate = (value: string) => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleDateString('vi-VN')
}

export const mapDocument = (document: DocumentResponse): DocumentListItem => ({
  id: document.id,
  fileName: document.originalFileName,
  fileType: document.fileType,
  status: statusLabels[document.status] || document.status,
  date: formatDate(document.createdAt),
})

const getDocuments = async () => {
  const { data } = await api.get<DocumentResponse[]>('/api/v1/documents')
  return data.map(mapDocument)
}

const getDocumentById = async (id: number) => {
  const { data } = await api.get<DocumentResponse>(`/api/v1/documents/${id}`)
  return data
}

const getDocumentOCR = async (id: number) => {
  const { data } = await api.get<OCRResultResponse>(`/api/v1/documents/${id}/ocr`)
  return data
}

const uploadDocument = async (file: File) => {
  const formData = new FormData()
  formData.append('file', file)

  const { data } = await api.post<DocumentResponse>('/api/v1/documents/upload', formData)
  return data
}

const documentService = {
  getDocuments,
  getDocumentById,
  getDocumentOCR,
  uploadDocument,
}

export default documentService
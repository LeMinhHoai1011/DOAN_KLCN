import api from './api'
import axios from 'axios'
import { PreviewLoadError, previewHttpError, validatePreviewBlob } from './previewBlob'

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
  processingStatus: DocumentStatus
  reviewStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CORRECTED'
  companyId: number | null
  typeId: number | null
  documentType: string | null
  documentTypeCode: string | null
  documentTypeName: string | null
  amount: number | null
  aiConfidence: number | null
  companyRole: { role: string | null; confidence: number | null; reason: string | null } | null
  documentDirection: string | null
  transactionAssessment: { type: string | null; confidence: number | null; reason: string | null } | null
  uploadedById: number | null
  createdAt: string
  updatedAt: string
}

interface DocumentPageResponse {
  content: DocumentResponse[]
  totalElements: number
  totalPages: number
  number: number
}

export interface DocumentPage {
  content: DocumentListItem[]
  totalElements: number
  totalPages: number
  /** UI page number, normalized to one-based numbering. */
  number: number
}

export interface DocumentPageQuery {
  /** UI page number, starting at 1. */
  page?: number
  size?: number
  search?: string
  dateFrom?: string
  dateTo?: string
  processingStatus?: DocumentStatus
  reviewStatus?: DocumentResponse['reviewStatus']
  typeId?: number
  sort?: string
}

export type WorkflowAction = 'SUBMIT' | 'START_REVIEW' | 'APPROVE' | 'REJECT' | 'REQUEST_INFO' | 'RESUBMIT'
export interface WorkflowActionRequest { action: WorkflowAction; note?: string }

export interface OCRResultResponse {
  id: number
  documentId: number
  rawText: string
  /** OCR layout JSON v1/v2. Coordinates may be normalized or expressed in source-page pixels. */
  layoutJson: string | null
  language: string | null
  sourceType: string | null
  ocrEngine: string | null
  confidence: number | null
  processedAt: string
}

export interface ExtractedFieldResponse {
  id: number
  fieldName: string
  fieldValue: string | null
  source: string | null
  confidence: number | null
  originalAiValue: string | null
  correctedValue: string | null
  manuallyCorrected: boolean
  correctedById: number | null
  correctedAt: string | null
  locations?: Array<{
    page: number; x: number; y: number; width: number; height: number
    pageWidth: number; pageHeight: number; matchConfidence: number
  }>
}

export interface AiDocumentProcessingResponse {
  documentId: number
  status: DocumentStatus
  requiresReview: boolean
  warnings: string[]
  preprocessing: {
    applied: boolean
    detectedAngleDegrees: number
    originalWidth: number
    originalHeight: number
    processedWidth: number
    processedHeight: number
    durationMs: number
    warning: string | null
  } | null
}

export interface DocumentUploadResponse {
  success: boolean
  documentId: number
  processingStatus: DocumentStatus
  reviewStatus: DocumentResponse['reviewStatus']
  documentTypeCode: string | null
  documentTypeName: string | null
  documentTypeConfidence: number | null
  message: string
  error: { code: string; message: string; retryable: boolean } | null
}

export interface DocumentListItem {
  id: number
  fileName: string
  fileType: string
  fileSize: number
  /** Raw processing state returned by the backend; never replace it with a label. */
  status: DocumentStatus
  reviewStatus: DocumentResponse['reviewStatus']
  /** Presentation label only; source states remain available above. */
  displayStatus: string
  date: string
  supplier?: string
  amount?: number
  aiConfidence?: number
  documentTypeCode: string | null
  documentTypeName: string
}

const statusLabels: Record<DocumentStatus, string> = {
  UPLOADED: 'Đã tải lên',
  PROCESSING: 'Đang xử lý',
  PROCESSED: 'Đã xử lý',
  NEED_REVIEW: 'Cần kiểm tra',
  COMPLETED: 'Hoàn thành',
  FAILED: 'Lỗi xử lý',
}

const formatDate = (value: string) => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleDateString('vi-VN')
}

export const mapDocument = (document: DocumentResponse): DocumentListItem => ({
  id: document.id,
  fileName: document.originalFileName,
  fileType: document.fileType,
  fileSize: document.fileSize,
  status: document.status,
  reviewStatus: document.reviewStatus,
  displayStatus: statusLabels[document.status] || document.status,
  date: formatDate(document.createdAt),
  amount: document.amount == null ? undefined : Number(document.amount),
  aiConfidence: document.aiConfidence == null ? undefined : Math.round(Number(document.aiConfidence) * 100),
  documentTypeCode: document.documentTypeCode || document.documentType,
  documentTypeName: document.documentTypeName || document.documentTypeCode || document.documentType || 'Chưa phân loại',
})

const getDocumentPage = async ({ page = 1, size = 50, sort = 'createdAt,desc', ...filters }: DocumentPageQuery = {}): Promise<DocumentPage> => {
  const { data } = await api.get<DocumentPageResponse>('/api/v1/documents', {
    params: { page: Math.max(0, page - 1), size, sort, ...filters },
  })
  return { ...data, number: data.number + 1, content: data.content.map(mapDocument) }
}

const getDocuments = async () => {
  const page = await getDocumentPage()
  return page.content
}

const getDocumentById = async (id: number) => {
  const { data } = await api.get<DocumentResponse>(`/api/v1/documents/${id}`)
  return data
}

const getDocumentOCR = async (id: number) => {
	const { data } = await api.get<OCRResultResponse>(`/api/v1/documents/${id}/ocr`)
	return data
}

const getDocumentExtractedFields = async (id: number) => {
  const { data } = await api.get<ExtractedFieldResponse[]>(`/api/v1/documents/${id}/extracted-fields`)
  return data
}

const correctExtractedField = async (documentId: number, fieldId: number, fieldValue: string) => {
  const { data } = await api.patch<ExtractedFieldResponse>(
    `/api/v1/documents/${documentId}/extracted-fields/${fieldId}/correction`,
    { fieldValue },
  )
  return data
}

export interface DocumentType {
  id: number;
  code: string;
  name: string;
  description: string;
  active: boolean;
  documentCount: number;
}

const getDocumentTypes = async () => {
  const { data } = await api.get<DocumentType[]>('/api/v1/documents/types')
  return data.map(type => ({ ...type, active: type.active ?? true, documentCount: Number(type.documentCount || 0) }))
}

const uploadDocument = async (file: File) => {
  const formData = new FormData()
  formData.append('file', file)

  const { data } = await api.post<DocumentUploadResponse>('/api/v1/documents/upload', formData)
  return data
}

const processDocument = async (id: number, reprocess = false) => {
  const action = reprocess ? 'reprocess' : 'process'
  const { data } = await api.post<AiDocumentProcessingResponse>(`/api/v1/documents/${id}/${action}`)
  return data
}

const executeWorkflow = async (id: number, request: WorkflowActionRequest) => {
  const { data } = await api.post<DocumentResponse>(`/api/v1/documents/${id}/workflow`, request)
  return data
}

const downloadDocument = async (id: number, preview = false) => {
  const endpoint = preview ? 'preview' : 'download'
  const { data } = await api.get<Blob>(`/api/v1/documents/${id}/${endpoint}`, { responseType: 'blob' })
  return URL.createObjectURL(data)
}

type DocumentTypeInput = Pick<DocumentType, 'code' | 'name' | 'description' | 'active'>
const createDocumentType = async (request: DocumentTypeInput) => (await api.post<DocumentType>('/api/v1/documents/types', request)).data
const updateDocumentType = async (id: number, request: DocumentTypeInput) => (await api.put<DocumentType>(`/api/v1/documents/types/${id}`, request)).data
const updateDocument = async (id: number, request: { typeId: number }) => (await api.put<DocumentResponse>(`/api/v1/documents/${id}`, request)).data

const loadDocumentPreview = async (id: number) => {
  try {
    const response = await api.get<Blob>(`/api/v1/documents/${id}/preview`, { responseType: 'blob' })
	const contentType = response.headers['content-type']
	const validated = await validatePreviewBlob(response.data, typeof contentType === 'string' ? contentType : null)
    return { ...validated, status: response.status }
  } catch (error) {
    if (error instanceof PreviewLoadError) throw error
    if (axios.isAxiosError(error)) {
      const status = error.response?.status
      throw previewHttpError(status)
    }
    throw new PreviewLoadError('PREVIEW_SERVER_ERROR', 'Unexpected preview loading failure')
  }
}

const documentService = {
  getDocuments,
  getDocumentPage,
  getDocumentById,
	getDocumentOCR,
	getDocumentExtractedFields,
	correctExtractedField,
  uploadDocument,
  processDocument,
  executeWorkflow,
  getDocumentTypes,
	createDocumentType,
	updateDocumentType,
	updateDocument,
	downloadDocument,
	loadDocumentPreview,
}

export default documentService

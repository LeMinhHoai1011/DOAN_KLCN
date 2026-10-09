import api from './api'
import axios from 'axios'
import { classificationReadOutcome } from './readState'

export type ClassificationStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'NEED_REVIEW'
  | 'CORRECTED'
  | 'CLASSIFIED'
  | 'REVIEW_REQUIRED'
  | 'VERIFIED'

export interface ClassificationResponse {
  id: number
  documentId: number
  category: string | null
  accountingCategoryId: number | null
  accountingCategoryCode: string | null
  accountingCategoryName: string | null
  accountingAccount: string | null
  confidence: number | null
  reason: string | null
  status: ClassificationStatus
  aiGenerated: boolean
  createdAt: string
  updatedAt: string
}

export interface ClassificationUpdateRequest {
  category?: string
  confidence?: number
  reason?: string
  status?: ClassificationStatus
  aiGenerated?: boolean
}

const getClassification = async (documentId: number) => {
  const { data } = await api.get<ClassificationResponse>(`/api/v1/documents/${documentId}/classification`)
  return data
}

/** A missing classification is a valid read state; auth and server failures still reject. */
const getClassificationOrNull = async (documentId: number) => {
  try {
    return await getClassification(documentId)
  } catch (error) {
    if (axios.isAxiosError(error) && classificationReadOutcome(error.response?.status || 0) === 'empty') return null
    throw error
  }
}

const updateClassification = async (documentId: number, request: ClassificationUpdateRequest) => {
  const { data } = await api.put<ClassificationResponse>(`/api/v1/documents/${documentId}/classification`, request)
  return data
}

const approveClassification = async (documentId: number) => {
  const { data } = await api.post<ClassificationResponse>(`/api/v1/documents/${documentId}/classification/approve`)
  return data
}

const reviewClassification = async (documentId: number) => {
  const { data } = await api.post<ClassificationResponse>(`/api/v1/documents/${documentId}/classification/review`)
  return data
}

const correctClassification = async (documentId: number, request: ClassificationUpdateRequest) => {
  const { data } = await api.put<ClassificationResponse>(`/api/v1/documents/${documentId}/classification/correction`, request)
  return data
}

const classificationService = {
  getClassification,
  getClassificationOrNull,
  updateClassification,
  approveClassification,
  reviewClassification,
  correctClassification,
}

export default classificationService

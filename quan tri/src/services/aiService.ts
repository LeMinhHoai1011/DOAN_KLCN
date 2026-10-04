import api from './api'

export interface AiProviderResponse {
  provider: string
  model: string
  content: string
  rawResponse: string
  durationMs: number
}

const testImage = async (file: File, prompt?: string) => {
  const formData = new FormData()
  formData.append('file', file)
  if (prompt?.trim()) {
    formData.append('prompt', prompt.trim())
  }

  const { data } = await api.post<AiProviderResponse>('/api/v1/ai/test', formData)
  return data
}

const aiService = { testImage }

export default aiService

export type PreviewErrorCode =
  | 'PREVIEW_AUTH_ERROR' | 'PREVIEW_NOT_FOUND' | 'PREVIEW_SERVER_ERROR'
  | 'PREVIEW_EMPTY_BLOB' | 'PREVIEW_INVALID_CONTENT_TYPE' | 'PREVIEW_IMAGE_DECODE_ERROR'

export class PreviewLoadError extends Error {
  readonly code: PreviewErrorCode
  readonly status?: number
  constructor(code: PreviewErrorCode, message: string, status?: number) {
	  super(message); this.name = 'PreviewLoadError'; this.code = code; this.status = status
  }
}

export const previewHttpError = (status?: number) => {
  if (status === 401 || status === 403) return new PreviewLoadError('PREVIEW_AUTH_ERROR', 'Preview request was not authorized', status)
  if (status === 404) return new PreviewLoadError('PREVIEW_NOT_FOUND', 'Preview document was not found', status)
  return new PreviewLoadError('PREVIEW_SERVER_ERROR', 'Preview endpoint or storage failed', status)
}

const normalizeType = (value: string | null | undefined) => (value ?? '').split(';')[0].trim().toLowerCase()

const sniffType = async (blob: Blob) => {
  const bytes = new Uint8Array(await blob.slice(0, 12).arrayBuffer())
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'image/jpeg'
  if (bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47) return 'image/png'
  if (bytes[0] === 0x25 && bytes[1] === 0x50 && bytes[2] === 0x44 && bytes[3] === 0x46) return 'application/pdf'
  if (bytes[0] === 0x47 && bytes[1] === 0x49 && bytes[2] === 0x46) return 'image/gif'
  if (bytes[0] === 0x52 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x46
    && bytes[8] === 0x57 && bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50) return 'image/webp'
  return ''
}

export const validatePreviewBlob = async (blob: Blob, responseContentType?: string | null) => {
  if (!(blob instanceof Blob)) throw new PreviewLoadError('PREVIEW_INVALID_CONTENT_TYPE', 'Preview response is not a Blob')
  if (blob.size === 0) throw new PreviewLoadError('PREVIEW_EMPTY_BLOB', 'Preview Blob is empty')

  const headerType = normalizeType(responseContentType)
  const blobType = normalizeType(blob.type)
  const declaredType = headerType || blobType
  if (declaredType.includes('json') || declaredType.startsWith('text/')) {
    throw new PreviewLoadError('PREVIEW_INVALID_CONTENT_TYPE', `Preview endpoint returned ${declaredType}`)
  }

  const detectedType = await sniffType(blob)
  const contentType = detectedType || declaredType
  if (!(contentType.startsWith('image/') || contentType === 'application/pdf')) {
    throw new PreviewLoadError('PREVIEW_INVALID_CONTENT_TYPE', `Unsupported preview content type: ${contentType || 'unknown'}`)
  }
  // A Blob URL serves Blob.type. Re-wrap when storage/DB metadata is generic or wrong,
  // so the browser and PDF.js receive the verified binary MIME type.
  const normalizedBlob = blob.type === contentType ? blob : new Blob([blob], { type: contentType })
  return { blob: normalizedBlob, contentType, size: normalizedBlob.size }
}

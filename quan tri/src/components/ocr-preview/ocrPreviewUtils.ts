import type { ExtractedFieldResponse } from '../../services/documentService'

export type OcrLayoutElement = {
  id?: string; text: string; confidence?: number
  x: number; y: number; width: number; height: number
}

export type OcrLayoutPage = {
  page?: number; pageNumber?: number
  width?: number; height?: number; imageWidth?: number; imageHeight?: number
  words?: OcrLayoutElement[]; lines?: OcrLayoutElement[]; blocks?: OcrLayoutElement[]
}

export type FieldLocation = NonNullable<ExtractedFieldResponse['locations']>[number]
export type OverlayMode = 'field' | 'line' | 'word' | 'block'

export const pageNumberOf = (page: OcrLayoutPage) => page.page ?? page.pageNumber ?? 1
export const pageWidthOf = (page: OcrLayoutPage) => page.width ?? page.imageWidth ?? 1
export const pageHeightOf = (page: OcrLayoutPage) => page.height ?? page.imageHeight ?? 1

export const parseOcrLayout = (layoutJson: string | null | undefined): OcrLayoutPage[] => {
  if (!layoutJson) return []
  try {
    const parsed = JSON.parse(layoutJson) as { pages?: OcrLayoutPage[] } | OcrLayoutPage[]
    const pages = Array.isArray(parsed) ? parsed : parsed.pages
    return Array.isArray(pages) ? pages : []
  } catch {
    return []
  }
}

export const scaleBoundingBox = (
  box: Pick<OcrLayoutElement, 'x' | 'y' | 'width' | 'height'>,
  pageWidth: number,
  pageHeight: number,
  renderedWidth: number,
  renderedHeight: number,
) => {
  const normalized = box.x <= 1 && box.y <= 1 && box.width <= 1 && box.height <= 1
  const x = normalized ? box.x * pageWidth : box.x
  const y = normalized ? box.y * pageHeight : box.y
  const width = normalized ? box.width * pageWidth : box.width
  const height = normalized ? box.height * pageHeight : box.height
  const scaleX = renderedWidth / Math.max(1, pageWidth)
  const scaleY = renderedHeight / Math.max(1, pageHeight)
  return { left: x * scaleX, top: y * scaleY, width: width * scaleX, height: height * scaleY }
}

export const bestLocation = (field: ExtractedFieldResponse | null | undefined): FieldLocation | null => {
  const locations = field?.locations ?? []
  return locations.reduce<FieldLocation | null>((best, location) => {
    if (!best) return location
    return (location.matchConfidence ?? -1) > (best.matchConfidence ?? -1) ? location : best
  }, null)
}

const labels: Record<string, string> = {
  invoiceNumber: 'Số hóa đơn', invoiceSeries: 'Ký hiệu hóa đơn', invoiceDate: 'Ngày hóa đơn',
  sellerName: 'Tên người bán', sellerTaxCode: 'Mã số thuế người bán', sellerAddress: 'Địa chỉ người bán',
  sellerPhone: 'Số điện thoại người bán', buyerName: 'Tên người mua', buyerTaxCode: 'Mã số thuế người mua',
  buyerAddress: 'Địa chỉ người mua', subtotal: 'Tổng tiền trước thuế', vatAmount: 'Tiền thuế GTGT',
  taxAmount: 'Tiền thuế', totalAmount: 'Tổng tiền thanh toán', paymentMethod: 'Hình thức thanh toán',
  amountInWords: 'Số tiền bằng chữ', taxAuthorityCode: 'Mã cơ quan thuế', signDate: 'Ngày ký',
}

const vietnameseWords: Record<string, string> = {
  phi: 'Phí', van: 'vận', chuyen: 'chuyển', giam: 'giảm', gia: 'giá', ghi: 'Ghi', chu: 'chú',
  tuyen: 'Tuyển', dung: 'dụng', thuong: 'thường', xuyen: 'xuyên', so: 'Số', ngay: 'Ngày', tien: 'tiền',
}

export const fieldLabel = (name: string) => {
  if (labels[name]) return labels[name]
  const words = name.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim().split(/\s+/)
  const humanized = words.map((word, index) => {
    const translated = vietnameseWords[word.toLocaleLowerCase('vi-VN')] ?? word.toLocaleLowerCase('vi-VN')
    return index === 0 ? translated.charAt(0).toLocaleUpperCase('vi-VN') + translated.slice(1) : translated
  }).join(' ')
  return humanized || 'Thông tin bổ sung'
}

const groups: Record<string, string> = {
  invoiceNumber: 'Thông tin hóa đơn', invoiceSeries: 'Thông tin hóa đơn', invoiceDate: 'Thông tin hóa đơn',
  sellerName: 'Người bán', sellerTaxCode: 'Người bán', sellerAddress: 'Người bán', sellerPhone: 'Người bán',
  buyerName: 'Người mua', buyerTaxCode: 'Người mua', buyerAddress: 'Người mua',
  subtotal: 'Thanh toán', vatAmount: 'Thanh toán', taxAmount: 'Thanh toán', totalAmount: 'Thanh toán',
  paymentMethod: 'Thanh toán', amountInWords: 'Thanh toán', taxAuthorityCode: 'Thông tin hóa đơn điện tử',
  signDate: 'Thông tin hóa đơn điện tử',
}

export const fieldGroup = (name: string, isVatInvoice: boolean) => {
  if (!isVatInvoice) return /(recruit|advert|marketing|slogan|tuyenDung|quangCao)/i.test(name) ? 'Thông tin bổ sung' : 'Thông tin chính'
  return groups[name] ?? 'Thông tin bổ sung'
}


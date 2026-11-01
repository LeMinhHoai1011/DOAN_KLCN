export const assessmentTerms: Record<string, string> = {
  DOCUMENT_TYPE_REASON: 'Căn cứ phân loại chứng từ',
  MISSING_DOCUMENT_TYPE_EVIDENCE: 'Chưa có đủ căn cứ phân loại chứng từ.',
  MISSING_CLASSIFICATION_CONFIDENCE: 'AI chưa cung cấp độ tin cậy phân loại.',
  LOW_CLASSIFICATION_CONFIDENCE: 'Độ tin cậy phân loại thấp; cần xác nhận loại chứng từ.',
  MISSING_INVOICE_EXTRACTION: 'Chưa đọc được thông tin hóa đơn.',
  MISSING_INVOICE_NUMBER: 'Thiếu số hóa đơn.', MISSING_INVOICE_DATE: 'Thiếu ngày hóa đơn.',
  MISSING_SELLER_NAME: 'Thiếu tên người bán.', MISSING_SELLER_TAX_CODE: 'Thiếu mã số thuế người bán.',
  MISSING_TOTAL_AMOUNT: 'Thiếu tổng thanh toán.', MISSING_SUBTOTAL: 'Thiếu tổng tiền trước thuế.',
  MISSING_VAT_AMOUNT: 'Thiếu tiền thuế GTGT.', INVOICE_ITEMS_MISSING: 'Chưa đọc được danh sách hàng hóa.',
  INVALID_SELLER_TAX_CODE: 'Mã số thuế người bán chưa hợp lệ.', INVALID_BUYER_TAX_CODE: 'Mã số thuế người mua chưa hợp lệ.',
  INVALID_INVOICE_DATE: 'Ngày hóa đơn chưa hợp lệ.', INVALID_SIGN_DATE: 'Ngày ký chưa hợp lệ.',
  TOTAL_MISMATCH: 'Tổng thanh toán không khớp tiền trước thuế và tiền thuế.',
  ITEM_AMOUNT_MISMATCH: 'Số lượng × đơn giá không khớp thành tiền. Kiểm tra dấu chấm và dấu phẩy trên chứng từ.',
  ITEM_TAX_MISMATCH: 'Tiền thuế hàng hóa không khớp thành tiền và thuế suất.',
  INVALID_INVOICE_ITEM: 'Có dòng hàng hóa không hợp lệ.', MISSING_INVOICE_ITEM_NAME: 'Thiếu tên hàng hóa.',
  MISSING_INVOICE_ITEM_AMOUNTS: 'Thiếu số lượng, đơn giá hoặc thành tiền.',
  NEGATIVE_ITEM_QUANTITY: 'Số lượng hàng hóa âm.', NEGATIVE_ITEM_UNIT_PRICE: 'Đơn giá hàng hóa âm.',
  NEGATIVE_ITEM_TAX_AMOUNT: 'Tiền thuế hàng hóa âm.', NEGATIVE_ITEM_AMOUNT: 'Thành tiền hàng hóa âm.',
  INVALID_ITEM_TAX_RATE: 'Thuế suất hàng hóa chưa hợp lệ.',
  INVALID_ACCOUNTING_CATEGORY: 'Nhóm nghiệp vụ AI đề xuất chưa phù hợp danh mục công ty.',
  RETAIL_DATA_INSUFFICIENT: 'Chưa đủ dữ liệu đối soát hóa đơn bán lẻ.', RETAIL_AMOUNT_MISMATCH: 'Số tiền hóa đơn bán lẻ chưa khớp.',
}
export const assessmentCode = (text: string) => text.match(/^([A-Z][A-Z0-9_]*):/)?.[1] ?? ''
export const assessmentMessage = (text: string, explicitCode?: string) => {
  const code = explicitCode || assessmentCode(text)
  const row = text.match(/(?:row|line)\s*=\s*(\d+)/i)?.[1]
  const label = assessmentTerms[code] ?? (code.startsWith('EXTRACTION_FAILED_') ? 'Không đọc rõ một trường trên chứng từ; cần đối chiếu ảnh gốc.' : 'Có thông tin cần đối chiếu với chứng từ gốc.')
  return row ? `Dòng ${row}: ${label}` : label
}
export const reviewMessages = (reasons: string[]) => [...new Set(reasons
  .filter(text => assessmentCode(text) !== 'DOCUMENT_TYPE_REASON')
  .map(text => assessmentMessage(text)))]

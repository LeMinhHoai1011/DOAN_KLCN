/**
 * Row discounts ("Tiền chiết khấu") come from the latest AI reading: the invoice has no discount column, so the
 * backend attaches them only to rows that still have the values they were read with.
 */
export const hasItemDiscount = (items: ReadonlyArray<{ discountAmount?: number | null }>) =>
  items.some(item => item.discountAmount != null && item.discountAmount !== 0)

export const ITEM_DISCOUNT_NOTE = 'Chiết khấu dòng theo hóa đơn; thành tiền đã trừ chiết khấu. Giá trị này lấy từ lần AI đọc gần nhất và tự ẩn nếu dòng đã được sửa.'

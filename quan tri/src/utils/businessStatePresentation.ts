export function statePresentation(group: string, state: string | null): {label: string; tone: string} {
 const labels: Record<string, Record<string,string>> = {
 processing: {UPLOADED:'Đã tải lên',PROCESSING:'Đang xử lý',PROCESSED:'Đã xử lý',NEED_REVIEW:'Cần kiểm tra',FAILED:'Xử lý thất bại'},
 classification: {PENDING:'Chờ phân loại',COMPLETED:'Đã phân loại',FAILED:'Phân loại thất bại'},
 reconciliation: {NOT_RUN:'Chưa đối soát',MATCHED:'Khớp',WARNING:'Có cảnh báo',MISMATCH:'Sai lệch'},
 review: {PENDING:'Chờ duyệt',IN_REVIEW:'Đang duyệt',APPROVED:'Đã duyệt',REJECTED:'Đã từ chối'},
 payment: {UNPAID:'Chưa thanh toán',PARTIALLY_PAID:'Thanh toán một phần',PAID:'Đã thanh toán'} };
 const tone = state && ['FAILED','REJECTED','MISMATCH'].includes(state) ? 'text-failed' : state && ['NEED_REVIEW','WARNING','PARTIALLY_PAID'].includes(state) ? 'text-review' : state && ['APPROVED','COMPLETED','PROCESSED','MATCHED','PAID'].includes(state) ? 'text-parsed' : 'text-ink-muted';
 return {label: state ? labels[group]?.[state] || 'Chưa xác định' : group === 'payment' ? 'Chưa có dữ liệu thanh toán' : 'Chưa xác định', tone};
}

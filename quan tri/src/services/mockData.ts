export interface OcrData {
  taxCode: string;
  invoiceNo: string;
  address: string;
  phone: string;
  subTotal: number;
  vatAmount: number;
  paymentMethod: string;
}

export interface MockDocument {
  id: string;
  fileName: string;
  type: string;
  supplier: string;
  date: string;
  amount: number;
  aiClassification: string;
  aiConfidence: number;
  status: string;
  ocrData: OcrData;
}

export const generateMockDocuments = (): MockDocument[] => {
  const types = ['Hóa đơn mua hàng', 'Hóa đơn bán hàng', 'Phiếu thu', 'Phiếu chi', 'Biên lai', 'Khác'];
  const statuses = ['Đã xử lý', 'Cần kiểm tra', 'Đang xử lý', 'Lỗi'];
  const suppliers = ['Công ty TNHH ABC', 'Tập đoàn XYZ', 'Cửa hàng tiện lợi 24h', 'Công ty CP Công Nghệ VNG', 'FPT Telecom'];
  const ais = ['Chi phí văn phòng', 'Chi phí vận chuyển', 'Chi phí tiếp khách', 'Chi phí nguyên vật liệu', 'Chi phí dịch vụ', 'Tài sản', 'Khác'];

  return Array.from({ length: 20 }, (_, index) => {
    const status = statuses[Math.floor(Math.random() * statuses.length)];
    const aiConfidence = Math.floor(Math.random() * 40) + 60;
    const isLowConfidence = aiConfidence < 80;

    return {
      id: `DOC-${String(index + 1).padStart(3, '0')}`,
      fileName: `invoice_${index + 1}.pdf`,
      type: types[Math.floor(Math.random() * types.length)],
      supplier: suppliers[Math.floor(Math.random() * suppliers.length)],
      date: new Date(Date.now() - Math.floor(Math.random() * 10000000000)).toISOString().split('T')[0],
      amount: Math.floor(Math.random() * 50000000) + 500000,
      aiClassification: ais[Math.floor(Math.random() * ais.length)],
      aiConfidence,
      status: status === 'Đã xử lý' && isLowConfidence ? 'Cần kiểm tra' : status,
      ocrData: {
        taxCode: `010${Math.floor(1000000 + Math.random() * 9000000)}`,
        invoiceNo: `INV${Math.floor(10000 + Math.random() * 90000)}`,
        address: '123 Đường ABC, Quận 1, TP. HCM',
        phone: `090${Math.floor(1000000 + Math.random() * 9000000)}`,
        subTotal: Math.floor(Math.random() * 40000000) + 500000,
        vatAmount: Math.floor(Math.random() * 5000000),
        paymentMethod: Math.random() > 0.5 ? 'Chuyển khoản' : 'Tiền mặt',
      }
    };
  });
};

export const mockDocuments = generateMockDocuments();

export const mockDashboardStats = {
  total: 2458,
  processed: 2210,
  processing: 128,
  needsReview: 95,
  error: 25,
};

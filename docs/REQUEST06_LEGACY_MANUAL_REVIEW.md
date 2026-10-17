# Giao dịch cũ cần đối soát thủ công — 2026-10-16

Nguồn: SELECT read-only invoice_db/public. Không có bằng chứng nguồn gốc/xác nhận tiền cho các dòng này; không suy từ ACTIVE/COMPLETED hoặc trạng thái duyệt thành đã thanh toán. Không thay đổi dữ liệu. Chỉ ghi ID/liên kết/trạng thái, không xuất số tiền, nội dung chứng từ hoặc thông tin tài khoản.

| Transaction ID | Company ID | Document ID | InvoiceData ID | Trạng thái cũ | Thu/Chi | Xác minh thanh toán |
|---|---|---|---|---|---|---|
| 1 | 13 | 151 | 87 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 2 | 13 | 137 | 73 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 3 | 13 | 136 | 72 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 4 | 13 | 149 | 85 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 5 | 13 | 135 | 71 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 6 | 13 | 131 | 67 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 7 | 13 | 142 | 78 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 8 | 13 | 146 | 82 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 9 | 13 | 139 | 75 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 10 | 13 | 143 | 79 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 11 | 13 | 145 | 81 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 12 | 13 | 140 | 76 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 13 | 13 | 153 | 89 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 14 | 13 | 152 | 88 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 15 | 13 | 133 | 69 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 16 | 13 | 158 | 94 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 17 | 13 | 129 | 65 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 18 | 13 | 155 | 91 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 19 | 13 | 156 | 92 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 20 | 13 | 132 | 68 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 21 | 13 | 134 | 70 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 22 | 13 | 141 | 77 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 23 | 13 | 138 | 74 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 24 | 13 | 148 | 84 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 25 | 13 | 147 | 83 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 26 | 13 | 157 | 93 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 27 | 13 | 154 | 90 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 28 | 13 | 150 | 86 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 29 | 13 | 130 | 66 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 30 | 13 | 144 | 80 | COMPLETED | EXPENSE | CHƯA XÁC MINH |
| 31 | 13 |  | 130 | ACTIVE | INCOME | CHƯA XÁC MINH |
| 32 | 13 |  | 131 | ACTIVE | INCOME | CHƯA XÁC MINH |

Tổng: 32 dòng. Việc xác minh hoặc xử lý riêng từng dòng cần đối chiếu chứng từ ngân hàng/tiền mặt và người có quyền phê duyệt; không tự backfill/xóa/chuyển trạng thái.

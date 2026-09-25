# Tổng quan Kiến trúc và Tình trạng Dự án (DOAN_KLCN)

Tài liệu này tổng hợp cấu trúc hiện tại của toàn bộ dự án bao gồm cả Frontend và Backend, giúp bạn dễ dàng theo dõi, dò luồng và hoàn thiện cấu trúc bên ngoài.

---

## 1. Kiến trúc Tổng thể
Hệ thống là một ứng dụng quản lý chứng từ và hóa đơn, có thiết kế hỗ trợ tích hợp luồng xử lý AI/OCR (nhận diện ký tự) và phân loại chứng từ tự động.
- **Frontend**: Ứng dụng SPA (Single Page Application) sử dụng React + Vite. Nằm trong thư mục `quan tri`.
- **Backend**: Cung cấp RESTful API viết bằng Java Spring Boot. Nằm trong thư mục `backendqt`.
- **Cơ sở dữ liệu (Database)**: Hiện tại đang sử dụng cấu hình H2 (in-memory) để phát triển nhanh, không cần cài đặt DB thực. Đã cấu hình sẵn profile để chuyển sang sử dụng PostgreSQL khi lên môi trường thật.

---

## 2. Phân tích Frontend (`/quan tri`)

### Công nghệ sử dụng
- **Lõi**: React 19, TypeScript, Vite.
- **UI & Styling**: TailwindCSS 4, Lucide React (Icons).
- **Routing & State**: React Router DOM, Axios (gọi API).
- **Thống kê**: Chart.js & React-chartjs-2.

### Cấu trúc tính năng (Dựa trên `src/App.tsx`)
Ứng dụng được chia theo luồng **Role-based (Dựa trên vai trò)** với 3 layout và khu vực riêng biệt:
1. **Admin (Quản trị viên)**:
   - Các trang: Dashboard, User Management (Quản lý người dùng), Xem chi tiết Document.
   - *Tính năng Placeholder (Chưa có API)*: Quản lý vai trò (Roles), Thống kê nâng cao, Cài đặt.
2. **Accountant (Kế toán)**:
   - Các trang: Dashboard, Quản lý toàn bộ danh sách chứng từ (Documents & Detail), Tải lên chứng từ (Upload).
   - *Tính năng Placeholder (Chưa có API)*: OCR & AI Tracking, Kho lưu trữ, Phân loại, Báo cáo.
3. **Employee (Nhân viên)**:
   - Các trang: Dashboard, Xem chứng từ của mình, Tải lên chứng từ mới.

### Tình trạng & Các vấn đề cần hoàn thiện
- Hệ thống định tuyến (Route Guard) đã hoạt động để phân rẽ giao diện, tuy nhiên hiện tại frontend đang dựa quá nhiều vào thông tin `role` lưu trong `localStorage` mà không đồng bộ hoặc kiểm tra kỹ lại với Backend khi reload trang.
- Chưa xử lý tập trung trường hợp JWT Token hết hạn hoặc sai (lỗi 401 Unauthorized), nên việc văng ra màn hình đăng nhập có thể chưa mượt.
- Các màn hình API chưa được cung cấp ở Backend thì Frontend đang sử dụng component `UnavailableFeature` để hiển thị tạm.

---

## 3. Phân tích Backend (`/backendqt/backendqt`)

### Công nghệ sử dụng
- **Lõi**: Java 17+, Spring Boot 3, Maven.
- **Bảo mật**: Spring Security, JWT (Stateless Authentication), BCrypt (mã hóa mật khẩu).
- **Database**: H2 Database (Hiện tại), PostgreSQL (Sắp tới), JPA/Hibernate.

### Cấu trúc Database Entity
Hệ thống thiết kế CSDL khá phức tạp và chi tiết để phục vụ nghiệp vụ kế toán, các Entity chính bao gồm:
- **Người dùng**: `User`, `Role`, enum `UserRole`, `Company`.
- **Hồ sơ & Chứng từ**: `Document`, `DocumentVersion` (lưu vết lịch sử).
- **Hóa đơn**: `Invoice`, `InvoiceItem` (chi tiết từng dòng hóa đơn).
- **AI & Phân loại**: `OCRResult` (Kết quả quét văn bản), `ExtractedField`, `Classification`, `ClassificationCorrection` (sửa lỗi phân loại AI), `ProcessingLog`.
- **Kế toán**: `AccountingCategory`, `AccountingEntry`.

### Danh sách API (RESTful)
- **Xác thực (`/api/v1/auth`)**: Đăng ký (`/register`), Đăng nhập (`/login`).
- **Người dùng (`/api/v1/users`)**: Xem/Cập nhật thông tin cá nhân (`/me`), Đổi mật khẩu.
- **Chứng từ (`/api/v1/documents`)**: Upload chứng từ, CRUD, xem OCR (`/ocr`), và luồng Duyệt/Đánh giá phân loại (`/classification`, `/approve`, `/review`, `/correction`).
- **Hóa đơn (`/api/v1/invoices`)**: CRUD hóa đơn.
- **Thống kê (`/api/v1/dashboard`)**: API lấy số liệu tổng quan (`/statistics`).

### Tình trạng & Các vấn đề cần hoàn thiện (Từ `ROLE_AUTH_AUDIT.md`)
- **Phân quyền (Authorization)**: Backend hiện chỉ yêu cầu Token hợp lệ (`.authenticated()`), **CHƯA** kiểm tra kỹ Role tại từng API. Dẫn đến việc Nhân viên có thể gọi API của Admin hoặc Kế toán nếu biết đường dẫn.
- **Data Isolation (Phân quyền dữ liệu)**: Chưa có bộ lọc dữ liệu theo user. Ví dụ: API `/documents` đang trả về toàn bộ chứng từ cho mọi role, thay vì chỉ trả về chứng từ của Employee đó tạo (IDOR vulnerability).
- **Vai trò (Roles)**: Cần chuẩn hóa enum chỉ còn 3 loại `ADMIN`, `ACCOUNTANT`, `EMPLOYEE` và ngăn chặn việc User tự do chọn Role khi đăng ký mới.
- **Thiếu API**: Chưa có API Quản lý User (`CRUD User`) cho Admin, khiến giao diện Admin hiện đang thiếu dữ liệu thật.

---

## 4. Gợi ý Kế hoạch hoàn thiện (Next Steps)

Để kết nối và hoàn thiện cấu trúc bên ngoài giữa Frontend và Backend, bạn nên ưu tiên các bước sau:

1. **Khóa luồng đăng ký & Chuẩn hóa Role**:
   - Backend: Cập nhật enum role, chặn người dùng tự do gửi role `ADMIN` qua API `/register`.
2. **Hoàn thiện Authorization Backend**:
   - Thêm Annotation `@PreAuthorize("hasRole('ADMIN')")` cho các chức năng nhạy cảm.
   - Thêm logic vào Service để: Nếu là `EMPLOYEE`, truy vấn DB chỉ lấy `Document` thuộc về `userId` đó.
3. **Phát triển API User Management**:
   - Viết API GET/POST/PUT/DELETE `/api/v1/users` cho phép Admin quản trị hệ thống.
4. **Cập nhật luồng xác thực Frontend**:
   - Cấu hình Axios Interceptor để bắt lỗi HTTP 401 -> Tự động xóa Token và chuyển hướng về Login.
   - Khi vừa Login, fetch ngay `/api/v1/users/me` để lấy `Role` chuẩn nhất từ server thay vì tin tưởng payload có sẵn từ cũ.
5. **Gắn API vào Frontend**:
   - Áp dụng API User Management mới vào trang `UserManagement.tsx` của Admin.
   - Kiểm tra lại các trang `Documents` để đảm bảo Kế toán nhìn thấy tất cả, và Nhân viên chỉ thấy của mình.

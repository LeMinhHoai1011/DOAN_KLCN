# Báo cáo kiểm tra phân quyền và giao diện theo role

## 1. Những gì đang hoạt động

- Frontend React/Vite có login, register, layout chung, Admin layout và Employee layout.
- Backend Spring Security dùng JWT stateless, BCrypt `PasswordEncoder` và filter xác thực Bearer token.
- JWT được tạo sau khi đăng nhập và frontend lưu token để gửi qua `Authorization`.
- Có entity `User`, `Role`, enum `UserRole`, `UserStatus` và các API nghiệp vụ chứng từ/hóa đơn/OCR/classification.
- Có seed tài khoản admin hiện tại thông qua `DemoAdminInitializer`.

## 2. Những gì chưa hoạt động đúng

- Role hiện tại chỉ có `ADMIN` và `USER`; chưa có `ACCOUNTANT` và `EMPLOYEE`.
- Backend chỉ yêu cầu `.authenticated()`, chưa kiểm tra role ở endpoint nghiệp vụ.
- Dữ liệu document/invoice/classification chưa giới hạn theo user hoặc company; có nguy cơ IDOR.
- Frontend dùng `localStorage.user.role` làm nguồn điều hướng, không đồng bộ lại với backend.
- Route chung `/documents`, `/upload` mở cho mọi user đăng nhập và có thể làm lẫn khu vực role.
- Admin User Management chưa có API quản lý user tương ứng; trang hiện tại là placeholder.
- Chưa có xử lý tập trung cho JWT hết hạn/sai và HTTP 401 ở frontend.
- Chưa có test authorization cho ba role, ownership, disabled user và token lỗi.

## 3. Những gì còn thiếu

- Chuẩn hóa ba role `ADMIN`, `ACCOUNTANT`, `EMPLOYEE`.
- Authorization backend bằng Spring Security cho API admin và API nghiệp vụ.
- API admin quản lý user tối thiểu để trang quản trị có dữ liệu thật.
- Route guard riêng cho Admin, Accountant và Employee.
- Tách/hoàn thiện giao diện Accountant và Employee theo quyền, giữ lại API thật hiện có.
- Tài khoản mẫu cho ba role.
- Test backend/frontend build và các ca kiểm thử quyền.

## 4. Những gì cần sửa

1. Dùng enum role làm nguồn quyền duy nhất trong authentication; giữ `roleRef` để tương thích schema nhưng không dùng song song để cấp quyền.
2. Thêm `ACCOUNTANT`, `EMPLOYEE`; không cho đăng ký public tự chọn quyền.
3. Thêm authorization ở `SecurityConfig` và method/controller, đặc biệt API user chỉ dành cho ADMIN.
4. Giới hạn danh sách/chi tiết document và invoice theo company hoặc người tạo đối với EMPLOYEE; ADMIN/ACCOUNTANT được xử lý nghiệp vụ theo phạm vi hiện tại.
5. Đồng bộ role sau login từ response backend và khóa các route trái role.
6. Cập nhật seed admin và thêm seed accountant/employee chỉ khi username chưa tồn tại.
7. Chỉ kết nối các UI với API đang tồn tại; các chức năng chưa có API phải hiển thị rõ là chưa hỗ trợ.

## 5. Những gì không nên sửa

- Không đổi toàn bộ prefix API hiện tại.
- Không xóa các trang/chức năng chứng từ, hóa đơn, OCR và classification đang dùng.
- Không tạo mock API thay cho endpoint backend thật.
- Không refactor lớn database hoặc bỏ `roleRef` ngay khi chưa có migration; trước mắt dùng enum `User.role` nhất quán.
- Không để frontend là lớp bảo mật duy nhất.

## 6. Ma trận mục tiêu

| Khu vực | ADMIN | ACCOUNTANT | EMPLOYEE |
|---|---:|---:|---:|
| Đăng nhập / hồ sơ cá nhân | Có | Có | Có |
| Quản lý user / role | Có | Không | Không |
| Dashboard hệ thống | Có | Không | Không |
| Xem và xử lý chứng từ | Có | Có | Giới hạn dữ liệu của mình |
| Upload chứng từ | Theo API | Có | Có theo phạm vi |
| OCR / AI / classification | Xem | Xử lý | Xem theo quyền |
| Xóa hoặc thay đổi dữ liệu | Theo quyền quản trị/nghiệp vụ | Nghiệp vụ | Không sửa dữ liệu đã xác nhận |

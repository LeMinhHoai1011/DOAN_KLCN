# Hoàn thiện quản lý vai trò và cấp quyền trực tiếp

Ngày kiểm tra: 30/10/2026.

## Điều chỉnh giao diện theo yêu cầu tiếp theo

Trang tài khoản hiện chỉ cho chọn **một** vai trò đang hoạt động: ACCOUNTANT hoặc EMPLOYEE, bằng radio. Không có lựa chọn ADMIN hoặc role tùy chỉnh, bao gồm form tạo tài khoản. Với tài khoản đang có nhiều vai trò, phải chọn rõ một vai trò trước khi lưu; không tự sửa dữ liệu khi mở form. Lưu vẫn gửi snapshot đầy đủ của các role cũ để chống ghi đè và giữ quy tắc bảo vệ ADMIN hiện có. Backend nhiều role vẫn giữ tương thích cho client hiện hữu. Các mô tả nhiều vai trò bên dưới ghi lại phạm vi của lần triển khai trước, đã được điều chỉnh tại giao diện theo yêu cầu này.

## Chức năng hoàn thiện

- `/admin/roles`: tìm vai trò, hiển thị trạng thái, tạo/sửa thông tin, bảo vệ mã và trạng thái role hệ thống.
- Permission được chỉnh trong bản nháp; chọn từng quyền hoặc cả nhóm chức năng, có trạng thái chọn một phần.
- Nút **Lưu thay đổi** gửi một PUT cho toàn bộ permission. **Hủy** trả về bản đã tải/lưu. Không có request ghi khi tick.
- Khóa thao tác liên quan khi request đang chạy; giữ bản nháp khi API từ chối và chỉ thông báo thành công sau phản hồi thành công.
- Permission không thuộc nhóm được đưa vào mục Quyền khác. Permission inactive được hiển thị chỉ đọc; backend không cho cấp mới permission inactive.
- Role USER lịch sử chỉ được xem quyền. ADMIN vẫn giữ các ngoại lệ truy cập theo role ở API hiện có; giao diện giải thích điều này.
- Sao chép role qua một API transaction: sao chép các permission active đã lưu, không sao chép tài khoản được gán. Không sao chép USER lịch sử. Nếu tạo bản sao thất bại, transaction rollback.
- `/admin/users`: hiển thị đầy đủ role trực tiếp, bao gồm role inactive; chọn nhiều role active hợp lệ và lưu một lần.
- Không cho chọn mới USER hoặc role inactive. Tài khoản có USER lịch sử không được sửa danh sách role.
- Hiển thị hợp permission active từ role active dưới dạng chỉ đọc, kèm thông tin ngoại lệ ADMIN và trạng thái khóa tài khoản. Danh sách này không thay thế các điều kiện nghiệp vụ/phạm vi dữ liệu tại API.
- Form tạo tài khoản chỉ đưa ra role active thuộc ADMIN, ACCOUNTANT, EMPLOYEE; validation độ dài khớp DTO backend.
- Có xác nhận khi tạo tài khoản ADMIN, thay đổi vai trò tài khoản liên quan ADMIN, thay đổi permission/metadata ADMIN và khóa/mở tài khoản ADMIN.

## API và cập nhật đồng thời

Giữ các API hiện có và bổ sung:

- `POST /api/v1/roles/{id}/copy`: tạo bản sao trong một transaction, chỉ ADMIN.
- `GET /api/v1/users/{id}/access`: trả đầy đủ role trực tiếp và permission active, chỉ ADMIN.
- PUT permission nhận thêm `expectedPermissionIds`.
- PUT role metadata nhận thêm `expected: { code, name, description, active }`.
- PUT role của tài khoản nhận thêm `expectedRoleIds`.

Frontend gửi snapshot gốc. Backend khóa bản ghi role trước khi so sánh/sửa permission hoặc metadata. Gán role tài khoản tiếp tục dùng khóa ADMIN guard hiện có và so sánh snapshot sau khi lấy khóa. Snapshot không khớp trả **409 / RBAC_UPDATE_CONFLICT** trước khi sửa dữ liệu. Không tự hợp nhất hoặc tự thử lại bằng snapshot mới.

Khi sửa permission, giữ lại các dòng role_permissions còn được chọn để tránh insert lại dòng cùng unique key trước khi orphan removal hoàn tất.

Các trường expected là tùy chọn để giữ tương thích với client cũ. Client không gửi snapshot vẫn dùng hành vi cũ và không được bảo vệ khỏi ghi đè từ dữ liệu cũ ở phía client đó. Đây là so sánh trạng thái, không phải bộ đếm phiên bản lịch sử.

## Phạm vi giữ nguyên

- Không sửa entity/bảng, không migration và không tác động database vận hành trong quá trình kiểm thử.
- Không tạo groups, group_members, group_roles hoặc cơ chế kế thừa nhóm.
- Không đổi JWT, các quy tắc ADMIN cuối cùng/tự bỏ ADMIN, USER lịch sử, bộ lọc công ty/người tải lên hoặc workflow kế toán.
- Không thay hàng loạt hasRole bằng hasAuthority.
- Không commit/push. Các thay đổi mobile có sẵn trong workspace không thuộc đợt sửa này.

## File thay đổi

Backend — tương đối với `backendqt/backendqt/src/main/java/com/example/invoice/`:

- `controller/RoleController.java`
- `controller/UserController.java`
- `dto/role/RoleCreateRequest.java`
- `dto/role/RoleUpdatePermissionsRequest.java`
- `dto/user/AssignRolesRequest.java`
- `dto/user/UserAccessResponse.java` — mới
- `exception/RbacConflictException.java` — mới
- `exception/GlobalExceptionHandler.java`
- `repository/RoleRepository.java`
- `service/RoleService.java`
- `service/UserService.java`

Frontend — tương đối với `quan tri/`:

- `src/pages/admin/RoleManagement.tsx`
- `src/pages/admin/UserManagement.tsx`
- `src/components/admin/PermissionChecklist.tsx` — mới
- `src/services/roleService.ts`
- `src/utils/rbacDraft.ts` — mới
- `src/utils/rbacDraft.test.mjs` — mới
- `scripts/rbac-ui-check.mjs` — mới
- `package.json` — thêm test RBAC vào test mặc định và lệnh test trình duyệt

Backend test:

- `backendqt/backendqt/src/test/java/com/example/invoice/RbacManagementIntegrationTest.java` — mới
- `backendqt/backendqt/src/test/java/com/example/invoice/service/SystemRoleProtectionTest.java` — cập nhật mock theo phương thức khóa ghi

## Kiểm thử đã chạy

1. `npm.cmd run build`: thành công. Vite còn cảnh báo bundle lớn hơn 500 kB; chưa chỉnh bundling ngoài phạm vi.
2. `npm.cmd test`: **66/66 test đạt**.
3. Playwright/Edge headless với API giả lập: **đạt** cho tick nhiều quyền, chọn cả nhóm, Hủy, một request lưu, khóa khi chờ, lỗi 409/403 giữ bản nháp và không báo thành công, xác nhận ADMIN, bảo vệ role hệ thống/USER, tạo/sao chép role, nhiều role tài khoản, quyền chỉ đọc và lọc role tạo tài khoản.
4. Backend: **58/58 test đạt**, không bỏ qua; gồm 8 integration test RBAC mới và các bộ test bảo vệ ADMIN, USER/system role, tạo tài khoản, gán nhiều role, JWT và token version.
5. `git diff --check` trên backend/frontend: đạt.

Backend chạy với các test cụ thể trên H2 in-memory riêng biệt, không dùng datasource vận hành:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=C:/Users/LENOVO/.m2/repository' '-Dtest=RbacManagementIntegrationTest,SystemRoleProtectionTest,AdminProtectionTest,UserRoleUpdateTest,AdminUserCreationTest,UserMapperDynamicRoleTest,JwtAuthenticationFilterTest,JwtServiceTest,TokenVersionClaimTest,AdminGuardConcurrencyTest' test -q
```

Integration test xác minh hai transaction sửa permission cùng snapshot chỉ có một bên thành công; tương tự với hai transaction gán role cùng tài khoản. Cũng kiểm tra permission được thực thi ở API chứng từ với JWT đã phát hành, sau khi cấp và thu hồi permission.

Test trình duyệt dùng Vite chạy ở `http://127.0.0.1:5173` và chặn API bằng fixture. Chạy `npm.cmd run test:rbac-ui` khi có Playwright; có thể đặt `PLAYWRIGHT_MODULE` thành URL ESM của Playwright hiện có, `BROWSER_CHANNEL` để chọn browser, `UI_BASE_URL` để đổi địa chỉ Vite. Phiên kiểm tra này dùng Playwright từ runtime sẵn có và Edge; không thêm dependency Playwright vào dự án.

Ảnh đã kiểm tra bố cục:

- [Trang vai trò](../quan%20tri/artifacts/rbac-review/roles.png)
- [Trang tài khoản](../quan%20tri/artifacts/rbac-review/users.png)

## Giới hạn còn tồn tại

- Chưa mở API tạo tài khoản trực tiếp bằng role tùy chỉnh. Có thể tạo với role hệ thống hợp lệ rồi gán role tùy chỉnh bằng chức năng nhiều role. Frontend vẫn định tuyến theo bốn role hệ thống; tài khoản chỉ có role tùy chỉnh chưa có workspace riêng. Không tự mở rộng nghiệp vụ này khi chưa thống nhất hợp đồng API/client.
- Permission không thay thế mọi kiểm tra role: có permission vẫn có thể bị chặn bởi role, công ty, chủ sở hữu hoặc workflow. ADMIN vẫn có các ngoại lệ hiện hữu.
- Việc bỏ flag active của role có thể làm mất quyền truy cập của tài khoản chỉ mang role đó; vai trò hệ thống được bảo vệ như trước.
- Trang tài khoản tải access cho từng tài khoản sau khi tải danh sách. Với dữ liệu lớn nên bổ sung pagination/batch endpoint ở đợt tối ưu riêng.
- Kiểm thử browser dùng API giả lập, backend integration dùng H2; chưa chạy toàn bộ luồng browser kết nối PostgreSQL vận hành hoặc kiểm thử khóa trên PostgreSQL thật.
- Snapshot bảo vệ giao diện mới; client cũ không gửi expected vẫn giữ hợp đồng cũ. Tắt trang/tab được cảnh báo khi còn bản nháp; chưa bổ sung router blocker cho mọi điều hướng nội bộ.

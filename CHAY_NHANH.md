# Cách chạy nhanh project

Thực hiện bằng **PowerShell tại thư mục gốc `DOAN_KLCN`**.

## 1. Chuẩn bị lần đầu

- Cài **JDK 21, Node.js 22 LTS, PostgreSQL, MinIO và Ollama**.
- Chuẩn bị database `invoice_db` có schema tương thích. Backend chỉ kiểm tra schema, không tự tạo bảng.
- Tạo cấu hình nếu chưa có (không ghi đè cấu hình đang dùng):

```powershell
if (!(Test-Path config/config.local.json)) { Copy-Item config/config.example.json config/config.local.json }
if (!(Test-Path .env)) { Copy-Item .env.example .env }
```

Sửa `config/config.local.json`: thông tin PostgreSQL, đường dẫn `minio.exe`, thư mục dữ liệu MinIO và địa chỉ Ollama.
Sửa `.env`: `DB_PASSWORD`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `JWT_SECRET` (tối thiểu 32 byte).

```powershell
Push-Location "quan tri"
npm.cmd install
Pop-Location
ollama pull qwen3-vl:8b
```

Nếu dùng Ollama trên máy khác, tải model trên máy đó và đặt `ai.ollama.host` trong cấu hình thành IP máy chạy Ollama.

## 2. Chạy hệ thống

Khởi động dịch vụ PostgreSQL, sau đó chạy:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\start.ps1 all
```

Script chạy/kiểm tra MinIO, Ollama, backend và frontend.

| Trang | Địa chỉ mặc định |
| --- | --- |
| Web | http://localhost:5173 |
| Swagger API | http://localhost:8081/swagger-ui.html |
| MinIO Console | http://localhost:9001 |

## 3. Chạy riêng và dừng

```powershell
.\scripts\start.ps1 backend
.\scripts\start.ps1 frontend
.\scripts\start.ps1 minio
.\scripts\start.ps1 ollama
```

Dừng backend/frontend bằng `Ctrl+C` trong từng cửa sổ chạy. Dừng tiến trình MinIO/Ollama khi không còn sử dụng.

Cấu hình database mới, nâng cấp schema và xử lý lỗi: xem [hướng dẫn đầy đủ](HUONG_DAN_CAU_HINH_VA_CHAY.md).

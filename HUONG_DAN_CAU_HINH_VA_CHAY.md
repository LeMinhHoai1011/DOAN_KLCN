# Hướng dẫn cấu hình và chạy project DOAN_KLCN

Tài liệu này áp dụng cho môi trường Windows/PowerShell và script khởi động `scripts/start.ps1` hiện có trong project.

## 1. Thành phần của hệ thống

- Backend: Java 21, Spring Boot 3.5.6, chạy mặc định tại `http://localhost:8081`.
- Frontend: React + Vite, chạy mặc định tại `http://localhost:5173`.
- Cơ sở dữ liệu: PostgreSQL, mặc định cổng `5432`, database `invoice_db`.
- Lưu trữ file: MinIO, API cổng `9000`, giao diện quản trị cổng `9001`.
- AI: Ollama, mặc định tại `http://localhost:11434`, model `qwen3-vl:8b`.
- OCR: Tess4J với dữ liệu tiếng Việt và tiếng Anh trong `backendqt/backendqt/tessdata`.

## 2. Phần mềm cần cài

Trên máy chạy project, cài các phần mềm sau:

1. Git.
2. JDK 21, sau đó kiểm tra:

   ```powershell
   java -version
   ```

3. Node.js phù hợp với Vite 8 (khuyến nghị Node.js 22 LTS), sau đó kiểm tra:

   ```powershell
   node --version
   npm --version
   ```

4. PostgreSQL.
5. MinIO Server cho Windows (`minio.exe`).
6. Ollama nếu chạy AI trên chính máy này. Nếu dùng Ollama trên máy khác thì máy chạy project không bắt buộc phải cài Ollama.

Project đã có Maven Wrapper (`mvnw.cmd`), vì vậy không cần cài Maven riêng.

## 3. Chuẩn bị PostgreSQL

Khởi động PostgreSQL rồi tạo database, ví dụ bằng `psql`:

```sql
CREATE DATABASE invoice_db;
```

Ghi nhớ các thông tin sau để dùng ở bước cấu hình:

- Địa chỉ máy PostgreSQL, ví dụ `localhost`.
- Cổng, thường là `5432`.
- Tên database, mặc định của project là `invoice_db`.
- Username và password PostgreSQL.

Backend đang dùng `spring.jpa.hibernate.ddl-auto=update`, vì vậy các bảng sẽ được tạo/cập nhật khi backend khởi động. Các file SQL bổ sung nằm trong `backendqt/backendqt/src/main/resources/db/migration`; project hiện không cấu hình Flyway để tự chạy các file này.

## 4. Chuẩn bị MinIO

Tạo một thư mục để chứa dữ liệu, ví dụ:

```powershell
New-Item -ItemType Directory -Force C:\minio-data
```

Đặt `minio.exe` tại một vị trí cố định, ví dụ:

```text
C:\minio\minio.exe
```

Script khởi động sẽ tự chạy MinIO với thư mục và cổng được khai báo trong `config/config.local.json`.

## 5. Tạo file cấu hình local

Tại thư mục gốc của project, chạy:

```powershell
Copy-Item config\config.example.json config\config.local.json
Copy-Item .env.example .env
```

Hai file này đã được `.gitignore` bỏ qua, không commit chúng vì chúng chứa cấu hình riêng và thông tin bí mật.

### 5.1. Cấu hình `config/config.local.json`

Ví dụ cấu hình khi mọi dịch vụ chạy trên cùng một máy:

```json
{
  "paths": {
    "backend": "backendqt/backendqt",
    "frontend": "quan tri",
    "minioExecutable": "C:/minio/minio.exe",
    "minioData": "C:/minio-data"
  },
  "backend": { "host": "localhost", "port": 8081 },
  "frontend": { "host": "localhost", "port": 5173 },
  "database": {
    "host": "localhost",
    "port": 5432,
    "database": "invoice_db",
    "username": "postgres"
  },
  "minio": {
    "host": "localhost",
    "apiPort": 9000,
    "consolePort": 9001,
    "bucket": "invoices"
  },
  "ai": {
    "provider": "ollama",
    "ollama": {
      "host": "localhost",
      "port": 11434,
      "model": "qwen3-vl:8b",
      "timeoutSeconds": 120,
      "numContext": 8192,
      "numPredict": 3072,
      "reservedOutputTokens": 3072
    },
    "cloud": {
      "baseUrl": "",
      "model": "",
      "chatCompletionsPath": "/chat/completions",
      "timeoutSeconds": 120
    }
  }
}
```

Lưu ý: trong JSON trên Windows nên dùng dấu `/` trong đường dẫn như `C:/minio/minio.exe`, hoặc dùng `\\` nếu viết dấu gạch chéo ngược.

### 5.2. Cấu hình `.env`

Thay toàn bộ giá trị mẫu bằng giá trị thực:

```dotenv
DB_PASSWORD=mat_khau_postgresql
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=mot_mat_khau_minio_manh
JWT_SECRET=mot_chuoi_bi_mat_ngau_nhien_dai_toi_thieu_32_byte

DEMO_ADMIN_ENABLED=false
DEMO_ADMIN_PASSWORD=mat_khau_demo_neu_bat_tai_khoan_demo

AI_CLOUD_API_KEY=
TESSDATA_PATH=./tessdata
OCR_LANGUAGE=vie+eng
OCR_ENABLED=true
UPLOAD_MAX_FILE_SIZE=10MB
UPLOAD_MAX_REQUEST_SIZE=12MB
VITE_API_BASE_URL=http://localhost:8081
```

Có thể tạo JWT secret ngẫu nhiên bằng PowerShell:

```powershell
$bytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Sao chép kết quả vào `JWT_SECRET`. Không thêm dấu nháy và không để khoảng trắng thừa quanh dấu `=` trong `.env`.

## 6. Cài dependency frontend

Chỉ cần thực hiện lần đầu hoặc sau khi `package.json` thay đổi:

```powershell
Set-Location "quan tri"
npm install
Set-Location ..
```

## 7. Chuẩn bị Ollama chạy trên cùng máy

Mở PowerShell và tải model:

```powershell
ollama pull qwen3-vl:8b
ollama list
```

Tên model trong kết quả `ollama list` phải giống chính xác với `ai.ollama.model` trong `config/config.local.json`.

Không cần tự chạy `ollama serve` khi dùng script: nếu cổng Ollama chưa hoạt động, `start.ps1` sẽ tự khởi động Ollama.

## 8. Chạy toàn bộ project

Từ thư mục gốc của project:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\start.ps1 all
```

Script sẽ lần lượt:

1. Chạy/kiểm tra MinIO.
2. Chạy/kiểm tra Ollama và model.
3. Kiểm tra PostgreSQL rồi chạy backend.
4. Chạy frontend.

Các địa chỉ mặc định:

- Frontend: `http://localhost:5173`
- Swagger backend: `http://localhost:8081/swagger-ui.html`
- MinIO Console: `http://localhost:9001`
- Ollama API: `http://localhost:11434/api/tags`

Backend và frontend được mở trong hai cửa sổ PowerShell riêng. Để dừng môi trường, nhấn `Ctrl+C` trong các cửa sổ đó và dừng tiến trình MinIO/Ollama nếu không còn sử dụng.

## 9. Chạy từng dịch vụ

```powershell
.\scripts\start.ps1 minio
.\scripts\start.ps1 ollama
.\scripts\start.ps1 backend
.\scripts\start.ps1 frontend
```

Đổi cổng tạm thời cho dịch vụ đang chạy riêng:

```powershell
.\scripts\start.ps1 backend -Port 8082
.\scripts\start.ps1 frontend -Port 5174
.\scripts\start.ps1 ollama -Port 11435 -Model "qwen3-vl:8b"
```

Nếu AI đã được chạy bên ngoài và chỉ muốn bỏ qua bước kiểm tra/khởi động AI khi chạy toàn bộ:

```powershell
.\scripts\start.ps1 all -SkipAI
```

## 10. Dùng Ollama trên máy khác trong cùng mạng LAN

Giả sử:

- Máy A chạy Ollama, có IP LAN `192.168.1.50`.
- Máy B chạy project này.
- Hai máy nhìn thấy nhau trong cùng mạng LAN.

### 10.1. Trên máy A (máy chạy Ollama)

Tải model trước:

```powershell
ollama pull qwen3-vl:8b
```

Nếu Ollama đang chạy ở khay hệ thống, thoát Ollama trước để tránh trùng cổng. Sau đó mở PowerShell và chạy:

```powershell
$env:OLLAMA_HOST = "0.0.0.0:11434"
ollama serve
```

Giữ terminal này mở trong lúc máy B sử dụng Ollama. Lệnh trên chỉ đặt biến môi trường cho terminal hiện tại.

Mở cổng trên Windows Firewall bằng PowerShell chạy với quyền Administrator:

```powershell
New-NetFirewallRule -DisplayName "Ollama LAN 11434" -Direction Inbound -Protocol TCP -LocalPort 11434 -Action Allow -Profile Private
```

Nên chỉ dùng rule này ở mạng `Private`. Không mở cổng Ollama trực tiếp ra Internet vì Ollama API mặc định không có xác thực.

Kiểm tra ngay trên máy A:

```powershell
Invoke-RestMethod http://localhost:11434/api/tags
```

Nếu máy A chạy Linux/macOS, có thể chạy từ terminal:

```bash
OLLAMA_HOST=0.0.0.0:11434 ollama serve
```

Đồng thời cho phép TCP `11434` trong firewall của hệ điều hành đó.

### 10.2. Trên máy B (máy chạy project)

Kiểm tra kết nối đến máy A:

```powershell
Test-NetConnection 192.168.1.50 -Port 11434
Invoke-RestMethod http://192.168.1.50:11434/api/tags
```

Sửa phần `ai.ollama` trong `config/config.local.json`:

```json
"ai": {
  "provider": "ollama",
  "ollama": {
    "host": "192.168.1.50",
    "port": 11434,
    "model": "qwen3-vl:8b",
    "timeoutSeconds": 120,
    "numContext": 8192,
    "numPredict": 3072,
    "reservedOutputTokens": 3072
  },
  "cloud": {
    "baseUrl": "",
    "model": "",
    "chatCompletionsPath": "/chat/completions",
    "timeoutSeconds": 120
  }
}
```

Sau đó chạy bình thường:

```powershell
.\scripts\start.ps1 all
```

Khi `ai.ollama.host` là IP máy A và cổng `11434` truy cập được, script sẽ nhận biết Ollama đã chạy từ xa, kiểm tra `/api/tags`, rồi truyền `OLLAMA_BASE_URL=http://192.168.1.50:11434` cho backend. Không cần sửa `application.yml`.

Nếu IP máy A thường xuyên thay đổi, nên cấu hình DHCP reservation trên router hoặc dùng hostname có thể phân giải được trong mạng LAN.

### 10.3. Lỗi thường gặp với Ollama từ xa

- `TcpTestSucceeded: False`: kiểm tra IP, cùng mạng LAN, tiến trình `ollama serve` và firewall máy A.
- Kết nối được nhưng không thấy model: chạy `ollama pull qwen3-vl:8b` trên máy A, không phải máy B.
- Script báo model không tồn tại: tên/tag model trong JSON không khớp chính xác với kết quả `/api/tags` hoặc `ollama list`.
- Phản hồi AI bị timeout: tăng `timeoutSeconds`, đồng thời kiểm tra tài nguyên GPU/RAM và tốc độ mạng máy A.
- JSON đầu ra bị cắt: tăng `numPredict` và `reservedOutputTokens`, nhưng luôn giữ `reservedOutputTokens <= numPredict` và `reservedOutputTokens < numContext`.

## 11. Kiểm tra sau khi khởi động

```powershell
Invoke-WebRequest http://localhost:8081/swagger-ui.html -UseBasicParsing
Invoke-RestMethod http://localhost:11434/api/tags
Test-NetConnection localhost -Port 9000
Test-NetConnection localhost -Port 5173
```

Nếu dùng Ollama từ xa, thay `localhost` trong lệnh kiểm tra Ollama bằng IP máy chạy Ollama.

## 12. Xử lý lỗi phổ biến

### PowerShell chặn chạy script

Chỉ mở quyền cho terminal hiện tại:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

### `node_modules not found`

```powershell
Set-Location "quan tri"
npm install
```

### PostgreSQL không kết nối được

```powershell
Test-NetConnection localhost -Port 5432
```

Kiểm tra dịch vụ PostgreSQL, `database.host`, `database.port`, `database.username` trong JSON và `DB_PASSWORD` trong `.env`.

### MinIO không khởi động

Kiểm tra `paths.minioExecutable`, `paths.minioData`, hai cổng `9000`/`9001`, cùng `MINIO_ACCESS_KEY` và `MINIO_SECRET_KEY`.

### OCR không tìm thấy dữ liệu ngôn ngữ

Đảm bảo hai file sau tồn tại:

```text
backendqt/backendqt/tessdata/vie.traineddata
backendqt/backendqt/tessdata/eng.traineddata
```

Với script hiện tại, backend được chạy tại thư mục `backendqt/backendqt`, nên `TESSDATA_PATH=./tessdata` là đúng.

### Một cổng đã được sử dụng

```powershell
Get-NetTCPConnection -LocalPort 8081,5173,9000,9001,11434 -ErrorAction SilentlyContinue
```

Đóng tiến trình cũ hoặc đổi cổng tương ứng trong `config/config.local.json`.

## 13. Lệnh kiểm thử và build

Backend:

```powershell
Set-Location backendqt\backendqt
.\mvnw.cmd test
```

Frontend:

```powershell
Set-Location "quan tri"
npm test
npm run build
```


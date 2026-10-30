# Hướng dẫn chạy backend và giao diện mobile

Áp dụng cho dự án `DOAN_KLCN` trên Windows/PowerShell. Backend là Spring Boot trong `backendqt/backendqt`; ứng dụng mobile là Flutter trong `invoice_1`.

## 1. Phần mềm cần có

| Thành phần | Yêu cầu của source hiện tại |
| --- | --- |
| Backend | JDK 21; dùng Maven Wrapper có sẵn |
| Mobile | Flutter SDK có Dart tương thích `^3.12.0` theo `invoice_1/pubspec.yaml` |
| Android | Android SDK và Android Emulator hoặc điện thoại bật USB debugging |
| Database | PostgreSQL và database đã có schema tương thích |
| Lưu trữ | MinIO Server |
| OCR/AI | Dữ liệu Tesseract `vie+eng`; Ollama hoặc AI cloud đã cấu hình |

Kiểm tra công cụ:

```powershell
java -version
flutter --version
flutter doctor -v
flutter devices
```

Nếu Android toolchain yêu cầu chấp nhận license:

```powershell
flutter doctor --android-licenses
```

Không cần chạy React/Vite để đăng nhập và sử dụng các màn hình mobile gọi API trực tiếp. Chỉ cần chạy web nếu sử dụng chức năng mở hồ sơ trên web.

## 2. Chuẩn bị cấu hình backend

Các lệnh trong mục này chạy từ thư mục gốc:

```powershell
Set-Location 'D:\maudoan_2\DOAN_KLCN'
```

Nếu source nằm ở nơi khác, thay đường dẫn trên bằng thư mục của bạn.

### 2.1. Tạo file cấu hình nếu chưa có

```powershell
if (-not (Test-Path -LiteralPath 'config/config.local.json')) {
    Copy-Item -LiteralPath 'config/config.example.json' -Destination 'config/config.local.json'
}
if (-not (Test-Path -LiteralPath '.env')) {
    Copy-Item -LiteralPath '.env.example' -Destination '.env'
}
```

Giữ nguyên cấu hình đang sử dụng nếu hai file đã tồn tại. Không đưa mật khẩu thật vào tài liệu hoặc commit các file cấu hình riêng.

### 2.2. Sửa `config/config.local.json`

Giữ các phần trong file mẫu và sửa các giá trị sau theo máy của bạn:

| Thuộc tính | Ví dụ |
| --- | --- |
| `paths.backend` | `backendqt/backendqt` |
| `paths.frontend` | `quan tri` |
| `paths.minioExecutable` | `C:/minio/minio.exe` |
| `paths.minioData` | `C:/minio-data` |
| `backend.host` / `backend.port` | `localhost` / `8081` |
| `database.host` / `database.port` | `localhost` / `5432` |
| `database.database` | `invoice_db` hoặc tên database hiện có |
| `database.username` | Tài khoản PostgreSQL của bạn |
| `minio.host` / `minio.apiPort` | `localhost` / `9000` |
| `minio.consolePort` / `minio.bucket` | `9001` / `invoices` |
| `ai.provider` | `ollama` khi dùng Ollama |
| `ai.ollama.host` / `ai.ollama.port` | `localhost` / `11434` |
| `ai.ollama.model` | Model đã cài, ví dụ `qwen3-vl:8b` |

Đường dẫn MinIO phải trỏ tới file thực và thư mục dữ liệu có sẵn. Dùng `/` hoặc `\\` trong chuỗi JSON. Các mục frontend vẫn cần có vì script hiện tại kiểm tra cấu trúc cấu hình chung, kể cả khi chỉ chạy backend.

### 2.3. Sửa `.env`

Điền giá trị thật thay các placeholder:

```dotenv
DB_PASSWORD=YOUR_DATABASE_PASSWORD
MINIO_ACCESS_KEY=YOUR_MINIO_ACCESS_KEY
MINIO_SECRET_KEY=YOUR_MINIO_SECRET_KEY
JWT_SECRET=YOUR_RANDOM_SECRET_AT_LEAST_32_UTF8_BYTES
DEMO_ADMIN_ENABLED=false
TESSDATA_PATH=./tessdata
OCR_LANGUAGE=vie+eng
OCR_ENABLED=true
UPLOAD_MAX_FILE_SIZE=10MB
UPLOAD_MAX_REQUEST_SIZE=12MB
```

Không giữ nguyên chuỗi `YOUR_...`: script sẽ từ chối khởi động. Nếu MinIO đang chạy sẵn, access key/secret phải khớp với tài khoản MinIO đang dùng.

Có thể tạo JWT secret bằng PowerShell:

```powershell
$jwtBytes = New-Object byte[] 48
$jwtGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtGenerator.GetBytes($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
$jwtGenerator.Dispose()
```

Chép kết quả vào `JWT_SECRET` trong `.env`. Giữ secret ổn định giữa các lần chạy nếu muốn token đã phát hành tiếp tục hợp lệ.

Nếu cần tạo tài khoản từ giao diện quản trị, thêm `INTERNAL_COMPANY_ID` vào `.env` với ID **công ty đã tồn tại** trong database. Không dùng ID giả hoặc tự tạo dữ liệu mẫu để vượt qua kiểm tra.

### 2.4. Database, OCR và tài khoản

- Khởi động PostgreSQL và dùng database đã được chuẩn bị cho dự án.
- `ddl-auto: validate` chỉ kiểm tra schema; không tự tạo bảng. Chỉ tạo database rỗng là chưa đủ.
- Các SQL trong `src/main/resources/db/migration` là những thay đổi bổ sung; không phải bộ khởi tạo đầy đủ và không được cấu hình tự chạy bằng Flyway. Không chạy lại toàn bộ thư mục một cách tùy ý.
- OCR tiếng Việt cần `vie.traineddata` và `eng.traineddata` trong `backendqt/backendqt/tessdata` hoặc thư mục khai báo bằng `TESSDATA_PATH`.
- Đăng nhập bằng tài khoản hợp lệ đang có trong database. Backend hiện không tự seed tài khoản demo; đăng ký công khai đã bị tắt. Không có tài khoản thì nhờ quản trị viên tạo tài khoản Kế toán/Nhân viên.

Chi tiết chuẩn bị database và cấu hình AI từ xa: [Hướng dẫn cấu hình chung](HUONG_DAN_CAU_HINH_VA_CHAY.md).

## 3. Khởi động backend

Từ thư mục gốc dự án, chạy lần lượt:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start.ps1 minio
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start.ps1 ollama
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start.ps1 backend
```

Trước đó, nếu model chưa có, cài model phù hợp với cấu hình:

```powershell
ollama pull qwen3-vl:8b
```

Nếu dùng AI cloud đã cấu hình, bỏ lệnh khởi động Ollama; điền `ai.cloud` trong JSON và `AI_CLOUD_API_KEY` trong `.env`. Nếu dùng Ollama từ xa, dịch vụ trên máy từ xa phải được khởi động trước.

Lệnh `start.ps1 backend` nạp `.env` và JSON, kiểm tra PostgreSQL, rồi gọi Maven Wrapper trong thư mục backend. Lệnh này không tự chạy MinIO/Ollama. Không dùng `start.ps1 all` nếu chỉ cần backend và mobile vì `all` còn khởi động giao diện web.

Kiểm tra backend tại:

```text
http://localhost:8081/swagger-ui.html
```

Hoặc kiểm tra bằng PowerShell:

```powershell
Test-NetConnection localhost -Port 8081
Invoke-WebRequest -Uri 'http://localhost:8081/swagger-ui.html' -UseBasicParsing
```

Các API nghiệp vụ cần Bearer token. Mở một API được bảo vệ mà thấy `401` không đồng nghĩa backend chưa chạy.

Nếu chạy Maven trực tiếp, chỉ dùng lệnh sau **sau khi đã đặt đủ biến môi trường trong chính terminal đó**:

```powershell
Set-Location 'D:\maudoan_2\DOAN_KLCN\backendqt\backendqt'
.\mvnw.cmd spring-boot:run
```

Spring Boot hiện không tự đọc file `.env` ở thư mục gốc. Biến môi trường được script nạp trong tiến trình riêng cũng không tự truyền ngược sang terminal khác; dùng script là cách chạy chính trong hướng dẫn này.

## 4. Chuẩn bị ứng dụng Flutter

Mở terminal khác:

```powershell
Set-Location 'D:\maudoan_2\DOAN_KLCN\invoice_1'
flutter pub get
flutter devices
```

Backend tiếp tục chạy trong lúc dùng mobile.

Địa chỉ được khai báo tại `lib/app/constants/api_constants.dart`, đọc bằng `String.fromEnvironment`. Source hiện mặc định là `http://localhost:8081/api/v1`; khi chạy Android hãy truyền địa chỉ phù hợp bằng `--dart-define`.

## 5. Chạy trên Android Emulator

Khởi động máy ảo bằng Android Studio Device Manager, hoặc:

```powershell
flutter emulators
flutter emulators --launch YOUR_EMULATOR_ID
flutter devices
```

Thay `YOUR_EMULATOR_ID` bằng ID trong `flutter emulators`. ID dùng cho `flutter run -d` lấy từ `flutter devices`, có thể khác ID dùng để khởi động máy ảo.

Ví dụ thiết bị đang chạy có ID `emulator-5554`:

```powershell
flutter run -d emulator-5554 --dart-define=API_BASE_URL=http://10.0.2.2:8081/api/v1
```

Với Android Emulator chuẩn, `10.0.2.2` trỏ về máy tính chạy emulator. `localhost` trong emulator trỏ về chính emulator.

## 6. Chạy trên điện thoại Android thật

1. Bật Developer options và USB debugging trên điện thoại.
2. Cắm USB và chấp nhận thông báo cấp quyền debug.
3. Kết nối điện thoại và máy tính vào cùng mạng Wi-Fi/LAN nếu dùng địa chỉ LAN.
4. Xem IPv4 của máy tính bằng `ipconfig`, rồi kiểm tra điện thoại bằng `flutter devices`.

Ví dụ máy tính có IP `192.168.1.20` và thiết bị có ID `YOUR_DEVICE_ID`:

```powershell
flutter run -d YOUR_DEVICE_ID --dart-define=API_BASE_URL=http://192.168.1.20:8081/api/v1
```

Thay cả IP và device ID bằng giá trị thực. Trên trình duyệt điện thoại, thử mở:

```text
http://192.168.1.20:8081/swagger-ui.html
```

Nếu không mở được, kiểm tra cùng mạng, IP, Windows Firewall và ứng dụng backend đang lắng nghe trên địa chỉ có thể truy cập từ LAN. Không dùng `localhost` làm địa chỉ API trên điện thoại.

**Cách khác qua USB**, khi `adb` có trong PATH:

```powershell
adb -s YOUR_DEVICE_ID reverse tcp:8081 tcp:8081
flutter run -d YOUR_DEVICE_ID --dart-define=API_BASE_URL=http://127.0.0.1:8081/api/v1
```

Kết nối USB và ánh xạ reverse phải còn hoạt động. Đặt lại ánh xạ khi thiết bị kết nối lại nếu cần.

## 7. Địa chỉ kết nối cần nhớ

| Nơi chạy mobile | `API_BASE_URL` |
| --- | --- |
| Android Emulator chuẩn | `http://10.0.2.2:8081/api/v1` |
| Điện thoại cùng LAN | `http://IP_MAY_TINH:8081/api/v1` |
| Điện thoại qua USB + adb reverse | `http://127.0.0.1:8081/api/v1` |

Phải có `/api/v1` ở cuối và khớp cổng backend thực tế. `API_BASE_URL` là URL backend; không điền cổng Vite `5173` hoặc MinIO `9000` vào đây.

Đổi `--dart-define` cần dừng và chạy lại ứng dụng; hot reload không thay cấu hình này. Biến `.env` của backend hoặc `VITE_API_BASE_URL` không tự cấu hình Flutter.

Nếu dùng chức năng mở web, chạy web riêng rồi bổ sung, ví dụ cho emulator:

```powershell
flutter run -d emulator-5554 --dart-define=API_BASE_URL=http://10.0.2.2:8081/api/v1 --dart-define=WEB_APP_URL=http://10.0.2.2:5173
```

Với điện thoại qua LAN, `WEB_APP_URL` phải dùng IP máy tính và web phải được cấu hình lắng nghe trên LAN. Cấu hình backend vẫn giữ nguyên.

## 8. Kiểm tra và xử lý lỗi

| Hiện tượng | Cách kiểm tra |
| --- | --- |
| Không thấy thiết bị | `flutter doctor -v`, `flutter devices`; bật emulator hoặc cấp quyền USB debugging |
| Dart/Flutter không tương thích | Kiểm tra Dart của Flutter đáp ứng `^3.12.0` trong pubspec; dùng đúng Flutter SDK trong terminal/IDE |
| Không tìm thấy Java | Backend cần JDK 21; kiểm tra `java -version` và `JAVA_HOME` |
| Backend báo schema validation failed | Database/schema chưa khớp source; chuẩn bị schema phù hợp, không đổi sang `create` để bỏ qua lỗi |
| Connection refused / timeout | Kiểm tra backend, IP/cổng và đường mạng tương ứng emulator/LAN/USB |
| Đăng nhập báo 401 | Kiểm tra tài khoản/mật khẩu; token cũ có thể hết hạn hoặc không còn hợp lệ, đăng nhập lại |
| API trả 403 | Kiểm tra role/permission và phạm vi dữ liệu của tài khoản |
| Không tạo được tài khoản | Kiểm tra quyền ADMIN, role hợp lệ và `INTERNAL_COMPANY_ID` trỏ tới công ty tồn tại |
| Upload/OCR/AI lỗi | Kiểm tra MinIO, dữ liệu `vie+eng`, endpoint/model AI và log backend |
| Xử lý AI lâu | Có thể tăng `API_RECEIVE_TIMEOUT_MS`; tăng timeout không sửa được lỗi kết nối hoặc model |

Ví dụ tăng thời gian chờ mobile lên 240 giây:

```powershell
flutter run -d emulator-5554 --dart-define=API_BASE_URL=http://10.0.2.2:8081/api/v1 --dart-define=API_RECEIVE_TIMEOUT_MS=240000
```

Manifest Android **debug** hiện có quyền INTERNET và cho HTTP cleartext để phát triển. Hướng dẫn này dùng bản debug; cấu hình mạng và ký ứng dụng release cần chuẩn bị riêng.

## 9. Build APK debug và dừng chương trình

Tại `invoice_1`, ví dụ build bản cài cho điện thoại dùng LAN:

```powershell
flutter build apk --debug --dart-define=API_BASE_URL=http://192.168.1.20:8081/api/v1
```

APK tạo tại:

```text
invoice_1/build/app/outputs/flutter-apk/app-debug.apk
```

IP trên được đóng vào bản build; build lại nếu đổi máy/cổng/IP. Điện thoại cài APK vẫn phải kết nối được backend.

Nhấn `q` trong terminal `flutter run` để kết thúc phiên chạy. Nhấn `Ctrl+C` trong cửa sổ backend để dừng Spring Boot; MinIO/Ollama là các tiến trình riêng.

## 10. Lệnh chạy nhanh sau khi đã cấu hình

**Terminal backend — từ thư mục gốc:**

```powershell
Set-Location 'D:\maudoan_2\DOAN_KLCN'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start.ps1 minio
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start.ps1 ollama
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start.ps1 backend
```

**Terminal mobile — sau khi bật emulator:**

```powershell
Set-Location 'D:\maudoan_2\DOAN_KLCN\invoice_1'
flutter pub get
flutter devices
flutter run -d emulator-5554 --dart-define=API_BASE_URL=http://10.0.2.2:8081/api/v1
```

Các lệnh này được đối chiếu với source và script của dự án; việc viết tài liệu không khởi động dịch vụ, tạo tài khoản hoặc thay đổi database.

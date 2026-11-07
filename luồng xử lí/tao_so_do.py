"""Rebuild editable, uncompressed draw.io diagrams from the audited flow specification."""
from pathlib import Path
import xml.etree.ElementTree as ET
import hashlib
import json
import re

OUT=Path(__file__).resolve().parent
ROOT=OUT.parent
JAVA='backendqt/backendqt/src/main/java/com/example/invoice/'
flows=[]
def flow(title,sources,nodes,edges,note=''):
    parsed=[]
    for line in nodes.strip().splitlines():
        if re.match(r'^\w+\|\d+\|\d+\|',line): parsed.append(line.split('|',4))
        else: parsed[-1][4]+='\n'+line
    flows.append(dict(title=title,sources=sources,nodes=parsed,
                      edges=[line.split('|') for line in edges.strip().splitlines()],note=note))

flow('01 · Tổng quan hệ thống', ['quan tri/src/App.tsx','invoice_1/lib/app/routes.dart',JAVA+'controller/DocumentController.java',JAVA+'service/BenchmarkAdapter.java','benchmark_runner/main.py'],'''
web|0|0|start|Web React / Vite\nAdmin · Accountant · Employee/User
mobile|1|0|start|Mobile Flutter\nĐăng nhập · chứng từ · workspace
api|0|1|process|Spring Boot REST API\nJWT + quyền + phạm vi công ty/chủ sở hữu
files|2|1|data|MinIO\nFile gốc · phiên bản · ảnh xử lý
db|0|2|data|JPA repositories / database\nChứng từ · hóa đơn · hàng hóa · lịch sử
ocr|1|2|process|PDFBox / Adaptive OCR\nTess4J + OpenCV
ai|1|3|process|AiProcessingService\nProvider theo cấu hình (Ollama / external)
review|0|3|process|Kiểm tra · đối soát · duyệt\nThu/chi xác nhận riêng
bench|2|3|process|Python benchmark → /api/benchmark\nFile-only adapter, không ghi DB nghiệp vụ
gt|2|4|data|results / ground_truth / reports\nGT · metric · Excel · dashboard cục bộ
''','''web|api|REST
mobile|api|REST
api|files|đọc / ghi
api|db|nghiệp vụ
api|ocr|xử lý
ocr|ai|text hoặc ảnh
ai|db|qua persistence nghiệp vụ
db|review|dữ liệu hiện tại
bench|ocr|dùng helper chung
bench|gt|evidence và báo cáo
''','Hai ranh giới: pipeline nghiệp vụ có persistence; benchmark gọi helper chung nhưng không ghi DB/MinIO nghiệp vụ.')

flow('02 · Đăng nhập và phân quyền',[JAVA+'service/AuthService.java',JAVA+'config/SecurityConfig.java',JAVA+'security/JwtAuthenticationFilter.java',JAVA+'service/DocumentService.java','quan tri/src/App.tsx','invoice_1/lib/app/routes.dart'],'''
start|0|0|start|Người dùng nhập username / password
auth|0|1|process|AuthService.login\nKhóa bản ghi user · AuthenticationManager
valid|0|2|decision|Thông tin đăng nhập hợp lệ?
bad|1|2|error|Từ chối đăng nhập
token|0|3|data|JWT Bearer + thông tin user\nClient lưu theo cơ chế của từng ứng dụng
request|0|4|process|Request kế tiếp → JwtAuthenticationFilter\nKiểm tra token và user
permission|0|5|decision|Đủ vai trò / authority / scope?
deny|1|5|error|401 / 403 hoặc không tìm thấy theo scope
service|0|6|end|Thực hiện chức năng được phép
logout|2|3|process|Logout → revokeTokens của user\nĐăng ký công khai đang bị vô hiệu hóa
''','''start|auth|
auth|valid|
valid|bad|Không
valid|token|Có
token|request|
request|permission|
permission|deny|Không
permission|service|Có
token|logout|đăng xuất
''','Guard của client điều hướng giao diện; backend tiếp tục kiểm quyền và phạm vi dữ liệu, không chỉ tin client.')

flow('03 · Upload và phiên bản file',[JAVA+'controller/DocumentController.java',JAVA+'service/DocumentUploadProcessingService.java',JAVA+'service/DocumentService.java',JAVA+'service/FileValidationService.java','quan tri/src/services/documentService.ts','invoice_1/lib/features/documents/data/document_repository.dart'],'''
start|0|0|start|Web / mobile chọn hoặc chụp file
check|0|1|process|POST /documents/upload\nKiểm quyền · validateUpload
valid|0|2|decision|File và công ty/người dùng hợp lệ?
error|1|2|error|Upload lỗi → không bắt đầu OCR/AI
store|0|3|data|Tên file an toàn → object key theo công ty\nLưu MinIO + Document + phiên bản
mode|0|4|decision|process=true?
auto|0|5|process|uploadAndProcess → processUploaded
wait|1|5|process|process=false → trả ID trước\nClient gọi /process-upload, theo dõi /progress
pipeline|0|6|process|DocumentAiProcessingService.process
result|0|7|end|Trả trạng thái hiện tại\nAI lỗi vẫn giữ chứng từ đã upload
version|2|3|process|POST /{id}/versions\nlockForEdit → validate → lưu phiên bản mới
''','''start|check|
check|valid|
valid|error|Không
valid|store|Có
store|mode|
mode|auto|Có (mặc định API)
mode|wait|Không
auto|pipeline|
wait|pipeline|
pipeline|result|
start|version|thay file của chứng từ cũ
''','Upload và lưu chứng từ commit trước công việc AI từ xa. Không vẽ upload là một transaction kéo dài xuyên suốt AI.')

flow('04 · Pipeline nghiệp vụ',[JAVA+'service/DocumentAiProcessingService.java',JAVA+'service/PipelineExecutionService.java',JAVA+'service/DocumentAiResultPersistenceService.java'],'''
start|0|0|start|process / reprocess / retry
claim|0|1|process|load + kiểm file → executions.start\nKhóa Document · kiểm trạng thái · tạo job/attempt
prepare|0|2|process|Nạp loại chứng từ và danh mục active\nĐọc file MinIO, kiểm rỗng/kích thước
type|0|3|decision|PDF hay ảnh?
pdf|0|4|process|PDF: phân tích từng trang\nLớp chữ hoặc OCR + AI
image|1|4|process|Ảnh: Adaptive OCR\nChọn nhánh AI text / vision
validate|0|5|process|Gộp kết quả · xác định vai trò công ty\nValidator: loại, trường, tiền, cảnh báo
persist|0|6|data|persist kết quả + OCR\nFieldVerification → complete job/attempt
done|0|7|end|PROCESSED hoặc NEED_REVIEW\nKết thúc progress registry
failure|2|5|error|Runtime failure → phân loại theo stage\nLưu FAILED hoặc NEED_REVIEW; trả lỗi
unknown|2|6|process|UnresolvedDocumentType riêng\npersistUnresolvedType → NEED_REVIEW
''','''start|claim|
claim|prepare|
prepare|type|
type|pdf|PDF
type|image|Ảnh
pdf|validate|
image|validate|
validate|persist|hợp lệ / có cảnh báo
persist|done|
prepare|failure|lỗi
pdf|failure|lỗi
image|failure|lỗi
validate|unknown|không xác định loại
unknown|done|
''','Mũi tên lỗi biểu diễn nhánh đại diện; catch runtime bao quanh toàn bộ phần chuẩn bị/xử lý/lưu trong processAttempt.')

flow('05 · PDF nhiều trang',[JAVA+'service/DocumentAiProcessingService.java',JAVA+'service/PdfImageConversionService.java'],'''
start|0|0|start|processPdf → analyzePdfPages
extract|0|1|process|PDFBox extractTextPages\nKiểm usability từng trang
all|0|2|decision|Mọi trang có lớp chữ dùng được?
native|0|3|process|Không render ảnh\nMỗi trang → analyzeDigitalPdfText
render|1|3|process|Render PDF → danh sách ảnh trang
page|1|4|decision|Trang hiện tại có lớp chữ dùng được?
text|0|5|process|Dùng text layer → AI TEXT\nOCR metadata: pdf-text-layer
scan|1|5|process|Adaptive OCR → dựng layout\nLưu OCR tích lũy → AI text/vision
loop|1|6|decision|Còn trang?
aggregate|0|7|process|aggregateOcr + aggregatePageResults\nGộp items bằng distinct; cảnh báo xung đột loại
done|0|8|end|Validator → persistence → field verification
''','''start|extract|
extract|all|
all|native|Có
all|render|Không
render|page|
page|text|Có
page|scan|Không
text|loop|
scan|loop|
loop|page|Có
loop|aggregate|Không
native|aggregate|đủ các trang
aggregate|done|
''','Luồng thực gọi là analyzePdfPages. analyzeVisionFallback và DocumentTextExtractionService không được tự ghép vào nhánh này nếu không có call site. Retail nhiều trang có cảnh báo kiểm tay và bỏ retailDetails khi gộp.')

flow('06 · OCR thích nghi',[JAVA+'service/AdaptiveOcrService.java',JAVA+'service/OpenCvImageProcessor.java',JAVA+'service/Tess4jOcrService.java',JAVA+'service/OcrLayoutReconstructor.java'],'''
start|0|0|start|Ảnh đầu vào / ảnh trang PDF
quality|0|1|process|ImageQualityAnalyzer\nĐộ nghiêng, kích thước và chất lượng ảnh
enabled|0|2|decision|Phân tích ảnh có sẵn và adaptive bật?
fallback|1|2|process|Tess4J trên ảnh gốc\nNORMAL; ghi cảnh báo khi OpenCV lỗi
profile|0|3|process|Lặp OcrProcessingProfile\nOpenCV xử lý → Tess4J OCR
score|0|4|process|Map box về ảnh gốc → đánh giá score\nConfidence, chữ/từ, layout, trường trọng yếu, số
good|0|5|decision|Đủ điểm tốt hoặc hết maxAttempts?
next|1|5|process|Thử profile tiếp\nLỗi profile giữ các attempt trước
select|0|6|process|Chọn attempt điểm cao nhất\nKhông đủ minimumQualityImprovement → baseline
layout|0|7|process|Dựng word / line / block\nChuẩn hóa văn bản và cấu trúc layout
done|0|8|end|OCR + ảnh được chọn + metadata chất lượng
''','''start|quality|
quality|enabled|
enabled|fallback|Không
enabled|profile|Có
profile|score|
score|good|
good|next|Chưa
next|profile|
good|select|Có
select|layout|
fallback|layout|
layout|done|
''','Score OCR là heuristic chọn chất lượng, không phải CER/WER và không phải xác suất giá trị trích xuất đúng.')

flow('07 · Chọn AI text hoặc vision',[JAVA+'service/DocumentAiProcessingService.java',JAVA+'ai/AiProcessingService.java',JAVA+'ai/OllamaAiProvider.java',JAVA+'ai/AiDocumentResultParser.java'],'''
start|0|0|start|OCR + ảnh đã chọn + ngữ cảnh công ty
context|0|1|process|structuredContext + normalizeOcrText\nPrompt: loại chứng từ + danh mục công ty
reliable|0|2|decision|OCR đủ tin cậy?\nconfidence + layout + score + dấu + bằng chứng
text|0|3|process|analyzeTextDocument\nProvider cấu hình → parse JSON
vision|1|3|process|analyzeDocument\nGửi ảnh cho provider → parse JSON
format|0|4|decision|Nhánh text trả lỗi structured output?
result|0|5|end|AiDocumentResult\nGhi mode TEXT / VISION / kết hợp
other|2|4|error|Lỗi kết nối / timeout / lỗi khác\nNém lỗi về pipeline, không tự lặp vô hạn
''','''start|context|
context|reliable|
reliable|text|Có
reliable|vision|Không
text|format|
format|vision|Có: một vision fallback
format|result|Không: thành công
text|other|lỗi không phải định dạng
vision|result|thành công
vision|other|thất bại
''','PDF digital đi trực tiếp TEXT theo sơ đồ 05. Ollama dùng /api/generate, stream=false, format=json; kiểm token budget và timeout. Không đồng nhất fallback định dạng với retry job.')

flow('08 · Chuẩn hóa và lưu AI',[JAVA+'ai/AiDocumentResultValidator.java',JAVA+'service/DocumentAiResultPersistenceService.java',JAVA+'service/ClassificationService.java'],'''
start|0|0|start|AiDocumentResult đã parse
validate|0|1|process|Validator kiểm allowedTypes / categoryCodes\nChuẩn hóa ngày, định danh, tiền, cảnh báo
unknown|1|1|error|Không xác định được loại\nLưu NEED_REVIEW riêng, không ép loại
lock|0|2|decision|Chứng từ đã APPROVED?
skip|1|2|end|Bỏ qua ghi đè kết quả đã duyệt
base|0|3|data|Lưu intelligence + OCR + classification\nNgưỡng confidence/review reasons
invoice|0|4|decision|Loại hóa đơn và có invoice extraction?
rows|0|5|data|Lưu invoice + item hợp lệ\nRetail details có điều kiện dòng đầy đủ
non|1|5|process|Loại không phải hóa đơn\nDọn dữ liệu AI hóa đơn cũ theo logic service
fields|0|6|data|Lưu extractedFields / extraFields\nTránh trùng core và giữ lịch sử sửa tay
done|0|7|end|PROCESSED / NEED_REVIEW\nAudit snapshot + processing log
''','''start|validate|
validate|unknown|unresolved type
validate|lock|
lock|skip|Có
lock|base|Không
base|invoice|
invoice|rows|Có
invoice|non|Không phải loại hóa đơn
rows|fields|
non|fields|
fields|done|
''','Trường hợp hóa đơn thiếu extraction vẫn theo guard trong persist; không suy ra mọi lần AI đều ghi đủ item. Chỉnh loại/danh mục thủ công là chức năng riêng ClassificationService.')

flow('09 · Job, lỗi và retry',[JAVA+'service/PipelineExecutionService.java',JAVA+'service/PipelineFailures.java',JAVA+'service/ProcessingProgressRegistry.java'],'''
start|0|0|start|Yêu cầu process / retry / reprocess
gate|0|1|decision|Document/job cho phép xử lý?
deny|1|1|error|Chặn đang PROCESSING/RUNNING\nChặn đã duyệt; đã trả lại cần RESUBMIT
retry|0|2|decision|Là retry lỗi cũ?
rules|1|3|decision|FAILED + retryable + có lịch sử\nvà attempt < MAX_ATTEMPTS=3?
no|2|3|error|RETRY_NOT_ALLOWED
new|0|3|data|Tạo job mới nếu không là job lỗi\nSTART attempt và commit transaction
remote|0|4|process|Thực hiện OCR / AI từ xa\nKhông giữ transaction DB xuyên yêu cầu remote
ok|0|5|decision|Xử lý thành công?
success|0|6|end|Attempt/job SUCCESS hoặc NEED_REVIEW
fail|1|6|error|Classify exception theo stage\nVALIDATION_FAILED/OCR_LOW_QUALITY → NEED_REVIEW\nLỗi khác → FAILED
history|0|7|data|Lưu log, thời gian, lỗi và lịch sử\nClient đọc /progress, /processing-history
''','''start|gate|
gate|deny|Không
gate|retry|Có
retry|new|Không
retry|rules|Có
rules|no|Không
rules|new|Có: dùng lại job lỗi
new|remote|
remote|ok|
ok|success|Có
ok|fail|Không
success|history|
fail|history|
''','Retry là thao tác theo điều kiện của API; sơ đồ không hàm ý có hàng đợi tự chạy lại nền. Progress registry là thông tin tiến độ, không thay lịch sử DB.')

flow('10 · Sửa dữ liệu và xác minh trường',[JAVA+'service/InvoiceService.java',JAVA+'service/ExtractedFieldCorrectionService.java',JAVA+'verification/FieldVerificationService.java',JAVA+'verification/FieldVerifier.java',JAVA+'service/OcrFieldLocator.java'],'''
start|0|0|start|Người kiểm tra mở chi tiết chứng từ
read|0|1|process|Đọc file + invoice + extracted fields\nHiển thị AI value, giá trị đã sửa, vùng OCR
edit|0|2|decision|Sửa dữ liệu hay xem bằng chứng?
save|0|3|data|lockForEdit → lưu invoice/items hoặc correction\nAudit trước/sau; chặn đã duyệt/đang xử lý
verify|1|3|process|FieldVerification report / recompute\nSau AI tự verifyAfterProcessing
inputs|1|4|process|Tập hợp giá trị hiện tại + nguồn AI/manual\nOCR evidence + rule + core profile
score|1|5|data|Lưu status / score / reasons / vị trí\nĐánh dấu stale khi dữ liệu thay đổi
done|0|6|end|Người dùng đọc kết quả và sửa/đối soát tiếp
''','''start|read|
read|edit|
edit|save|Sửa
edit|verify|Xem / tính lại
save|verify|yêu cầu recompute sau sửa
verify|inputs|
inputs|score|
score|done|
''','FieldVerification không sửa giá trị nghiệp vụ, không lấy điểm rule làm GT benchmark. Gọi recompute sau sửa là thao tác riêng; không vẽ thành auto-call của mọi endpoint sửa.')

flow('11 · Duyệt và gửi lại',[JAVA+'service/ReviewWorkflowService.java',JAVA+'controller/DocumentWorkflowController.java',JAVA+'service/ReconciliationState.java'],'''
upload|0|0|start|UPLOADED + review=PENDING
submit|0|1|process|SUBMIT bởi EMPLOYEE/USER\nGhi review event, chặn gửi lặp
process|0|2|process|Pipeline → PROCESSED / NEED_REVIEW
start|0|3|process|START_REVIEW bởi ACCOUNTANT/ADMIN\nPENDING → NEED_REVIEW
decision|0|4|decision|Quyết định của người duyệt?
guard|0|5|decision|APPROVE: nếu có invoice\nđã đối soát mới nhất, không unresolved?
approve|0|6|end|review=APPROVED\nstatus=COMPLETED · khóa chỉnh sửa
reject|1|5|process|REJECT / REQUEST_INFO\nBắt buộc lý do; NEED_REVIEW\nreview=REJECTED / CORRECTED
resubmit|1|6|process|EMPLOYEE/USER bổ sung rồi RESUBMIT\nreview=PENDING; status=UPLOADED
fix|2|5|process|Chạy lại đối soát / xử lý kiểm tra\nChưa đủ điều kiện thì chặn APPROVE
''','''upload|submit|
submit|process|
process|start|
start|decision|
decision|guard|Duyệt
decision|reject|Trả lại / bổ sung
guard|approve|Có / không có invoice
guard|fix|Không
fix|guard|thử duyệt sau khi xử lý
reject|resubmit|
resubmit|process|xử lý lại
''','SUBMIT ghi event, không tự gọi AI trong ReviewWorkflowService. APPROVE chỉ là quyết định duyệt, không tạo phiếu thu/chi hoặc xác nhận đã thanh toán.')

flow('12 · Đối soát hóa đơn',[JAVA+'service/ReconciliationService.java',JAVA+'service/ReconciliationState.java',JAVA+'ai/InvoiceReconciliationRules.java',JAVA+'ai/RetailInvoiceRules.java'],'''
start|0|0|start|Người dùng chạy đối soát chứng từ
load|0|1|process|lockForEdit → lấy invoice\nTạo batch và dấu vết dữ liệu đầu vào
type|0|2|decision|Retail hay VAT?
vat|0|3|process|InvoiceReconciliationRules\nKiểm tra số tiền header/items theo rule
retail|1|3|process|RetailInvoiceRules\nCăn cứ giảm trừ/thuế/retailDetails và stale
save|0|4|data|Lưu kết quả mới, không xóa lịch sử\nExpected / actual / difference / status
decision|0|5|decision|Người dùng quyết định kiểm tra?
stale|1|6|decision|Batch còn khớp dữ liệu hiện tại?
manual|1|7|data|Lưu quyết định + comment + reviewer\nChỉ khi guard cho phép
rerun|2|7|process|Dữ liệu đã đổi → chạy lại đối soát
done|0|8|end|ReconciliationState\nDùng làm điều kiện APPROVE
''','''start|load|
load|type|
type|vat|VAT
type|retail|Retail
vat|save|
retail|save|
save|decision|
decision|done|Chỉ xem
decision|stale|Có
stale|manual|Có
stale|rerun|Không
rerun|load|
manual|done|
''','Chỉ số số học và đối soát không kết luận tính hợp pháp của chứng từ. Đối soát tiền thực thu/chi là luồng khác ở sơ đồ 13.')

flow('13 · Thu chi và thanh toán',[JAVA+'service/FinancialTransactionService.java',JAVA+'service/PaymentLinkService.java',JAVA+'service/PaymentReconciliationService.java'],'''
start|0|0|start|Tạo phiếu thu/chi có xác nhận thực tế
auth|0|1|process|Kiểm quyền ACCOUNTING_CREATE + scope\nKhóa công ty; requestKey UUID + fingerprint
valid|0|2|decision|paymentConfirmed=true, số tiền >0\nngày/phương thức/loại hợp lệ?
error|1|2|error|Từ chối yêu cầu không hợp lệ
link|0|3|process|Resolve document/invoice theo công ty\nKiểm chiều Thu/Chi, danh mục, không vượt tổng
save|0|4|data|Lưu ACTIVE + CONFIRMED\nAudit PAYMENT_CREATE; chống gửi lặp theo requestKey
summary|0|5|process|PaymentLinkService.calculate\nTổng xác nhận, đã thanh toán, còn lại
reconcile|0|6|data|PaymentReconciliationService.run\nLưu lịch sử đối soát thanh toán
edit|1|4|process|Sửa phiếu: expectedUpdatedAt phải khớp\nPhiếu CONFIRMED và còn chỉnh sửa được
cancel|1|5|process|Hủy: lý do + phiên bản khớp\nCANCELLED + audit; không xóa vật lý
''','''start|auth|
auth|valid|
valid|error|Không
valid|link|Có
link|save|qua các guard
link|error|sai liên kết / vượt tổng
save|summary|
summary|reconcile|chạy đối soát
save|edit|sửa
edit|summary|
save|cancel|hủy
cancel|summary|
''','createFromApprovedDocument hiện trả Optional.empty(). Không suy ra giao dịch tiền từ duyệt hóa đơn, hình thức thanh toán hay gợi ý AI.')

flow('14 · Lưu trữ và báo cáo nghiệp vụ',[JAVA+'service/DocumentArchiveService.java',JAVA+'service/DocumentAuditService.java',JAVA+'service/ExcelExportService.java',JAVA+'service/DashboardService.java',JAVA+'service/DocumentService.java'],'''
start|0|0|start|Tra cứu danh sách / chi tiết / báo cáo
scope|0|1|process|JWT + quyền + phạm vi công ty/chủ sở hữu\nBộ lọc ngày, trạng thái, loại, tìm kiếm
choice|0|2|decision|Chức năng cần đọc?
file|0|3|process|Archive versions / storage\nMinIO preview hoặc download đúng version
audit|1|3|process|History / snapshot\nAudit trước-sau, correction, review
export|2|3|process|ExcelExportService\nChứng từ / hóa đơn / báo cáo tài chính
out|0|4|end|Trả nội dung theo quyền và version
financial|2|4|data|Báo cáo tài chính lọc\npaymentConfirmation=CONFIRMED
retention|1|5|error|Yêu cầu DELETE chứng từ\nDOCUMENT_RETENTION_REQUIRED: chặn xóa vật lý
''','''start|scope|
scope|choice|
choice|file|File
choice|audit|Lịch sử
choice|export|Excel
file|out|
audit|out|
export|financial|Tài chính
export|out|Hóa đơn / chứng từ
start|retention|Xóa
''','Snapshot lỗi không lấy dữ liệu hiện tại thay thế. Các phiên bản file và dữ liệu có lịch sử riêng; dashboard nghiệp vụ không đồng nhất với benchmark dashboard.')

flow('15 · Gán nhãn đánh giá trên ứng dụng',[JAVA+'evaluation/LiveEvaluationService.java',JAVA+'evaluation/LiveEvaluationStore.java',JAVA+'evaluation/LivePrediction.java',JAVA+'evaluation/LiveEvaluationScoring.java','quan tri/src/pages/accountant/EvaluationLabeling.tsx'],'''
start|0|0|start|Chọn thủ công hoặc random theo loại hóa đơn
snapshot|0|1|process|Đòi AI snapshot của fileVersion hiện tại\nLưu sample + snapshotId + version
blind|0|2|process|Workspace ẩn giá trị AI trước CONFIRMED\nNgười gán nhãn xem file gốc
draft|0|3|data|Lưu DRAFT theo revision\nGT fields/category/items/ocrText
confirm|0|4|decision|Đủ nhãn / revision / điều kiện xác nhận?
error|1|4|error|Chặn thiếu nhãn hoặc version xung đột
reveal|0|5|process|CONFIRMED → reveal prediction snapshot\nSo GT với AI gốc, không lấy dữ liệu sửa làm AI
report|0|6|end|Report theo model\nCoverage, stale, unreadable, OCR-assisted
reopen|1|5|process|Reopen để sửa nhãn\nTheo dõi revisedAfterReveal
''','''start|snapshot|
snapshot|blind|
blind|draft|
draft|confirm|
confirm|error|Không
confirm|reveal|Có
reveal|report|
reveal|reopen|sửa GT
reopen|draft|
''','Đây là kho nhãn live của backend, không phải thư mục ground_truth của benchmark Python. Endpoint đánh giá bundle A/B/C/R còn là một luồng riêng (sơ đồ 16).')

flow('16 · Đánh giá bundle thực nghiệm',[JAVA+'evaluation/EvaluationService.java',JAVA+'evaluation/EvaluationMetrics.java',JAVA+'evaluation/EvaluationData.java',JAVA+'controller/EvaluationController.java'],'''
start|0|0|start|API Evaluation đọc thư mục cấu hình
bundle|0|1|data|Bundle dataset / manual GT / run results\nChiến lược A, B, C, R
validate|0|2|process|Đọc schema strict · validate dữ liệu\nTính hash GT và bundle
metrics|0|3|process|EvaluationMetrics.calculate\nTổng hợp và theo loại / processing mode
compare|0|4|decision|Đủ metadata để so sánh các run?
yes|0|5|end|AVAILABLE + báo cáo comparability\nDataset, GT, code, model/config provenance
no|1|5|end|Không kết luận so sánh tương đương\nThiếu dữ liệu có trạng thái / chỉ số null
''','''start|bundle|
bundle|validate|
validate|metrics|
metrics|compare|
compare|yes|Có
compare|no|Không
''','R là baseline không LLM; A/B/C là AI variants theo định nghĩa service. Sơ đồ mô tả đường đọc/tính báo cáo, không khẳng định các run đã được thực nghiệm đầy đủ.')

flow('17 · Chạy benchmark file-only',['benchmark_runner/main.py','benchmark_runner/src/benchmark_runner.py','benchmark_runner/src/dataset_loader.py',JAVA+'controller/BenchmarkController.java',JAVA+'service/BenchmarkAdapter.java'],'''
start|0|0|start|CLI run --run-id ... --mode ocr/ai/both
dataset|0|1|process|Quét JPG/PNG/PDF · SHA-256\nSinh sample_id; trùng hash → SKIPPED_DUPLICATE
health|0|2|process|Health API + JWT ADMIN\nAI cần Ollama ready/modelDigest; pin signature
resume|0|3|decision|Resume khớp dataset/API/config/runner?
stop|1|3|error|Không khớp → từ chối resume
call|0|4|process|Tuần tự gọi /api/benchmark/ocr hoặc /ai\nController: feature flag, file/options, single gate
adapter|0|5|process|BenchmarkAdapter dùng helper OCR/AI\nKhông repository, MinIO hay nghiệp vụ persistence
evidence|0|6|data|raw/sample/attempt: response, ocr, ai, entry\nattempts.jsonl + checkpoint nguyên tử
next|0|7|decision|Còn mẫu chưa xử lý?
export|0|8|end|Xuất review workbook\nmode both còn xuất OCR và Ai workbook
''','''start|dataset|
dataset|health|
health|resume|
resume|stop|Không
resume|call|Run mới / khớp
call|adapter|
adapter|evidence|SUCCESS / FAILED
evidence|next|
next|call|Có
next|export|Không
''','mode both gọi endpoint AI có kèm OCR, không phải hai lần chạy OCR và AI độc lập. Retry/rerun chọn bởi flags và giữ attempt cũ. Gate benchmark giữ đến khi task thực sự kết thúc, kể cả HTTP timeout.')

flow('18 · Import GT và tính P/R/F1',['benchmark_runner/src/external_gt.py','benchmark_runner/src/ground_truth_manager.py','benchmark_runner/src/ai_evaluator.py','benchmark_runner/src/ai_workbook.py','benchmark_runner/src/review_workbook.py'],'''
start|0|0|start|Nguồn GT: Excel review chuẩn hoặc GTDOAN ngoài
external|0|1|process|import-external-gt: preview mặc định\nGT_Chung_tu + GT_Hang_hoa, Mã mẫu + STT
standard|1|1|process|import-ai / import-ground-truth\nKiểm snapshot + metadata + prediction bất biến
validate|0|2|process|Kiểm sample/file hash, type, giá trị\nGT ngoài: ô trống exclude mặc định; bỏ cột chiết khấu
commit|0|3|decision|Commit + đã đối chiếu + identity đúng?
preview|1|3|end|READY / INVALID / UNCONFIRMED\nPreview không ghi GT
version|0|4|data|ground_truth/sample/v*.json\nCONFIRMED + reviewer + hash + source
compare|0|5|process|export-ai: đọc evidence + latest GT\nNormalize từng trường; Hungarian items theo tên chính xác
count|0|6|process|Đúng → TP; sai giá trị → FP+FN\nBỏ sót → FN; tự tạo ở ABSENT → FP
metric|0|7|end|Precision=TP/(TP+FP); Recall=TP/(TP+FN)\nF1=2TP/(2TP+FP+FN); theo core / nhóm
''','''start|external|Excel ngoài
start|standard|Workbook benchmark
external|validate|
standard|validate|
validate|commit|
commit|preview|Preview hoặc chưa hợp lệ
commit|version|Có
version|compare|
compare|count|
count|metric|
''','UNREADABLE bị loại; GT ngoài dùng UNREADABLE cả cho ô chưa gán nhãn theo quy ước tương thích được lưu ở review_source. Core thiếu điều kiện thì null. Bộ export Ai.xlsx và evaluator cơ bản có phạm vi riêng, không gộp điểm tùy tiện.')

flow('19 · Đánh giá OCR và ghép run',['benchmark_runner/src/ocr_workbook.py','benchmark_runner/src/ocr_ground_truth.py','benchmark_runner/src/ocr_evaluator.py','benchmark_runner/src/paired_export.py'],'''
start|0|0|start|Run OCR đã lưu evidence
export|0|1|process|export-ocr → workbook OCR\nPhân biệt OCR / PDF_TEXT_LAYER / MIXED
truth|0|2|process|Người kiểm tra chép đầy đủ văn bản GT\nĐánh dấu checked và đối chiếu bản gốc
import|0|3|data|import-ocr → GT văn bản versioned\nKiểm identity / evidence / phạm vi import
checked|0|4|decision|Có văn bản GT đã checked?
none|1|4|end|CER/WER=null\nKhông dùng GT trường thay văn bản toàn trang
distance|0|5|process|Chuẩn hóa NFC và CRLF\nLevenshtein ký tự và token từ
score|0|6|end|CER=char_errors/GT_chars\nWER=word_errors/GT_words; mẫu rỗng → null
pair|2|2|process|export-pair --ocr-run ... --ai-run ...
identity|2|3|decision|Hai manifest cùng sample/hash/path?
reject|2|4|error|Khác → DATASET_MISMATCH
paired|2|5|data|Xuất OCR.xlsx + Ai.xlsx\ndataset_comparison.json; kiểm dataset hiện tại
''','''start|export|
export|truth|
truth|import|
import|checked|
checked|none|Không
checked|distance|Có
distance|score|
pair|identity|
identity|reject|Không
identity|paired|Có
''','CER/WER càng thấp càng tốt và có thể vượt 100%. PDF_TEXT_LAYER đo văn bản PDF đã lấy, không tự coi là đã kiểm thử nhận dạng OCR ảnh.')

flow('20 · Dashboard benchmark cục bộ',['benchmark_runner/main.py','benchmark_runner/src/dashboard.py','benchmark_runner/src/dashboard.html'],'''
start|0|0|start|CLI serve --port 8765
server|0|1|process|ThreadingHTTPServer tại 127.0.0.1\nKhông cần Node hoặc JWT để đọc
select|0|2|process|Browser chọn run → GET /api/runs, /api/data
data|0|3|data|Manifest + checkpoint + latest ai_review\nAi.summary + OCR.summary + GT versions
display|0|4|process|3 thẻ Core Micro P/R/F1\nBiểu đồ nhóm, OCR, coverage, bảng lỗi
refresh|1|4|decision|Bấm Cập nhật báo cáo?
post|1|5|process|POST /api/refresh\nKiểm same-origin + header; khóa đồng bộ
export|1|6|data|export_ai đọc evidence/GT → Excel mới\nKhông chạy lại model
reload|0|7|end|Load lại dữ liệu; null không thành 0\nGT/checkpoint mới có cảnh báo cần cập nhật
''','''start|server|
server|select|
select|data|
data|display|
display|refresh|
refresh|post|Có
post|export|
export|reload|
refresh|reload|Không: xem báo cáo hiện có
reload|select|chọn run / tải lại
''','OCR được hiển thị theo run nguồn riêng. Import GT và chạy model vẫn qua CLI có xác thực, dashboard không nhận token trên giao diện.')

flow('21 · Quản trị và danh mục',[JAVA+'controller/UserController.java',JAVA+'service/UserService.java',JAVA+'service/RoleService.java',JAVA+'service/AccountingCategoryService.java',JAVA+'service/InternalCompanyService.java','quan tri/src/App.tsx'],'''
start|0|0|start|ADMIN mở Users / Roles / Categories\nCác chức năng danh mục khác theo authority
auth|0|1|process|JWT + method security\nService kiểm quyền và phạm vi công ty
kind|0|2|decision|Đối tượng quản lý?
user|0|3|process|UserService\nTạo / cập nhật / trạng thái / mật khẩu
role|1|3|process|RoleService\nVai trò và permission được gán
catalog|2|3|process|Company / DocumentType / AccountingCategory\nDanh mục và trạng thái hoạt động
save|0|4|data|Repositories lưu dữ liệu quản trị
effective|0|5|end|Quyền/danh mục được dùng ở các request sau\nPipeline chỉ nạp loại/danh mục active
''','''start|auth|
auth|kind|
kind|user|User
kind|role|Role
kind|catalog|Danh mục
user|save|
role|save|
catalog|save|
save|effective|
''','Đây là bản đồ chức năng quản trị ở mức service, không khẳng định mọi thao tác quản trị đều có trên mobile. Đăng ký công khai bị chặn dù client còn route Register.')


flow('22 · Phân loại và trạng thái tổng hợp',[JAVA+'service/ClassificationService.java',JAVA+'service/DocumentBusinessStateService.java',JAVA+'service/PaymentLinkService.java'],'''
start|0|0|start|Đọc hoặc sửa kết quả phân loại
edit|0|1|process|lockForEdit → upsert / correction\nKiểm loại, danh mục và tài khoản theo service
classify|0|2|data|ACCEPTED / CORRECTED / NEED_REVIEW\nSửa tay giữ predictedLabel/confidence/reason gốc
business|1|0|start|GET /documents/{id}/business-state
load|1|1|process|Đọc Document + Classification\nReview events + ReconciliationState
payment|1|2|process|PaymentLinkService.calculate\nChỉ dùng khoản xác nhận thanh toán
mapping|1|3|process|Ánh xạ trạng thái cho giao diện\nCOMPLETED → processing PROCESSED\nSTART_REVIEW → IN_REVIEW khi phù hợp
done|0|4|end|Trả processing / classification / reconciliation\nreview / payment / reason và review events
''','''start|edit|
edit|classify|
classify|done|
business|load|
load|payment|
payment|mapping|
mapping|done|
''','Trạng thái tổng hợp để trình bày không thay enum trạng thái lưu DB. Classification ACCEPTED không đồng nghĩa Document APPROVED và cũng không chứng minh đã thanh toán.')

flow('23 · Đo công sức kiểm duyệt',[JAVA+'service/ReviewEffortService.java',JAVA+'service/ReviewWorkflowService.java','quan tri/src/utils/activeReviewTimer.ts'],'''
start|0|0|start|Người dùng làm việc trên màn kiểm duyệt
timer|0|1|process|Client theo dõi thời gian hoạt động\nGửi activeReviewMs khi quyết định
save|0|2|data|ReviewWorkflow giới hạn thời gian hợp lệ\nLưu DocumentReview + audit của chỉnh sửa
query|0|3|process|ReviewEffortService.report theo khoảng ngày\nĐọc quyết định và lịch sử trước lần duyệt
edits|0|4|process|Đếm chỉnh sửa thực sự đổi nội dung\nĐối chiếu AI_EXTRACT và HUMAN_EDIT_ACTIONS
metrics|0|5|end|Số quyết định / approval / reject\nThời gian trung bình, trung vị; touchless rate\nChia theo loại chứng từ
''','''start|timer|
timer|save|
save|query|
query|edits|
edits|metrics|
''','Thời gian chưa đo giữ null, không ước lượng. Touchless là duyệt không sửa theo audit, không phải chứng minh AI chính xác tuyệt đối theo GT.')

COLORS={'start':('#e0eaff','#315fa4'),'end':('#dcf4e5','#31845b'),'process':('#ffffff','#7490ac'),'decision':('#fff1cb','#ad842c'),'data':('#e9e1fa','#8961b0'),'error':('#fde7e7','#c66767')}
def build():
    mx=ET.Element('mxfile',host='app.diagrams.net',version='24.7.17',type='device',compressed='false')
    for index,f in enumerate(flows,1):
        diagram=ET.SubElement(mx,'diagram',id=f'flow-{index}',name=f['title'])
        maxrow=max(int(n[2]) for n in f['nodes'])
        model=ET.SubElement(diagram,'mxGraphModel',dx='1500',dy='1100',grid='1',gridSize='10',guides='1',tooltips='1',connect='1',arrows='1',fold='1',page='1',pageScale='1',pageWidth='1500',pageHeight=str(max(1100,650+maxrow*190)),math='0',shadow='0')
        root=ET.SubElement(model,'root');ET.SubElement(root,'mxCell',id='0');ET.SubElement(root,'mxCell',id='1',parent='0')
        def vertex(id,value,style,x,y,w,h):
            c=ET.SubElement(root,'mxCell',id=id,value=value,style=style,vertex='1',parent='1')
            ET.SubElement(c,'mxGeometry',x=str(x),y=str(y),width=str(w),height=str(h),attrib={'as':'geometry'})
        vertex('title',f['title'],'text;html=0;align=left;verticalAlign=middle;fontSize=25;fontStyle=1;fontColor=#173957;',55,15,1380,50)
        vertex('subtitle','Rà mã nguồn tĩnh · 07/11/2026 · Xanh: đầu/cuối | Vàng: điều kiện | Tím: dữ liệu | Đỏ: lỗi',
                'text;html=0;align=left;fontSize=13;fontColor=#546e87;',55,70,1380,40)
        ids=set()
        for id,col,row,kind,label in f['nodes']:
            assert id not in ids;ids.add(id)
            fill,stroke=COLORS[kind]
            shape='rhombus;' if kind=='decision' else 'rounded=1;arcSize=18;' if kind in {'start','end'} else 'shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=12;' if kind=='data' else 'rounded=1;arcSize=8;'
            vertex(id,label.replace('\\n','\n'),shape+f'whiteSpace=wrap;html=0;fillColor={fill};strokeColor={stroke};fontColor=#1b344d;fontSize=14;spacing=12;align=center;verticalAlign=middle;',
                    70+int(col)*470,135+int(row)*190,330,125 if kind=='decision' else 105)
        for i,edge in enumerate(f['edges']):
            source,target,label=edge
            assert source in ids and target in ids,(f['title'],edge)
            c=ET.SubElement(root,'mxCell',id=f'edge-{i}',value=label,edge='1',parent='1',source=source,target=target,
                style='edgeStyle=orthogonalEdgeStyle;rounded=1;orthogonalLoop=1;jettySize=auto;html=0;endArrow=block;endFill=1;strokeColor=#6d8399;fontSize=12;fontColor=#354e66;labelBackgroundColor=#ffffff;')
            ET.SubElement(c,'mxGeometry',relative='1',attrib={'as':'geometry'})
        y=135+(maxrow+1)*190
        vertex('note','LƯU Ý: '+f['note'],'rounded=1;whiteSpace=wrap;html=0;align=left;verticalAlign=middle;spacing=15;fillColor=#fff8e7;strokeColor=#e0c980;fontColor=#5b4b28;fontSize=13;',55,y,1390,100)
        vertex('sources','NGUỒN ĐỐI CHIẾU:\n'+'\n'.join(f['sources']),'text;whiteSpace=wrap;html=0;align=left;verticalAlign=top;fontSize=11;fontColor=#60758a;',55,y+115,1390,145)
    target=OUT/'HE_THONG_XU_LY.drawio'
    ET.indent(mx);ET.ElementTree(mx).write(target,encoding='utf-8',xml_declaration=True)
    ET.parse(target)
    return target


def inventory():
    patterns=[('backendqt/backendqt/src/main/java','*.java'),('quan tri/src','*.ts'),('quan tri/src','*.tsx'),('invoice_1/lib','*.dart'),('benchmark_runner/src','*.py')]
    paths={ROOT/'benchmark_runner/main.py'}
    for folder,pattern in patterns: paths.update((ROOT/folder).rglob(pattern))
    records=[]
    for path in sorted(paths):
        content=path.read_text(encoding='utf-8-sig')
        records.append(dict(path=path.relative_to(ROOT).as_posix(),lines=len(content.splitlines()),sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
            entrypoints=re.findall(r'^\s*(?:public\s+(?:static\s+)?[^=;\n]+?\s+(\w+)\s*\(|(?:async\s+)?def\s+(\w+)\s*\(|export\s+(?:async\s+)?function\s+(\w+)\s*\()',content,re.M)))
    for f in flows:
        for source in f['sources']: assert (ROOT/source).is_file(),source
    (OUT/'doi_chieu_nguon.json').write_text(json.dumps(dict(date='2026-11-07',scope='Static source inventory; not runtime execution proof',files=records,diagrams=[dict(title=f['title'],sources=f['sources'],note=f['note']) for f in flows]),ensure_ascii=False,indent=2),encoding='utf-8')
    return records


def readme(records):
    text=['# Luồng xử lí theo mã nguồn hiện tại','',
        f'Mở **[HE_THONG_XU_LY.drawio](HE_THONG_XU_LY.drawio)** bằng extension Draw.io trong IDE. File chứa {len(flows)} trang; chọn tab ở cạnh dưới để chuyển sơ đồ. Có thể dùng Open With → Draw.io Editor nếu IDE mở XML.',
        '', 'Không cần thêm MCP, cài package hoặc tải dữ liệu ra dịch vụ ngoài. Các node, nhãn, điều kiện và connector đều chỉnh sửa được; file là XML draw.io không nén.',
        '', '## Phạm vi và cách đọc','',
        f'Đối chiếu tĩnh ngày 07/11/2026. Danh mục nguồn bao gồm {len(records)} file, {sum(r["lines"] for r in records):,} dòng thuộc Java application, React src, Flutter lib và benchmark Python. Quét danh mục/entrypoint toàn bộ phạm vi này; đọc chi tiết các service/call site cốt lõi để dựng luồng. Đây không phải khẳng định đã kiểm thử mọi nhánh hoặc đọc tuần tự từng dòng của toàn bộ repository.',
        '', 'Không quét credentials, .env, config.local, dữ liệu khách hàng, thư viện vendor hay file build. Các file test không được dùng làm bằng chứng endpoint đang được UI gọi. Luồng thể hiện implementation, không xác nhận mọi dịch vụ đang chạy trên máy.',
        '', 'Xanh dương: đầu vào; xanh lá: kết thúc; trắng: xử lý; vàng: quyết định; tím: dữ liệu; đỏ: nhánh lỗi/từ chối. Nhãn cạnh là điều kiện. Mỗi trang có lưu ý và danh sách nguồn ở cuối.',
        '', '## Mục lục và nguồn','']
    for f in flows:
        text += ['### '+f['title'],'',f['note'],'']
        text += ['- ['+Path(s).name+'](../'+s.replace(' ','%20')+')' for s in f['sources']]
        text += ['']
    text += ['## Các ranh giới quan trọng','',
        '- Pipeline nghiệp vụ và benchmark file-only dùng helper chung nhưng khác persistence. Benchmark không tạo dữ liệu nghiệp vụ.',
        '- PDF native có thể không chạy OCR ảnh; PDF hỗn hợp xử lý theo từng trang. Nhánh thực gọi là analyzePdfPages, không gộp mọi helper OCR vào một đường chạy.',
        '- Có DocumentTextExtractionService/TesseractOcrService và helper analyzeVisionFallback trong mã. Không vẽ chúng thành đường chạy chính của processPdf/processImage khi call site đã đọc không gọi chúng. Chúng được giữ trong inventory để tra cứu.',
        '- Adaptive OCR dùng điểm heuristic. FieldVerification dùng bằng chứng/rule. Hai loại điểm này không thay thế GT hoặc F1/CER/WER độc lập.',
        '- APPROVE khóa chứng từ, không sinh phiếu thu/chi; tiền thực thu/chi cần xác nhận riêng.',
        '- Gán nhãn live trên ứng dụng, evaluation bundle backend và GT benchmark Python là ba kho/luồng khác nhau.',
        '- Public register bị chặn ở backend; DELETE chứng từ vật lý cũng bị chặn dù controller/client còn phương thức.',
        '- Gộp PDF items có distinct trong implementation: nguy cơ mất các dòng trùng nội dung cần kiểm tra nghiệp vụ; sơ đồ ghi theo code, không ngầm coi là hành vi đúng mong muốn.',
        '- GT ngoài dùng UNREADABLE để loại cả ô chưa gán nhãn theo quy ước tương thích; không diễn giải tất cả là không đọc được trên bản gốc.',
        '', '## Tái tạo và kiểm tra','',
        'Chạy từ gốc dự án:', '', '```powershell', '.\\benchmark_runner\\.venv\\Scripts\\python.exe ".\\luồng xử lí\\tao_so_do.py"', '```', '',
        'Script kiểm mọi source link tồn tại, ID node không trùng, cạnh có đầu/cuối hợp lệ và XML parse được. `doi_chieu_nguon.json` giữ SHA-256, số dòng và entrypoint để biết khi source đổi cần rà lại. Script chỉ ghi ba artifact do nó quản lý trong thư mục này; không sửa source ứng dụng.',
        '', 'Chưa render bằng extension Draw.io của người dùng: công cụ trình duyệt không khả dụng trong phiên trước. Các tọa độ được đặt cố định theo cột/hàng và connector dùng orthogonal routing của draw.io. Khi xuất hình có thể dùng File → Export as → SVG/PDF trong editor.']
    (OUT/'README.md').write_text('\n'.join(text)+'\n',encoding='utf-8')


if __name__=='__main__':
    target=build(); records=inventory();readme(records)
    print(json.dumps(dict(diagrams=len(flows),nodes=sum(len(f['nodes']) for f in flows),edges=sum(len(f['edges']) for f in flows),source_files=len(records),file=str(target)),ensure_ascii=True))

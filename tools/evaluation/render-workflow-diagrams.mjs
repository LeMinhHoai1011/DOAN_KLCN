// Regenerate the standalone documentation viewer without external dependencies.
import { writeFileSync } from 'node:fs';

const diagrams = [
  ['overview', 'Tổng quan', 'Kiến trúc hệ thống', 'Hai ứng dụng dùng chung backend; backend kiểm tra quyền và phạm vi dữ liệu.', [
    ['Web React / Mobile Flutter', 'Gửi yêu cầu REST kèm JWT.'],
    ['Spring Boot API', 'Xác thực → kiểm tra quyền → giới hạn doanh nghiệp/chủ sở hữu.'],
    ['Service nghiệp vụ', 'Chứng từ, kiểm duyệt, đối soát, thu/chi và báo cáo.'],
    ['Dữ liệu và file', 'PostgreSQL lưu nghiệp vụ; MinIO lưu file và phiên bản.'],
    ['OCR và AI', 'PDFBox / Tesseract → AI text hoặc vision → lưu kết quả vào nghiệp vụ.'],
    ['Đánh giá độc lập', 'Bundle thí nghiệm → EvaluationService → thống kê / export.'],
  ], 'OCR/AI trả kết quả về service; đây là vòng trao đổi dữ liệu, không phải chuỗi bước kéo dài vô hạn.'],
  ['auth', 'Đăng nhập', 'Đăng nhập và truy cập', 'Quyền được kiểm tra lại ở backend cho mỗi yêu cầu.', [
    ['Nhập tài khoản', 'Backend nạp và khóa dòng tài khoản để xác thực.'],
    ['Kiểm tra mật khẩu', 'Sai → trả lỗi. Đúng → sinh JWT và thông tin user.'],
    ['Lưu phiên trên client', 'Web/mobile điều hướng đến màn hình phù hợp.'],
    ['Gọi API với Bearer token', 'JWT filter kiểm tra token; kiểm tra role/permission.'],
    ['Kiểm tra phạm vi dữ liệu', 'Giới hạn doanh nghiệp/chủ sở hữu theo service.'],
    ['Trả kết quả', 'Được phép → dữ liệu. Không được phép → lỗi truy cập.'],
  ], 'Đăng ký công khai đang bị backend vô hiệu hóa; tài khoản được tạo qua quản trị viên.'],
  ['ocr', 'Upload & OCR/AI', 'Tiếp nhận và xử lý chứng từ', 'Upload được lưu trước, rồi OCR/AI chạy đồng bộ trong request.', [
    ['Tiếp nhận file', 'Kiểm tra quyền và file → lưu MinIO, Document, version 1; commit phần upload.'],
    ['Bắt đầu pipeline', 'Kiểm tra/khóa trạng thái; nạp loại chứng từ và danh mục; đọc lại file.'],
    ['Chọn nguồn nội dung', 'Ảnh → Adaptive OCR. PDF → text layer theo trang; trang scan → render + OCR.'],
    ['Chọn nhánh AI', 'PDF text tốt → AI text. OCR ảnh đủ tốt → AI text; OCR kém → vision. Một số lỗi output text ảnh → fallback vision.'],
    ['Kiểm tra và lưu kết quả', 'Gộp trang → validator → OCR, fields, invoice/items và classification.'],
    ['Kết thúc', 'Đạt → PROCESSED. Cần kiểm tra → NEED_REVIEW. Lỗi xử lý → ghi lỗi/lịch sử, giữ chứng từ đã upload.'],
  ], 'Có process / reprocess / retry theo điều kiện trạng thái. PDF text gọi AI text trực tiếp; không áp dụng nhầm fallback của nhánh ảnh.'],
  ['review', 'Kiểm duyệt', 'Duyệt và bổ sung chứng từ', 'ADMIN/ACCOUNTANT ra quyết định; EMPLOYEE/USER gửi hoặc gửi lại theo trạng thái hợp lệ.', [
    ['Chứng từ mới', 'UPLOADED / PENDING. SUBMIT chỉ ghi lịch sử ở trạng thái này, không tự chạy AI.'],
    ['Đã xử lý', 'Pipeline thành công → PROCESSED hoặc NEED_REVIEW / PENDING.'],
    ['Bắt đầu duyệt (tùy chọn)', 'START_REVIEW → NEED_REVIEW / PENDING; có thể APPROVE trực tiếp từ bước 2.'],
    ['Phê duyệt', 'APPROVE → COMPLETED / APPROVED; khóa workflow tiếp theo.'],
    ['Hoặc trả lại', 'REJECT → REJECTED; REQUEST_INFO → CORRECTED. Cả hai giữ NEED_REVIEW và bắt buộc lý do.'],
    ['Bổ sung và gửi lại', 'RESUBMIT → UPLOADED / PENDING → gọi xử lý lại riêng; quay về bước 2 khi xử lý thành công.'],
  ], 'Bước 4 và 5 là hai nhánh lựa chọn. Phê duyệt không tự tạo phiếu thu/chi và không chứng minh đã thanh toán.'],
  ['invoice', 'Đối soát hóa đơn', 'Đối soát số học hóa đơn', 'Kiểm tra dữ liệu hóa đơn, độc lập với đối soát tiền đã thu/chi.', [
    ['Chọn chứng từ', 'Kế toán/admin chạy đối soát; nạp hóa đơn và dòng hàng đúng phạm vi.'],
    ['Kiểm tra từng dòng', 'Thành tiền = số lượng × đơn giá; VAT theo thuế suất.'],
    ['Kiểm tra các tổng', 'Tổng dòng ↔ subtotal; tổng VAT dòng ↔ VAT; subtotal + VAT ↔ total.'],
    ['Phân loại kết quả', 'Đủ và khớp → MATCHED. Lệch → MISMATCH. Thiếu → INSUFFICIENT_DATA.'],
    ['Lưu batch và kết quả', 'Người dùng xem và ra quyết định thủ công theo quyền.'],
    ['Tổng hợp trạng thái', 'Dùng batch mới nhất; hóa đơn sửa sau batch → WARNING, cần kiểm tra/chạy lại.'],
  ], 'Các phép tính làm tròn HALF_UP đến 2 chữ số; thiếu dòng hàng phải báo thiếu dữ liệu.'],
  ['payment', 'Ghi nhận & đối soát tiền', 'Ghi nhận và đối soát thanh toán', 'Luồng chính cho phiếu có liên kết chứng từ/hóa đơn. Các nhánh sửa, hủy và dữ liệu cũ được giữ đầy đủ bên dưới.', [
    ['Chọn hồ sơ và preview', 'Chọn doanh nghiệp, chứng từ/hóa đơn → kiểm tra quan hệ liên kết; gợi ý thu/chi từ MST.'],
    ['Nhập xác nhận tiền', 'Người dùng nhập số tiền, ngày, phương thức; xác nhận phiếu thu/chi.'],
    ['Kiểm tra khi lưu', 'Quyền + khóa công ty + request key. Hồ sơ liên kết phải đủ điều kiện duyệt, tổng tiền và VND; chặn sai chiều và vượt tổng.'],
    ['Ghi nhận giao dịch', 'Lưu ACTIVE / CONFIRMED cùng audit. Phê duyệt chứng từ không tự thực hiện bước này.'],
    ['Tính tổng thanh toán', 'Chỉ cộng CONFIRMED; tính tổng đã trả và còn lại so với giá trị hồ sơ.'],
    ['Chạy đối soát tiền', 'Người dùng gọi đối soát → kiểm tra quyền, khóa công ty, tính lại summary → lưu PAYMENT_RECONCILE và snapshot giao dịch.'],
  ], 'Đọc tổng hợp thanh toán không tự lưu một lần đối soát. Chỉ thao tác chạy đối soát mới ghi snapshot/lịch sử.', [
    ['Kết quả tổng hợp', 'Chưa trả / trả một phần / đủ / vượt. OVERPAID là khả năng phát hiện trong summary; API tạo/sửa phiếu liên kết đang chặn vượt tổng.'],
    ['Sửa hoặc hủy → tính lại', 'Kiểm tra expectedUpdatedAt. Sửa: giữ CONFIRMED. Hủy: bắt buộc lý do → CANCELLED, loại khỏi tổng; ghi audit. Lần đọc summary/đối soát tiếp theo tính lại.'],
    ['Dữ liệu chưa đủ', 'Giao dịch cũ chưa xác minh → trạng thái chính thức có thể chưa xác định. Thiếu tổng → chưa kết luận. Ngoại tệ không tự quy đổi; có hỗ trợ phiếu không liên kết.'],
  ]],
  ['evaluation', 'Đánh giá OCR/AI', 'Chuẩn bị và đọc dữ liệu đánh giá', 'Đóng gói bằng chứng đã ghi nhận; công cụ này không tự chạy thí nghiệm OCR/AI.', [
    ['Chuẩn bị đầu vào', 'Manifest + mẫu xác minh thủ công + cấu hình + evidence.'],
    ['Kiểm tra bundle', 'Schema, đường dẫn, giới hạn kích thước, sample và SHA-256.'],
    ['Ghi output mới', 'Không ghi đè; tạo assets, configs, evidence và benchmark.json.'],
    ['Backend đọc bundle', 'EvaluationService kiểm tra tính hợp lệ từ thư mục cấu hình.'],
    ['Lọc và so sánh', 'Model, strategy A/B/C, thời gian; kiểm tra điều kiện so sánh.'],
    ['Thống kê và export', 'Chỉ kết luận theo dữ liệu đánh giá hiện có.'],
  ], 'Confidence không phải accuracy; cần ground truth và lượt chạy đủ điều kiện để so sánh.'],
];
const esc = s => s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const panels = diagrams.map(([id,,title,description,steps,note,branches], index) => `
<section id="panel-${id}" role="tabpanel" aria-labelledby="tab-${id}" tabindex="0" ${index ? 'hidden' : ''}>
  <header class="panel-heading"><span class="eyebrow">LUỒNG ${index + 1} / ${diagrams.length}</span><h2>${esc(title)}</h2><p>${esc(description)}</p></header>
  <ol class="flow">${steps.map(([label,detail],i) => `<li><span class="number">${i+1}</span><div><h3>${esc(label)}</h3><p>${esc(detail)}</p></div></li>`).join('')}</ol>
  ${branches ? `<div class="branches" aria-label="Kết quả và nhánh xử lý">${branches.map(([label,detail]) => `<article><h3>${esc(label)}</h3><p>${esc(detail)}</p></article>`).join('')}</div>` : ''}
  <aside>${esc(note)}</aside>
</section>`).join('\n');
const document = `<!doctype html>
<html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Sơ đồ luồng xử lý hiện tại</title>
<style>
*{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:#f3f6fb;color:#172b49;font:15px/1.55 system-ui,-apple-system,Segoe UI,Arial}main{max-width:1180px;margin:auto;padding:22px 24px 36px}h1{font-size:25px;line-height:1.25;margin:0 0 6px}a{color:#2459c4}.intro{margin:0 0 16px;color:#52647c;font-size:13px}.tabs{position:sticky;top:0;z-index:5;display:flex;gap:7px;flex-wrap:wrap;padding:12px 0;background:#f3f6fb;border-bottom:1px solid #dce4ef}.tabs button{font:inherit;font-size:13px;font-weight:600;cursor:pointer;border:1px solid #d3deec;background:white;color:#415875;padding:9px 12px;border-radius:8px}.tabs button[aria-selected=true]{background:#2058ba;border-color:#2058ba;color:white}.tabs button:hover{border-color:#2058ba}button:focus-visible,section:focus-visible{outline:3px solid #83b5ff;outline-offset:3px}section{background:white;border:1px solid #dde5ef;border-radius:12px;padding:20px;margin-top:14px;scroll-margin-top:110px}section[hidden]{display:none}.eyebrow{font-size:11px;font-weight:700;letter-spacing:.1em;color:#3970bd}h2{font-size:22px;line-height:1.3;margin:4px 0 6px}.panel-heading>p{color:#64748b;margin:0 0 18px;font-size:14px}.flow{list-style:none;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:24px 28px;margin:0;padding:0}.flow li{position:relative;display:flex;align-items:flex-start;gap:10px;min-width:0;padding:14px;background:#f0f6ff;border:1px solid #cadaf0;border-radius:10px}.flow li:not(:nth-child(3n)):not(:last-child)::after{content:'→';position:absolute;right:-24px;top:38%;color:#6789b5;font-size:21px}.flow li:nth-child(4)::before{content:'↓ Tiếp từ bước 3';position:absolute;top:-23px;left:0;color:#6789b5;font-size:11px}.number{display:grid;place-items:center;background:#2460bd;color:white;border-radius:50%;width:25px;height:25px;flex-shrink:0;font-size:12px;font-weight:700}h3{font-size:14px;line-height:1.4;margin:1px 0 6px}p{margin:0}.flow p,.branches p{font-size:13px;color:#475e7b;overflow-wrap:anywhere}.branches{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:20px;padding-top:17px;border-top:1px dashed #cdd9e8}.branches article{padding:12px;background:#fffbeb;border:1px solid #f1dfa8;border-radius:8px}aside{font-size:13px;color:#4d6078;background:#f7f9fc;border-left:3px solid #81a7df;padding:10px 12px;margin-top:18px}.reading{font-size:12px;color:#728197;margin-top:10px}.actions{display:flex;gap:10px;align-items:center;margin-top:14px}.actions button{padding:7px 12px;border:1px solid #ccd9eb;background:white;border-radius:6px;cursor:pointer;color:#365b91}.actions span{font-size:12px;color:#708096}@media(max-width:760px){main{padding:14px 12px}h1{font-size:21px}section{padding:15px}.tabs{gap:5px}.tabs button{padding:7px 9px;font-size:12px}.flow{grid-template-columns:1fr;gap:22px}.flow li:not(:last-child)::after,.flow li:nth-child(3)::after{content:'↓';left:50%;right:auto;top:auto;bottom:-25px;font-size:21px}.flow li:nth-child(4)::before{display:none}.branches{grid-template-columns:1fr}h2{font-size:20px}}@media(prefers-reduced-motion:reduce){html{scroll-behavior:auto}}@media print{body{background:white}main{max-width:none;padding:0}.tabs,.actions,.intro{display:none}section[hidden]{display:block}section{break-before:page;break-inside:avoid;border:0}section:first-of-type{break-before:auto}.flow li,.branches article{break-inside:avoid}h1{font-size:20px}}
</style></head><body><main>
<h1>Sơ đồ luồng xử lý hiện tại</h1>
<p class="intro">Khảo sát source 23/10/2026 · <a href="BAO_CAO_LUONG_XU_LY_HIEN_TAI.md">Báo cáo chức năng và dẫn chiếu mã nguồn</a></p>
<nav class="tabs" role="tablist" aria-label="Chọn luồng xử lý">${diagrams.map(([id,label],i)=>`<button type="button" role="tab" id="tab-${id}" aria-controls="panel-${id}" aria-selected="${!i}" tabindex="${i ? -1 : 0}">${esc(label)}</button>`).join('')}</nav>
${panels}
<p class="reading">Đọc từ trái sang phải, hết hàng trên xuống hàng dưới. Nhánh lựa chọn và vòng quay lại được ghi rõ trong từng ô.</p>
<div class="actions"><button type="button" id="top">↑ Đầu sơ đồ</button><button type="button" id="print">In tất cả luồng</button><span>Chọn tab để đưa luồng cần xem lên trên.</span></div>
</main><script>
const tabs=[...document.querySelectorAll('[role="tab"]')];
function activate(tab,scroll=true){
 tabs.forEach(item=>{const active=item===tab;item.setAttribute('aria-selected',String(active));item.tabIndex=active?0:-1;document.getElementById(item.getAttribute('aria-controls')).hidden=!active;});
 const id=tab.id.slice(4);history.replaceState(null,'','#'+id);
 if(scroll)requestAnimationFrame(()=>document.querySelector('.tabs').scrollIntoView({block:'start',behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth'}));
}
tabs.forEach((tab,index)=>{tab.addEventListener('click',()=>activate(tab));tab.addEventListener('keydown',event=>{let next;if(event.key==='ArrowRight')next=(index+1)%tabs.length;else if(event.key==='ArrowLeft')next=(index+tabs.length-1)%tabs.length;else if(event.key==='Home')next=0;else if(event.key==='End')next=tabs.length-1;else return;event.preventDefault();tabs[next].focus();activate(tabs[next]);});});
function fromHash(){const tab=tabs.find(item=>'#'+item.id.slice(4)===location.hash);if(tab)activate(tab,false);}
fromHash();addEventListener('hashchange',fromHash);
document.getElementById('top').addEventListener('click',()=>document.querySelector('.tabs').scrollIntoView({block:'start'}));
document.getElementById('print').addEventListener('click',()=>window.print());
</script></body></html>`;
writeFileSync(new URL('../../docs/SO_DO_LUONG_XU_LY_HIEN_TAI.html', import.meta.url), document, 'utf8');
console.log(`Rendered ${diagrams.length} responsive workflow tabs.`);


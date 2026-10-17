package com.example.invoice.service;

import com.example.invoice.dto.accounting.FinancialTransactionRequest;
import com.example.invoice.dto.accounting.FinancialTransactionResponse;
import com.example.invoice.entity.AccountingCategory;
import com.example.invoice.entity.Company;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.FinancialTransaction;
import com.example.invoice.entity.Invoice;
import com.example.invoice.entity.User;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.AccountingCategoryRepository;
import com.example.invoice.repository.CompanyRepository;
import com.example.invoice.repository.DocumentRepository;
import com.example.invoice.repository.FinancialTransactionRepository;
import com.example.invoice.repository.InvoiceRepository;
import com.example.invoice.repository.ClassificationRepository;
import java.util.Optional;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinancialTransactionService {
	private final FinancialTransactionRepository transactionRepository;
	private final AccountingCategoryRepository categoryRepository;
	private final CompanyRepository companyRepository;
	private final DocumentRepository documentRepository;
	private final InvoiceRepository invoiceRepository;
	private final ClassificationRepository classificationRepository;
	private final UserService userService;

    @org.springframework.beans.factory.annotation.Autowired private PaymentLinkService payments;
    @org.springframework.beans.factory.annotation.Autowired private PaymentSchemaGuard paymentSchema;
    @org.springframework.beans.factory.annotation.Autowired private com.example.invoice.repository.AuditLogRepository audit;
    @org.springframework.beans.factory.annotation.Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    public static final java.util.List<String> PAYMENT_METHODS=java.util.List.of("CASH","BANK_TRANSFER","CARD","OTHER");

    /** Compatibility boundary: approval is never evidence of a cash payment. */
    @Deprecated
    public Optional<FinancialTransaction> createFromApprovedDocument(Document document, User actor) { return Optional.empty(); }

	@Transactional(readOnly = true)
	public Page<FinancialTransactionResponse> findAll(LocalDate dateFrom, LocalDate dateTo, String transactionType,
			Long categoryId, Long documentId, Long invoiceId, Long companyId, Pageable pageable, Authentication authentication) {
        com.example.invoice.util.DateRanges.validate(dateFrom, dateTo);
		User user = userService.loadCurrent(authentication);
		Long permittedCompanyId = resolveCompanyId(user, companyId);
		Specification<FinancialTransaction> specification = (root, query, builder) -> builder.equal(root.get("company").get("id"), permittedCompanyId);
		if (dateFrom != null) specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("transactionDate"), dateFrom));
		if (dateTo != null) specification = specification.and((root, query, builder) -> builder.lessThanOrEqualTo(root.get("transactionDate"), dateTo));
		if (transactionType != null && !transactionType.isBlank()) specification = specification.and((root, query, builder) -> builder.equal(root.get("transactionType"), transactionType));
		if (categoryId != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("accountingCategory").get("id"), categoryId));
		if (documentId != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("document").get("id"), documentId));
		if (invoiceId != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("invoice").get("id"), invoiceId));
		return transactionRepository.findAll(specification, pageable).map(this::toResponse);
	}

 @Transactional(readOnly=true)
 public Page<FinancialTransactionResponse> unverified(Long company,Pageable pageable,Authentication auth){var actor=payments.authorize(auth,"ACCOUNTING_VIEW");Long target=payments.company(actor,company);Specification<FinancialTransaction> spec=(root,q,b)->b.and(b.equal(root.get("company").get("id"),target),b.isNull(root.get("paymentConfirmation")));return transactionRepository.findAll(spec,pageable).map(this::toResponse);}

 @Transactional
 public FinancialTransactionResponse create(FinancialTransactionRequest request, Authentication authentication) {
   User actor=payments.authorize(authentication,"ACCOUNTING_CREATE");Long company=payments.company(actor,request.companyId());payments.lockCompany(company);
   validate(request);String key;try{key=java.util.UUID.fromString(request.requestKey()).toString();}catch(Exception e){throw new BadRequestException("REQUEST_KEY_REQUIRED: Phiếu mới cần khóa request UUID hợp lệ");}
   var link=payments.resolve(company,request.documentId(),request.invoiceId(),false);String fingerprint=fingerprint(request,company,link);
   var prior=transactionRepository.findByCompanyIdAndRequestKey(company,key).orElse(null);
   if(prior!=null){if(!fingerprint.equals(prior.getRequestFingerprint())||prior.getCreatedBy()==null||!actor.getId().equals(prior.getCreatedBy().getId()))throw conflict("IDEMPOTENCY_KEY_REUSED: Khóa request đã được dùng cho nội dung khác");return toResponse(prior);}
   link=payments.resolve(company,request.documentId(),request.invoiceId(),true);validateLimit(company,link,request,null);paymentSchema.requireWritable(link,payments.linked(company,link),null);
   FinancialTransaction t=new FinancialTransaction();t.setCompany(companyRepository.findById(company).orElseThrow());t.setCreatedBy(actor);
   apply(t,request,link);t.setStatus("ACTIVE");t.setPaymentConfirmation("CONFIRMED");t.setRequestKey(key);t.setRequestFingerprint(fingerprint);
   transactionRepository.saveAndFlush(t);entityManager.refresh(t);record(t,actor,"PAYMENT_CREATE",null,toResponse(t));return toResponse(t);
 }
 @Transactional
 public FinancialTransactionResponse update(Long id,FinancialTransactionRequest request,Authentication auth){
   User actor=payments.authorize(auth,"ACCOUNTING_UPDATE");FinancialTransaction t=loadForCompany(id,actor);payments.lockCompany(t.getCompany().getId());entityManager.refresh(t,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
   requireEditable(t);requireFresh(t,request.expectedUpdatedAt());validate(request);
   if(request.companyId()!=null&&!request.companyId().equals(t.getCompany().getId()))throw new BadRequestException("Không chuyển giao dịch sang công ty khác");
   var link=payments.resolve(t.getCompany().getId(),request.documentId(),request.invoiceId(),true);validateLimit(t.getCompany().getId(),link,request,id);paymentSchema.requireWritable(link,payments.linked(t.getCompany().getId(),link),id);
   var old=toResponse(t);apply(t,request,link);transactionRepository.saveAndFlush(t);entityManager.refresh(t);record(t,actor,"PAYMENT_UPDATE",old,toResponse(t));return toResponse(t);
 }
 @Transactional
 public FinancialTransactionResponse cancel(Long id,String reason,java.time.LocalDateTime expected,Authentication auth){
   User actor=payments.authorize(auth,"ACCOUNTING_DELETE");var t=loadForCompany(id,actor);payments.lockCompany(t.getCompany().getId());entityManager.refresh(t,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
   if("CANCELLED".equals(t.getPaymentConfirmation()))return toResponse(t);requireEditable(t);requireFresh(t,expected);
   if(reason==null||reason.isBlank()||reason.length()>1000)throw new BadRequestException("CANCELLATION_REASON_REQUIRED: Nhập lý do hủy hợp lệ");
   var old=toResponse(t);t.setPaymentConfirmation("CANCELLED");t.setStatus("CANCELLED");transactionRepository.saveAndFlush(t);entityManager.refresh(t);
   record(t,actor,"PAYMENT_CANCEL: "+reason.trim(),old,toResponse(t));return toResponse(t);
 }
 public void delete(Long id,Authentication auth){payments.authorize(auth,"ACCOUNTING_DELETE");throw new BadRequestException("USE_CANCELLATION_ENDPOINT: Dùng thao tác hủy có lý do để giữ lịch sử");}
 private void requireEditable(FinancialTransaction t){if(!"CONFIRMED".equals(t.getPaymentConfirmation()))throw conflict("PAYMENT_NOT_EDITABLE: Phiếu đã hủy hoặc dữ liệu cũ chưa xác minh, cần đối soát riêng");}
 private void requireFresh(FinancialTransaction t,java.time.LocalDateTime expected){if(expected==null||!expected.equals(t.getUpdatedAt()))throw conflict("PAYMENT_STALE: Phiếu đã thay đổi hoặc thiếu phiên bản; tải lại trước khi sửa/hủy");}
 private org.springframework.web.server.ResponseStatusException conflict(String message){return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,message);}
 private void validate(FinancialTransactionRequest r){
   if(!Boolean.TRUE.equals(r.paymentConfirmed()))throw new BadRequestException("PAYMENT_CONFIRMATION_REQUIRED: Phải xác nhận tiền đã thực thu hoặc thực chi");
   if(!java.util.List.of("INCOME","EXPENSE").contains(r.transactionType()==null?"":r.transactionType())||r.amount()==null||r.amount().signum()<=0||r.amount().scale()>2||r.amount().precision()>19||r.amount().precision()-r.amount().scale()>17||r.transactionDate()==null)throw new BadRequestException("INVALID_PAYMENT: Loại, ngày hoặc số tiền không hợp lệ");
   if(r.paymentMethod()==null||!PAYMENT_METHODS.contains(r.paymentMethod()))throw new BadRequestException("INVALID_PAYMENT_METHOD: Chọn phương thức thanh toán hợp lệ");
 }
 private void validateLimit(Long company,PaymentLinkService.Link link,FinancialTransactionRequest request,Long exclude){
   if(link.document()!=null){var direction=payments.direction(link);if("INVOICE_COMPANY_TAX_CODE".equals(direction.source())&&!request.transactionType().equals(direction.type()))throw new BadRequestException("PAYMENT_DIRECTION_MISMATCH: Loại Thu/Chi không khớp bên mua/bán của doanh nghiệp trên hóa đơn.");var summary=payments.calculate(company,link,exclude);if(summary.confirmedPaid().add(request.amount()).compareTo(summary.totalAmount())>0)throw conflict("PAYMENT_EXCEEDS_TOTAL: Tổng thanh toán vượt giá trị hồ sơ");
     if(payments.linked(company,link).stream().anyMatch(t->"CONFIRMED".equals(t.getPaymentConfirmation())&&!java.util.Objects.equals(t.getId(),exclude)&&!request.transactionType().equals(t.getTransactionType())))throw new BadRequestException("PAYMENT_DIRECTION_CONFLICT: V1 không trộn Thu/Chi hoặc hoàn tiền trên cùng hồ sơ");}
   if(request.categoryId()!=null){var c=categoryRepository.findById(request.categoryId()).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy danh mục"));
     if(c.getCompany()==null||!company.equals(c.getCompany().getId())||!c.isActive())throw new BadRequestException("INVALID_PAYMENT_CATEGORY: Danh mục không hoạt động hoặc khác doanh nghiệp");
     if(c.getCategoryType()!=null&&!c.getCategoryType().name().equals(request.transactionType()))throw new BadRequestException("CATEGORY_TYPE_CONFLICT: Danh mục không khớp loại Thu/Chi");}
 }
 private void apply(FinancialTransaction t,FinancialTransactionRequest r,PaymentLinkService.Link link){
   t.setTransactionType(r.transactionType());t.setAmount(r.amount());t.setTransactionDate(r.transactionDate());t.setDescription(r.description()==null?null:r.description().trim());
   t.setDocument(link.document());t.setInvoice(link.invoice());t.setAccountingCategory(r.categoryId()==null?null:categoryRepository.findById(r.categoryId()).orElseThrow());t.setPaymentMethod(r.paymentMethod());
 }
 private String fingerprint(FinancialTransactionRequest r,Long company,PaymentLinkService.Link link){try{
   var v=new java.util.LinkedHashMap<String,Object>();v.put("company",company);v.put("type",r.transactionType());v.put("amount",r.amount().setScale(2).toPlainString());v.put("date",r.transactionDate());v.put("description",r.description()==null?"":r.description().trim());v.put("category",r.categoryId());v.put("document",link.document()==null?null:link.document().getId());v.put("invoice",link.invoice()==null?null:link.invoice().getId());v.put("method",r.paymentMethod());
   return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(v)));
 }catch(Exception e){throw new IllegalStateException("Không tạo được mã kiểm tra request",e);}}
 private void record(FinancialTransaction t,User actor,String action,Object old,Object next){try{
   var event=new com.example.invoice.entity.AuditLog();event.setUser(actor);event.setCompany(t.getCompany());event.setEntityType("FINANCIAL_TRANSACTION");event.setEntityId(t.getId().toString());event.setAction(action.startsWith("PAYMENT_CANCEL:")?"PAYMENT_CANCEL":action);
   event.setOldValue(old==null?null:json.writeValueAsString(old));event.setNewValue(json.writeValueAsString(java.util.Map.of("transaction",next,"reason",action.startsWith("PAYMENT_CANCEL:")?action.substring(15).trim():"")));audit.save(event);
 }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Không lưu được lịch sử thanh toán",e);}}
 @Transactional(readOnly=true)
 public java.util.List<com.example.invoice.entity.AuditLog> history(Long id,Authentication auth){User actor=payments.authorize(auth,"ACCOUNTING_VIEW");loadForCompany(id,actor);return audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc("FINANCIAL_TRANSACTION",id.toString());}
 private FinancialTransaction loadForCompany(Long id,User user){var t=transactionRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy giao dịch"));if(!isAdmin(user)&&(user.getCompany()==null||!user.getCompany().getId().equals(t.getCompany().getId())))throw new ResourceNotFoundException("Không tìm thấy giao dịch");return t;}
 private Long resolveCompanyId(User user,Long requested){if(isAdmin(user)&&requested!=null)return requested;if(user.getCompany()==null)throw new BadRequestException("COMPANY_REQUIRED: Chọn doanh nghiệp");if(requested!=null&&!requested.equals(user.getCompany().getId()))throw new org.springframework.security.access.AccessDeniedException("Bạn không thể truy cập công ty khác");return user.getCompany().getId();}
 private boolean isAdmin(User user){return user.getUserRoles().stream().anyMatch(a->a.getRole().isActive()&&"ADMIN".equals(a.getRole().getCode()));}
 private FinancialTransactionResponse toResponse(FinancialTransaction t){var c=t.getAccountingCategory();return new FinancialTransactionResponse(t.getId(),t.getTransactionType(),t.getAmount(),t.getTransactionDate(),t.getDescription(),c==null?null:c.getId(),c==null?null:c.getCategoryName(),t.getCompany().getId(),t.getDocument()==null?null:t.getDocument().getId(),t.getInvoice()==null?null:t.getInvoice().getId(),t.getPaymentMethod(),t.getStatus(),t.getCreatedBy()==null?null:t.getCreatedBy().getId(),t.getCreatedAt(),t.getUpdatedAt(),t.getPaymentConfirmation()==null?"UNVERIFIED":t.getPaymentConfirmation());}
}

package com.example.invoice.service;
import com.example.invoice.dto.accounting.*;
import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import com.example.invoice.exception.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
@Service @RequiredArgsConstructor
public class PaymentLinkService {
 private final DocumentRepository documents; private final InvoiceRepository invoices;
 private final FinancialTransactionRepository transactions; private final CompanyRepository companies;
 private final ClassificationRepository classifications;
 private final PaymentSchemaGuard schema;
 private final UserService users; private final ExtractedFieldRepository fields; private final FieldCorrectionRepository corrections;
 public record Link(Document document,Invoice invoice,BigDecimal total) {}
 public User authorize(Authentication auth,String permission) {
   User actor=users.loadCurrent(auth);
   if(!admin(actor) && (auth==null||auth.getAuthorities().stream().noneMatch(a->("PERMISSION_"+permission).equals(a.getAuthority()))))throw new AccessDeniedException("Bạn không có quyền thực hiện thao tác kế toán");
   return actor;
 }
 public boolean admin(User user){return user.getUserRoles().stream().anyMatch(a->a.getRole().isActive()&&"ADMIN".equals(a.getRole().getCode()));}
 public Long company(User actor,Long requested) {
   Long own=actor.getCompany()==null?null:actor.getCompany().getId();
   if(!admin(actor)&&requested!=null&&!requested.equals(own))throw new AccessDeniedException("Bạn không thể truy cập công ty khác");
   Long id=requested==null?own:requested;if(id==null||id<=0)throw new BadRequestException("COMPANY_REQUIRED: Chọn doanh nghiệp");return id;
 }
 public void lockCompany(Long company){companies.findByIdForUpdate(company).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy công ty"));}
 public Link resolve(Long company,Long documentId,Long invoiceId,boolean eligible) {
   if(documentId==null&&invoiceId==null)return new Link(null,null,null);
   Invoice invoice=invoiceId==null?null:invoices.findByIdAndDocumentCompanyId(invoiceId,company).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy hóa đơn trong doanh nghiệp"));
   Long canonical=invoice==null?documentId:invoice.getDocument().getId();
   if(documentId!=null&&!documentId.equals(canonical))throw new BadRequestException("INVOICE_DOCUMENT_MISMATCH: Hóa đơn không thuộc chứng từ đã chọn");
   Document document=documents.findByIdAndCompanyId(canonical,company).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy chứng từ trong doanh nghiệp"));
   if(invoice==null)invoice=invoices.findByDocumentId(document.getId()).orElse(null);
   if(eligible&&(document.getReviewStatus()!=ReviewStatus.APPROVED || (document.getStatus()!=DocumentStatus.COMPLETED&&document.getStatus()!=DocumentStatus.PROCESSED)))throw new BadRequestException("DOCUMENT_NOT_PAYABLE: Chỉ liên kết chứng từ đã được duyệt, xử lý hoàn tất");
   String currency=value(document.getId(),"currency");
   if(currency!=null&&!currency.isBlank()&&!"VND".equalsIgnoreCase(currency.trim()))throw new BadRequestException("UNSUPPORTED_CURRENCY: V1 chỉ hỗ trợ VND, không tự quy đổi ngoại tệ");
   BigDecimal total=invoice==null?decimal(value(document.getId(),"totalAmount")):invoice.getTotalAmount();
   if(total!=null&&(total.signum()<=0||total.scale()>2))total=null;
   if(eligible&&total==null)throw new BadRequestException("MISSING_PAYMENT_TOTAL: Hồ sơ chưa có tổng tiền hợp lệ để kiểm soát thanh toán");
   return new Link(document,invoice,total);
 }
 private BigDecimal decimal(String value){try{return value==null?null:new BigDecimal(value.trim());}catch(NumberFormatException e){return null;}}
 private String value(Long document,String name){
   return fields.findByDocumentId(document).stream().filter(f->name.equals(f.getFieldName())).sorted(Comparator.comparing(ExtractedField::getId,Comparator.nullsLast(Comparator.reverseOrder())))
     .map(f->corrections.findFirstByFieldIdOrderByCreatedAtDescIdDesc(f.getId()).map(FieldCorrection::getNewValue).orElse(f.getFieldValue())).filter(Objects::nonNull).findFirst().orElse(null);
 }
 public List<FinancialTransaction> linked(Long company,Link link){return link.document()==null?List.of():transactions.linked(company,link.document().getId(),link.invoice()==null?null:link.invoice().getId());}
 public PaymentSummary calculate(Long company,Link link,Long exclude) {
   var rows=linked(company,link);BigDecimal paid=rows.stream().filter(t->"CONFIRMED".equals(t.getPaymentConfirmation())&&!Objects.equals(t.getId(),exclude)).map(FinancialTransaction::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
   long unknown=rows.stream().filter(t->t.getPaymentConfirmation()==null).count();BigDecimal total=link.total();
   String status=total==null?null:paid.signum()==0?"UNPAID":paid.compareTo(total)<0?"PARTIALLY_PAID":paid.compareTo(total)==0?"PAID":"OVERPAID";
   String comparison=total==null?"UNKNOWN":paid.compareTo(total)<0?"UNDERPAID":paid.compareTo(total)==0?"MATCHED":"OVERPAID";
   return new PaymentSummary(link.document()==null?null:link.document().getId(),link.invoice()==null?null:link.invoice().getId(),total,paid,total==null?null:total.subtract(paid),unknown>0?null:status,status,unknown,"VND",comparison);
 }
 @Transactional(readOnly=true)
 public PaymentSummary summary(Long document,Long invoice,Long company,Authentication auth){User actor=authorize(auth,"ACCOUNTING_VIEW");Long target=company(actor,company);return calculate(target,resolve(target,document,invoice,false),null);}
 @Transactional(readOnly=true)
 public Page<PaymentLinkOption> search(String query,Long company,Long documentId,boolean invoiceOnly,int page,Authentication auth){
   User actor=authorize(auth,"ACCOUNTING_VIEW");Long target=company(actor,company);String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
   Specification<Document> spec=(root,c,b)->b.and(b.equal(root.get("company").get("id"),target),b.equal(root.get("reviewStatus"),ReviewStatus.APPROVED),root.get("status").in(DocumentStatus.COMPLETED,DocumentStatus.PROCESSED));
   if(documentId!=null)spec=spec.and((root,c,b)->b.equal(root.get("id"),documentId));
   if(invoiceOnly)spec=spec.and((root,c,b)->b.isNotNull(root.join("invoice").get("id")));
   if(!q.isBlank())spec=spec.and((root,c,b)->{var invoice=root.join("invoice",jakarta.persistence.criteria.JoinType.LEFT);String term="%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";return b.or(b.like(b.lower(root.get("originalFileName")),term,'\\'),b.like(b.lower(invoice.get("invoiceNumber")),term,'\\'));});
   return documents.findAll(spec,PageRequest.of(Math.max(0,page),20,Sort.by("id").descending())).map(d->{var i=invoices.findByDocumentId(d.getId()).orElse(null);BigDecimal total=i==null?decimal(value(d.getId(),"totalAmount")):i.getTotalAmount();var link=new Link(d,i,total);var direction=direction(link);var category=suggestedCategory(target,link,direction);return new PaymentLinkOption(d.getId(),i==null?null:i.getId(),d.getOriginalFileName(),i==null?null:i.getInvoiceNumber(),total,direction.type(),category==null?null:category.getId(),value(d.getId(),"currency")==null?"VND":value(d.getId(),"currency"));});
 }

 public record Direction(String type,String source) {}
 public Direction direction(Link link){
  // Invoice kind and AI assessment are not evidence of payment direction.
  if(link.document()==null||link.invoice()==null||link.document().getCompany()==null
      ||link.document().getReviewStatus()!=ReviewStatus.APPROVED)return new Direction(null,"UNKNOWN");
  String own=tax(link.document().getCompany().getTaxCode()),seller=tax(link.invoice().getSellerTaxCode()),buyer=tax(link.invoice().getBuyerTaxCode());
  if(own.isEmpty()||seller.isEmpty()||buyer.isEmpty())return new Direction(null,"UNKNOWN");
  boolean sale=own.equals(seller),purchase=own.equals(buyer);
  return sale!=purchase?new Direction(sale?"INCOME":"EXPENSE","INVOICE_COMPANY_TAX_CODE"):new Direction(null,"UNKNOWN");
 }
 private String tax(String code){String normalized=code==null?"":code.trim().replace("-","").replace(" ","");return normalized.matches("[0-9]{10}([0-9]{3})?")?normalized:"";}
 private AccountingCategory suggestedCategory(Long target,Link link,Direction direction){
  if(direction.type()==null)return null;
  return classifications.findFirstByDocumentIdOrderByCreatedAtDesc(link.document().getId()).map(Classification::getAccountingCategory)
    .filter(c->c.isActive()&&c.getCompany()!=null&&target.equals(c.getCompany().getId()))
    .filter(c->c.getCategoryType()!=null&&c.getCategoryType().name().equals(direction.type())).orElse(null);
 }
 @Transactional(readOnly=true)
 public PaymentPreview preview(Long document,Long invoice,Long company,Long editingId,Authentication auth){
  User actor=authorize(auth,editingId==null?"ACCOUNTING_CREATE":"ACCOUNTING_UPDATE");Long target=company(actor,company);
  if(document==null&&invoice==null)throw new BadRequestException("PAYMENT_LINK_REQUIRED: Chọn chứng từ hoặc hóa đơn.");
  FinancialTransaction editing=editingId==null?null:transactions.findById(editingId).filter(t->target.equals(t.getCompany().getId())).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy phiếu trong doanh nghiệp"));
  if(editing!=null&&!"CONFIRMED".equals(editing.getPaymentConfirmation()))throw new BadRequestException("PAYMENT_NOT_EDITABLE: Phiếu cũ hoặc đã hủy không được sửa qua preview.");
  var link=resolve(target,document,invoice,false);var summary=calculate(target,link,null);var available=calculate(target,link,editingId).remaining();var direction=direction(link);
  var category=suggestedCategory(target,link,direction);
  String code=null,message=null;
  if(link.document().getReviewStatus()!=ReviewStatus.APPROVED||(link.document().getStatus()!=DocumentStatus.COMPLETED&&link.document().getStatus()!=DocumentStatus.PROCESSED)){code="DOCUMENT_NOT_PAYABLE";message="Chỉ ghi nhận thanh toán cho hồ sơ đã duyệt, xử lý hoàn tất.";}
  else if(available==null){code="MISSING_PAYMENT_TOTAL";message="Hồ sơ chưa có tổng tiền hợp lệ.";}
  else if(available.signum()<=0){code="PAYMENT_FULLY_PAID";message="Hồ sơ không còn số tiền được thanh toán.";}
  else if(schema.blocks(link,linked(target,link),editingId)){code="PAYMENT_SCHEMA_REVIEW_REQUIRED";message="Ràng buộc hiện tại chặn thêm phiếu cho hồ sơ đã có giao dịch. Cần phê duyệt migration riêng.";}
  String description=link.invoice()!=null&&link.invoice().getInvoiceNumber()!=null?"Thanh toán hóa đơn "+link.invoice().getInvoiceNumber():"Thanh toán chứng từ #"+link.document().getId();
  var option=new PaymentLinkOption(link.document().getId(),link.invoice()==null?null:link.invoice().getId(),link.document().getOriginalFileName(),link.invoice()==null?null:link.invoice().getInvoiceNumber(),link.total(),direction.type(),category==null?null:category.getId(),"VND");
  return new PaymentPreview(option,summary,available,code==null,code,message,direction.source(),description,category==null||category.getCategoryType()==null?null:category.getCategoryType().name());
 }
}

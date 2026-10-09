package com.example.invoice.service;

import com.example.invoice.dto.reconciliation.ReconciliationResponse;
import com.example.invoice.entity.*;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ReconciliationService {
 private static final BigDecimal TOLERANCE=new BigDecimal("0.01");
 private final DocumentService documentService; private final InvoiceRepository invoiceRepository; private final ReconciliationRepository reconciliationRepository; private final ReconciliationBatchRepository batchRepository; private final UserService userService;
 @Transactional public List<ReconciliationResponse> run(Long documentId,Authentication authentication){
  Document document=documentService.load(documentId); Invoice invoice=invoiceRepository.findByDocumentId(documentId).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy hóa đơn của chứng từ"));
  List<Reconciliation> existing=reconciliationRepository.findByDocumentIdOrderByCreatedAtDesc(documentId); Map<String,Reconciliation> byType=new LinkedHashMap<>(); existing.forEach(r->byType.putIfAbsent(type(r),r));
  ReconciliationBatch batch=existing.isEmpty()?batch(document,invoice,authentication):existing.getFirst().getReconciliationBatch();
  return checks(invoice).stream().map(c->save(byType.get(c.type()),batch,document,invoice,c)).map(this::response).toList();
 }
 @Transactional public ReconciliationResponse decide(Long documentId,Long id,String status,String comment,Authentication authentication){Reconciliation r=reconciliationRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy kết quả đối soát"));if(!r.getDocument().getId().equals(documentId))throw new ResourceNotFoundException("Không tìm thấy kết quả đối soát");documentService.load(documentId);if(!"MATCHED".equals(status)&&!"NEED_REVIEW".equals(status))throw new IllegalArgumentException("Quyết định đối soát không được hỗ trợ");String checkType=type(r);r.setStatus(status);r.setComment(checkType+": "+(comment==null?"Manual decision":comment));r.setReviewedBy(userService.loadCurrent(authentication));r.setReviewedAt(java.time.LocalDateTime.now());return response(r);}
 public List<ReconciliationResponse> find(Long documentId){documentService.load(documentId);return reconciliationRepository.findByDocumentIdOrderByCreatedAtDesc(documentId).stream().map(this::response).toList();}
 private List<Check> checks(Invoice invoice){
  List<Check> result=new ArrayList<>(); List<InvoiceItem> items=invoice.getItems()==null?List.of():invoice.getItems();
  if(items.isEmpty())result.add(new Check("ITEMS_PRESENT",BigDecimal.ZERO,BigDecimal.ZERO,true,"Không có dòng hàng hóa để đối soát"));
  for(int i=0;i<items.size();i++){InvoiceItem item=items.get(i);BigDecimal amount=multiply(item.getQuantity(),item.getUnitPrice());result.add(check("ITEM_"+(i+1)+"_AMOUNT",item.getAmount(),amount,item.getQuantity()==null||item.getUnitPrice()==null||item.getAmount()==null,"Thành tiền dòng = số lượng × đơn giá"));BigDecimal rate=rate(item.getTaxRate());BigDecimal vat=item.getAmount()==null||rate==null?null:round(item.getAmount().multiply(rate));result.add(check("ITEM_"+(i+1)+"_VAT",item.getTaxAmount(),vat,item.getAmount()==null||item.getTaxRate()==null||item.getTaxAmount()==null,"VAT dòng theo thuế suất"));}
  BigDecimal itemSum=sum(items.stream().map(InvoiceItem::getAmount).toList()),vatSum=sum(items.stream().map(InvoiceItem::getTaxAmount).toList());
  result.add(check("SUBTOTAL",invoice.getSubtotal(),itemSum,invoice.getSubtotal()==null||items.isEmpty()||items.stream().anyMatch(i->i.getAmount()==null),"Tổng dòng so với tổng trước thuế"));
  result.add(check("VAT_TOTAL",invoice.getVatAmount(),vatSum,invoice.getVatAmount()==null||items.isEmpty()||items.stream().anyMatch(i->i.getTaxAmount()==null),"Tổng VAT dòng, hỗ trợ nhiều thuế suất"));
  BigDecimal total=invoice.getSubtotal()==null||invoice.getVatAmount()==null?null:round(invoice.getSubtotal().add(invoice.getVatAmount()));result.add(check("TOTAL",invoice.getTotalAmount(),total,invoice.getSubtotal()==null||invoice.getVatAmount()==null||invoice.getTotalAmount()==null,"Tổng trước thuế + VAT so với thanh toán"));return result;
 }
 private Check check(String type,BigDecimal declared,BigDecimal calculated,boolean missing,String message){return new Check(type,value(declared),value(calculated),missing,message);}
 private Reconciliation save(Reconciliation current,ReconciliationBatch batch,Document document,Invoice invoice,Check check){if(current!=null&&current.getReviewedAt()!=null)return current;Reconciliation r=current==null?new Reconciliation():current;r.setReconciliationBatch(batch);r.setDocument(document);r.setInvoice(invoice);r.setExpectedAmount(round(check.declared()));r.setActualAmount(round(check.calculated()));r.setDifferenceAmount(round(check.calculated().subtract(check.declared())));r.setStatus(!check.missing()&&r.getDifferenceAmount().abs().compareTo(TOLERANCE)<=0?"MATCHED":"NEED_REVIEW");r.setComment(check.type()+": "+(check.missing()?"MISSING_DATA; ":"")+check.message()+"; rounding=HALF_UP scale=2");return reconciliationRepository.save(r);}
 private ReconciliationBatch batch(Document d,Invoice i,Authentication a){User u=userService.loadCurrent(a);ReconciliationBatch b=new ReconciliationBatch();b.setCompany(d.getCompany());b.setBatchCode("DOC-"+d.getId()+"-"+System.currentTimeMillis());b.setPeriodFrom(i.getInvoiceDate()==null?LocalDate.now():i.getInvoiceDate());b.setPeriodTo(b.getPeriodFrom());b.setStatus("COMPLETED");b.setCreatedBy(u);return batchRepository.save(b);}
 private String type(Reconciliation r){return r.getComment()==null?"UNKNOWN_"+r.getId():r.getComment().split(":",2)[0];}
 private ReconciliationResponse response(Reconciliation r){return new ReconciliationResponse(r.getId(),type(r),r.getExpectedAmount(),r.getActualAmount(),r.getDifferenceAmount(),r.getStatus(),r.getComment());}
 private BigDecimal rate(BigDecimal v){return v==null?null:v.abs().compareTo(BigDecimal.ONE)<=0?v:v.movePointLeft(2);} private BigDecimal multiply(BigDecimal a,BigDecimal b){return a==null||b==null?null:round(a.multiply(b));} private BigDecimal sum(List<BigDecimal> v){return round(v.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add));} private BigDecimal value(BigDecimal v){return v==null?BigDecimal.ZERO:v;} private BigDecimal round(BigDecimal v){return v.setScale(2,RoundingMode.HALF_UP);} private record Check(String type,BigDecimal declared,BigDecimal calculated,boolean missing,String message){}
}

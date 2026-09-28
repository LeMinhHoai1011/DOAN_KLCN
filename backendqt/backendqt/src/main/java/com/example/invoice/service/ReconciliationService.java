package com.example.invoice.service;
import com.example.invoice.dto.reconciliation.ReconciliationResponse;
import com.example.invoice.entity.*;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ReconciliationService {
 private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
 private final DocumentService documentService; private final InvoiceRepository invoiceRepository; private final ReconciliationRepository reconciliationRepository; private final ReconciliationBatchRepository batchRepository; private final UserService userService;
 @Transactional public List<ReconciliationResponse> run(Long documentId, Authentication authentication) {
  Document document=documentService.load(documentId); Invoice invoice=invoiceRepository.findByDocumentId(documentId).orElseThrow(()->new ResourceNotFoundException("Invoice not found for document"));
  User user=userService.loadCurrent(authentication); ReconciliationBatch batch=new ReconciliationBatch(); batch.setCompany(document.getCompany()); batch.setBatchCode("DOC-"+documentId+"-"+System.currentTimeMillis()); batch.setPeriodFrom(invoice.getInvoiceDate()==null?LocalDate.now():invoice.getInvoiceDate()); batch.setPeriodTo(batch.getPeriodFrom()); batch.setStatus("COMPLETED"); batch.setCreatedBy(user); batch=batchRepository.save(batch);
  BigDecimal itemTotal=invoice.getItems().stream().map(item -> value(item.getQuantity()).multiply(value(item.getUnitPrice()))).reduce(BigDecimal.ZERO, BigDecimal::add);
  BigDecimal declaredItems=invoice.getItems().stream().map(item -> value(item.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
  return List.of(save(batch,document,invoice,itemTotal,declaredItems,"ITEMS", "Sum(quantity × unit price) compared with line amounts"), save(batch,document,invoice,declaredItems,value(invoice.getSubtotal()),"SUBTOTAL", "Sum(line amounts) compared with subtotal"), save(batch,document,invoice,value(invoice.getSubtotal()).add(value(invoice.getVatAmount())),value(invoice.getTotalAmount()),"TOTAL", "Subtotal + VAT compared with total payment")).stream().map(this::response).toList();
 }
 @Transactional public ReconciliationResponse decide(Long documentId,Long id,String status,String comment,Authentication authentication){ Reconciliation r=reconciliationRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Reconciliation not found")); if(!r.getDocument().getId().equals(documentId)) throw new ResourceNotFoundException("Reconciliation not found"); documentService.load(documentId); if(!"MATCHED".equals(status)&&!"NEED_REVIEW".equals(status)) throw new IllegalArgumentException("Unsupported reconciliation decision"); r.setStatus(status); r.setComment(comment); r.setReviewedBy(userService.loadCurrent(authentication)); r.setReviewedAt(java.time.LocalDateTime.now()); return response(r); }
 public List<ReconciliationResponse> find(Long documentId){ documentService.load(documentId); return reconciliationRepository.findByDocumentIdOrderByCreatedAtDesc(documentId).stream().map(this::response).toList(); }
 private Reconciliation save(ReconciliationBatch b,Document d,Invoice i,BigDecimal expected,BigDecimal actual,String type,String message){ Reconciliation r=new Reconciliation();r.setReconciliationBatch(b);r.setDocument(d);r.setInvoice(i);r.setExpectedAmount(expected);r.setActualAmount(actual);r.setDifferenceAmount(actual.subtract(expected));r.setStatus(expected.subtract(actual).abs().compareTo(TOLERANCE)<=0?"MATCHED":"NEED_REVIEW");r.setComment(type+": "+message);return reconciliationRepository.save(r); }
 private ReconciliationResponse response(Reconciliation r){return new ReconciliationResponse(r.getId(),r.getComment().split(":",2)[0],r.getExpectedAmount(),r.getActualAmount(),r.getDifferenceAmount(),r.getStatus(),r.getComment());}
 private BigDecimal value(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
}

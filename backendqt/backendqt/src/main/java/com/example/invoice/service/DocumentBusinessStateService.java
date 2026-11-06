package com.example.invoice.service;
import com.example.invoice.dto.document.DocumentBusinessState;
import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor
public class DocumentBusinessStateService {
  @org.springframework.beans.factory.annotation.Autowired private PaymentLinkService payments;
  private final DocumentService documents;
  private final ClassificationRepository classifications;
  private final ReconciliationRepository reconciliations;
  private final DocumentReviewRepository reviews;
  @Transactional(readOnly = true)
  public DocumentBusinessState get(Long id) {
    Document d = documents.load(id);
    Classification c = classifications.findFirstByDocumentIdOrderByCreatedAtDesc(id).orElse(null);
    var events = reviews.findByDocumentId(id).stream().sorted(Comparator.comparing(DocumentReview::getId, Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
    String review = d.getReviewStatus() == null ? "PENDING" : d.getReviewStatus().name();
    if ("CORRECTED".equals(review)) review = "PENDING";
    if (d.getReviewStatus() == ReviewStatus.PENDING && !events.isEmpty()) {
      String note = events.getLast().getReviewNote();
      if (note != null && (note.equals("START_REVIEW") || note.startsWith("START_REVIEW:"))) review = "IN_REVIEW";
    }
    var reconciliation = ReconciliationState.of(d, reconciliations.findByDocumentIdOrderByCreatedAtDesc(id));
    String processing = d.getStatus() == DocumentStatus.COMPLETED ? "PROCESSED" : d.getStatus().name();
    String classification = c == null || c.getStatus() == ClassificationStatus.PENDING ? "PENDING" : "COMPLETED";
    String payment=null;String paymentReason="Chưa có dữ liệu xác nhận thanh toán; giao dịch ACTIVE và phê duyệt không chứng minh đã thanh toán";
    if(payments!=null&&d.getCompany()!=null)try{var summary=payments.calculate(d.getCompany().getId(),payments.resolve(d.getCompany().getId(),id,null,false),null);payment=summary.paymentStatus();paymentReason=summary.unverifiedCount()>0?"Có "+summary.unverifiedCount()+" giao dịch cũ chưa xác minh, cần đối soát thủ công":summary.totalAmount()==null?"Chưa có tổng tiền để xác định thanh toán":"Chỉ tính khoản tiền đã được xác nhận rõ ràng";}catch(com.example.invoice.exception.BadRequestException unsupported){paymentReason=unsupported.getMessage();}
    return new DocumentBusinessState(processing, classification, reconciliation.overall(), reconciliation.stale(), review, payment,paymentReason,
        events.stream().map(r -> new DocumentBusinessState.Event(r.getId(), r.getReviewStatus().name(), r.getReviewNote(),
            r.getReviewer() == null ? null : r.getReviewer().getId(), r.getReviewedAt())).toList());
  }
}

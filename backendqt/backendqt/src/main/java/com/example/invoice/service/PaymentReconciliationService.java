package com.example.invoice.service;
import com.example.invoice.dto.accounting.*;
import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import java.util.*;
@Service @RequiredArgsConstructor
public class PaymentReconciliationService {
 private final PaymentLinkService payments;private final AuditLogRepository audit;private final ObjectMapper json;
 @Transactional
 public PaymentSummary run(Long document,Long invoice,Long company,Authentication auth){
   User actor=payments.authorize(auth,"ACCOUNTING_UPDATE");Long target=payments.company(actor,company);payments.lockCompany(target);
   var link=payments.resolve(target,document,invoice,false);if(link.document()==null)throw new com.example.invoice.exception.BadRequestException("Chọn chứng từ hoặc hóa đơn để đối soát");
   var summary=payments.calculate(target,link,null);try{var e=new AuditLog();e.setUser(actor);e.setCompany(link.document().getCompany());e.setAction("PAYMENT_RECONCILE");e.setEntityType("PAYMENT_RECONCILIATION");e.setEntityId(link.document().getId().toString());
   e.setNewValue(json.writeValueAsString(Map.of("summary",summary,"transactions",payments.linked(target,link).stream().map(t->Map.of("id",t.getId(),"amount",t.getAmount(),"verification",t.getPaymentConfirmation()==null?"UNVERIFIED":t.getPaymentConfirmation(),"updatedAt",t.getUpdatedAt().toString())).toList())));audit.save(e);return summary;
   }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Không lưu được kết quả đối soát",e);}
 }
 @Transactional(readOnly=true)
 public List<PaymentHistoryEvent> history(Long document,Long invoice,Long company,Authentication auth){var actor=payments.authorize(auth,"ACCOUNTING_VIEW");Long target=payments.company(actor,company);var link=payments.resolve(target,document,invoice,false);if(link.document()==null)return List.of();return audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc("PAYMENT_RECONCILIATION",link.document().getId().toString()).stream().map(e->new PaymentHistoryEvent(e.getId(),e.getAction(),e.getUser()==null?null:e.getUser().getId(),e.getCreatedAt(),e.getOldValue(),e.getNewValue())).toList();}
}

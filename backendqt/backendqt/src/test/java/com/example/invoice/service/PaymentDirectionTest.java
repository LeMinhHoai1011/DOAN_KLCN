package com.example.invoice.service;

import com.example.invoice.entity.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaymentDirectionTest {
 private final PaymentLinkService service=new PaymentLinkService(null,null,null,null,null,null,null,null,null);
 private PaymentLinkService.Link link(String kind,String own,String seller,String buyer){
  var company=new Company();company.setTaxCode(own);
  var document=new Document();document.setCompany(company);document.setDocumentType(kind);
  document.setReviewStatus(ReviewStatus.APPROVED);document.setTransactionAssessmentType("EXPENSE");
  var invoice=new Invoice();invoice.setDocument(document);invoice.setSellerTaxCode(seller);invoice.setBuyerTaxCode(buyer);
  return new PaymentLinkService.Link(document,invoice,BigDecimal.TEN);
 }
 @Test void vatAndSalesKindsNeverDecideDirection(){
  for(String kind:new String[]{"VAT_INVOICE","SALES_INVOICE"}){
   assertEquals("INCOME",service.direction(link(kind,"0312345678","0312345678","0399999999")).type());
   assertEquals("EXPENSE",service.direction(link(kind,"0312345678","0399999999","0312345678")).type());
  }
 }
 @Test void missingInvalidUnmatchedOrContradictoryTaxNeverFallsBackToAi(){
  for(String[] codes:new String[][]{{null,"0312345678","0399999999"},{"0312345678",null,"0312345678"},
      {"0312345678","0312345678",null},{"0312345678","0399999999","0388888888"},
      {"0312345678","0312345678","0312345678"},{"0312345678","bad","0312345678"}}){
   var direction=service.direction(link("VAT_INVOICE",codes[0],codes[1],codes[2]));
   assertNull(direction.type());assertEquals("UNKNOWN",direction.source());
  }
 }
 @Test void unapprovedInvoiceAndNonInvoiceNeedAccountantConfirmation(){
  var link=link("VAT_INVOICE","0312345678","0312345678","0399999999");
  link.document().setReviewStatus(ReviewStatus.PENDING);assertNull(service.direction(link).type());
  assertNull(service.direction(new PaymentLinkService.Link(link.document(),null,BigDecimal.TEN)).type());
 }
 @Test void normalizesBranchTaxCodeWithoutGuessing(){
  assertEquals("INCOME",service.direction(link("SALES_INVOICE","0312345678-001","0312345678001","0399999999")).type());
 }
}

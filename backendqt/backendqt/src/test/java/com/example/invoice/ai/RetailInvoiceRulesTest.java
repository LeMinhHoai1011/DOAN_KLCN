package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.invoice.RetailInvoiceDetails;
import com.example.invoice.dto.invoice.RetailInvoiceDetails.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RetailInvoiceRulesTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static Adjustment absent() { return new Adjustment(null,Treatment.NOT_PRESENT); }
    private static RetailInvoiceDetails details(LineAmountBasis basis,List<LineDiscount> discounts,
            Adjustment bill,Adjustment tax,Adjustment fees) {
        return new RetailInvoiceDetails("14:30",basis,discounts,bill,tax,fees,null,null);
    }
    @Test void simpleBillHasNoFabricatedVatAndCanLackBuyerTaxCodesAndNumber() {
        var details=details(LineAmountBasis.BEFORE_DISCOUNT,List.of(),absent(),absent(),absent());
        var item=new AiDocumentResult.AiInvoiceItemExtraction("Paper",n("2"),null,n("50"),null,null,n("100"));
        var invoice=new AiDocumentResult.AiInvoiceExtraction(null,null,"2026-10-23","Shop",null,null,null,null,null,
                null,null,n("100"),List.of(item)).withRetailDetails(details);
        var result=new AiDocumentResult("unit","unit","RETAIL_INVOICE",n("0.99"),"Bill",invoice,List.of(),
                List.of("DOCUMENT_TYPE_REASON: Bill cửa hàng có bảng hàng và tổng tiền"),"{}",1);
        var validated=new AiDocumentResultValidator().validate(result,Set.of("RETAIL_INVOICE"));
        assertFalse(validated.requiresReview(n("0.8")),validated.warnings().toString());
        assertNull(validated.result().invoice().vatAmount()); assertNull(validated.result().invoice().buyerTaxCode());
        assertThrows(AiDocumentResultValidator.UnresolvedDocumentTypeException.class,
                () -> new AiDocumentResultValidator().validate(result,Set.of("VAT_INVOICE","RECEIPT")));
    }
    @Test void lineAndBillDiscountAndSeparateTaxFeesAreAppliedExactlyOnce() {
        var detail=details(LineAmountBasis.AFTER_DISCOUNT,List.of(new LineDiscount(1,n("20"))),
                new Adjustment(n("10"),Treatment.SEPARATE),new Adjustment(n("17"),Treatment.SEPARATE),
                new Adjustment(n("5"),Treatment.SEPARATE));
        var checks=RetailInvoiceRules.evaluate(detail,List.of(new RetailInvoiceRules.Row(n("2"),n("100"),n("180"))),n("192"),n("17"),false);
        assertTrue(checks.stream().allMatch(c -> !c.missing() && c.declared().compareTo(c.calculated())==0));
        var before=details(LineAmountBasis.BEFORE_DISCOUNT,List.of(new LineDiscount(1,n("20"))),
                detail.billDiscount(),detail.tax(),detail.fees());
        var gross=RetailInvoiceRules.evaluate(before,List.of(new RetailInvoiceRules.Row(n("2"),n("100"),n("200"))),n("192"),n("17"),false);
        assertTrue(gross.stream().allMatch(c -> !c.missing() && c.declared().compareTo(c.calculated())==0));
    }
    @Test void includedAmountsAreNotAppliedTwiceAndCashIsOnlyAnArithmeticCheck() {
        var detail=new RetailInvoiceDetails("14:30:01",LineAmountBasis.AFTER_DISCOUNT,List.of(new LineDiscount(1,n("20"))),
                new Adjustment(n("5"),Treatment.INCLUDED),new Adjustment(n("10"),Treatment.INCLUDED),
                new Adjustment(n("2"),Treatment.INCLUDED),n("200"),n("20"));
        var checks=RetailInvoiceRules.evaluate(detail,List.of(new RetailInvoiceRules.Row(n("2"),n("100"),n("180"))),n("180"),n("10"),false);
        assertTrue(checks.stream().allMatch(c -> !c.missing() && c.declared().compareTo(c.calculated())==0));
        assertTrue(checks.stream().anyMatch(c -> "RETAIL_CASH_CHANGE".equals(c.type())));
    }
    @Test void unknownStaleMissingOrConflictingEvidenceNeverProducesFalseMatchedTotal() {
        var simple=details(LineAmountBasis.BEFORE_DISCOUNT,List.of(),absent(),absent(),absent());
        var rows=List.of(new RetailInvoiceRules.Row(n("1"),n("100"),n("100")));
        assertTrue(RetailInvoiceRules.evaluate(null,rows,n("100"),null,false).getFirst().missing());
        assertTrue(RetailInvoiceRules.evaluate(simple,rows,n("100"),null,true).getFirst().missing());
        assertTrue(RetailInvoiceRules.evaluate(details(LineAmountBasis.UNKNOWN,null,null,null,null),rows,n("100"),null,false).getFirst().missing());
        assertFalse(RetailInvoiceRules.problems(details(LineAmountBasis.BEFORE_DISCOUNT,
                List.of(new LineDiscount(1,n("1")),new LineDiscount(1,n("2"))),absent(),absent(),absent()),1).isEmpty());
        assertFalse(RetailInvoiceRules.problems(details(LineAmountBasis.BEFORE_DISCOUNT,List.of(),
                new Adjustment(n("10"),Treatment.NOT_PRESENT),absent(),absent()),1).isEmpty());
        var missing=RetailInvoiceRules.evaluate(simple,List.of(new RetailInvoiceRules.Row(null,n("100"),null)),n("100"),null,false);
        assertTrue(missing.stream().allMatch(RetailInvoiceRules.Check::missing));
        assertTrue(RetailInvoiceRules.evaluate(simple,List.of(),n("100"),null,false).getFirst().missing());
    }
    @Test void wrongAmountsRemainWrongAndNegativeValuesOrTimeNeedReview() {
        var simple=details(LineAmountBasis.BEFORE_DISCOUNT,List.of(),absent(),absent(),absent());
        var checks=RetailInvoiceRules.evaluate(simple,List.of(new RetailInvoiceRules.Row(n("2"),n("50"),n("90"))),n("100"),null,false);
        assertTrue(checks.stream().anyMatch(c -> !c.missing() && c.declared().compareTo(c.calculated())!=0));
        assertTrue(RetailInvoiceRules.evaluate(simple,List.of(new RetailInvoiceRules.Row(n("-1"),n("50"),n("-50"))),n("-50"),null,false).getFirst().missing());
        assertFalse(RetailInvoiceRules.problems(new RetailInvoiceDetails("25:99",LineAmountBasis.BEFORE_DISCOUNT,
                List.of(),absent(),absent(),absent(),null,null),1).isEmpty());
    }
    @Test void parserKeepsTypedRetailEvidenceAndLegacyReceiptDoesNotBecomeInvoice() {
        String content="""
            {"documentType":"RETAIL_INVOICE","invoice":{"sellerName":"Shop","totalAmount":100,
            "items":[{"productName":"A","quantity":1,"unitPrice":100,"amount":100}],
            "retailDetails":{"lineAmountBasis":"BEFORE_DISCOUNT","lineDiscounts":[],
            "billDiscount":{"treatment":"NOT_PRESENT"},"tax":{"treatment":"NOT_PRESENT"},"fees":{"treatment":"NOT_PRESENT"}}}}
            """;
        var parsed=AiDocumentResultParser.parse("unit","unit",content,content,1);
        assertNotNull(parsed.invoice().retailDetails()); assertNull(parsed.invoice().retailDetails().tax().amount());
        var receipt=new AiDocumentResult("unit","unit","RECEIPT",n("0.99"),"Receipt",parsed.invoice(),List.of(),List.of(),"{}",1);
        assertNull(new AiDocumentResultValidator().validate(receipt,Set.of("RECEIPT")).result().invoice());
    }
}

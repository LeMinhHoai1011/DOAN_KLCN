package com.example.invoice.service;

import com.example.invoice.ai.RetailInvoiceRules;
import com.example.invoice.dto.invoice.RetailInvoiceDetails;
import com.example.invoice.entity.*;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Typed retail evidence in the existing text field store; no schema migration or automatic provisioning. */
@Service @RequiredArgsConstructor
public class RetailInvoiceDetailsService {
    public static final String FIELD = "retailInvoiceDetails";
    private final ExtractedFieldRepository fields;
    private final InvoiceRepository invoices;
    private final ObjectMapper json;
    public record Stored(RetailInvoiceDetails details, String pricingFingerprint) {}
    public record View(RetailInvoiceDetails details, boolean stale) {}

    public static boolean isRetail(Document document) {
        if (document==null) return false;
        String type=document.getType()==null ? document.getDocumentType() : document.getType().getCode();
        return "RETAIL_INVOICE".equals(type);
    }
    @Transactional(readOnly=true)
    public View read(Invoice invoice) {
        var records=records(invoice.getDocument().getId());
        if (records.isEmpty()) return new View(null,false);
        if (records.size()!=1) return new View(null,true);
        try {
            var stored=json.readValue(records.getFirst().getFieldValue(),Stored.class);
            return new View(stored.details(),!Objects.equals(stored.pricingFingerprint(),fingerprint(invoice)));
        } catch (Exception invalid) { return new View(null,true); }
    }
    @Transactional(readOnly=true)
    public View readForDocument(Long documentId) {
        // Internal use after the caller has scoped the document, e.g. list export.
        return invoices.findByDocumentId(documentId).map(this::read).orElse(new View(null,false));
    }
    @Transactional
    public void storeAi(Invoice invoice,RetailInvoiceDetails details) {
        if (!isRetail(invoice.getDocument()) || !invoice.isAiGenerated()) return;
        var records=records(invoice.getDocument().getId());
        if (records.stream().anyMatch(field -> "RETAIL_MANUAL".equals(field.getSource()))) return;
        store(invoice,details,"RETAIL_AI");
    }
    @Transactional
    public View storeManual(Invoice invoice,RetailInvoiceDetails details) {
        if (!isRetail(invoice.getDocument())) throw new BadRequestException("RETAIL_INVOICE_REQUIRED: Chỉ áp dụng cho hóa đơn bán lẻ");
        var problems=RetailInvoiceRules.problems(details,invoice.getItems().size());
        if (!problems.isEmpty()) throw new BadRequestException(String.join("; ",problems));
        store(invoice,details,"RETAIL_MANUAL");
        invoice.setAiGenerated(false); invoice.setUpdatedAt(LocalDateTime.now()); invoices.flush();
        return read(invoice);
    }
    private List<ExtractedField> records(Long documentId) {
        return fields.findByDocumentId(documentId).stream().filter(field -> FIELD.equals(field.getFieldName())).toList();
    }
    private void store(Invoice invoice,RetailInvoiceDetails details,String source) {
        var records=records(invoice.getDocument().getId());
        if (records.size()>1) throw new BadRequestException("RETAIL_METADATA_CONFLICT: Có nhiều bản căn cứ bán lẻ; cần kiểm tra lịch sử");
        var field=records.isEmpty() ? new ExtractedField() : records.getFirst();
        field.setDocument(invoice.getDocument()); field.setFieldName(FIELD); field.setSource(source);
        try { field.setFieldValue(json.writeValueAsString(new Stored(details,fingerprint(invoice)))); }
        catch (Exception error) { throw new IllegalStateException("Không thể lưu căn cứ hóa đơn bán lẻ",error); }
        fields.saveAndFlush(field);
    }
    private String fingerprint(Invoice invoice) {
        try {
            var snapshot=new ArrayList<Object>();
            snapshot.add(decimal(invoice.getTotalAmount())); snapshot.add(decimal(invoice.getVatAmount()));
            for (var item : invoice.getItems()) snapshot.add(Arrays.asList(item.getProductName(),item.getUnit(),
                    decimal(item.getQuantity()),decimal(item.getUnitPrice()),decimal(item.getAmount()),
                    decimal(item.getTaxRate()),decimal(item.getTaxAmount())));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(snapshot)));
        } catch (Exception error) { throw new IllegalStateException("Không thể kiểm tra phiên bản căn cứ bán lẻ",error); }
    }
    private String decimal(BigDecimal value) { return value==null ? null : value.stripTrailingZeros().toPlainString(); }
}

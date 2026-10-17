package com.example.invoice.service;

import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Append-only snapshots in the existing audit table, atomic with the calling business transaction. */
@Service @RequiredArgsConstructor
@Transactional(propagation=Propagation.MANDATORY)
public class DocumentAuditService {
    public static final String TYPE="DOCUMENT_SNAPSHOT_V1";
    private final AuditLogRepository audit;
    private final DocumentVersionRepository versions;
    private final InvoiceRepository invoices;
    private final OCRResultRepository ocr;
    private final ClassificationRepository classifications;
    private final ExtractedFieldRepository fields;
    private final FieldCorrectionRepository corrections;
    private final ReconciliationRepository reconciliations;
    private final ProcessingJobRepository jobs;
    private final UserService users;
    private final ObjectMapper json;
    private final EntityManager em;

    public String capture(Document document) {
        em.flush(); // Include generated IDs/cascades; never commit separately from the mutation.
        Map<String,Object> value=new LinkedHashMap<>();value.put("schemaVersion",1);
        value.put("document",flat(document,"id","originalFileName","fileType","fileSize","documentType","status","reviewStatus","createdAt","updatedAt"));
        value.put("fileVersionId",versions.findByDocumentId(document.getId()).stream()
                .filter(v->Objects.equals(v.getObjectKey(),document.getFilePath()))
                .max(Comparator.comparing(DocumentVersion::getVersionNumber)).map(DocumentVersion::getId).orElse(null));
        value.put("invoice",invoices.findByDocumentId(document.getId()).map(i->{
            var m=flat(i,"id","invoiceNumber","invoiceSeries","invoiceDate","sellerName","sellerTaxCode","sellerAddress","sellerPhone","buyerName","buyerTaxCode","buyerAddress","subtotal","vatAmount","totalAmount","paymentMethod","amountInWords","taxAuthorityCode","signDate","aiGenerated");
            m.put("items",i.getItems().stream().map(item->flat(item,"id","productName","quantity","unit","unitPrice","taxRate","taxAmount","amount")).toList());return m;
        }).orElse(null));
        value.put("ocr",ocr.findFirstByDocumentIdOrderByProcessedAtDesc(document.getId()).map(o->flat(o,"id","rawText","layoutJson","ocrEngine","modelVersion","language","sourceType","confidence","processingTime","status","processedAt")).orElse(null));
        value.put("classification",classifications.findFirstByDocumentIdOrderByCreatedAtDesc(document.getId())
                .map(c->flat(c,"id","modelName","modelVersion","predictedLabel","category","accountingAccount","confidence","reason","status","aiGenerated")).orElse(null));
        value.put("fields",fields.findByDocumentId(document.getId()).stream().sorted(Comparator.comparing(ExtractedField::getId)).map(f->{
            var m=flat(f,"id","fieldName","fieldValue","source","confidence");
            var correction=corrections.findFirstByFieldIdOrderByCreatedAtDescIdDesc(f.getId()).orElse(null);
            m.put("effectiveValue",correction==null?f.getFieldValue():correction.getNewValue());
            m.put("correctionId",correction==null?null:correction.getId());return m;
        }).toList());
        value.put("reconciliations",reconciliations.findByDocumentIdOrderByCreatedAtDesc(document.getId()).stream()
                .map(r->{var m=flat(r,"id","status","expectedAmount","actualAmount","differenceAmount","reviewedAt");m.put("checkType",r.getComment()==null?null:r.getComment().split(":",2)[0]);return m;}).toList());
        value.put("latestJob",jobs.findFirstByDocumentIdAndJobTypeOrderByIdDesc(document.getId(),"OCR_AI_PIPELINE")
                .map(j->flat(j,"id","status","startedAt","completedAt")).orElse(null));
        try {return json.writeValueAsString(value);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Không thể lưu snapshot chứng từ",e);}
    }
    public void append(Document document,String action,String before) {
        String after=capture(document);
        save(document,action,before,after);
    }
    public void appendAi(Document document,String action,String before,com.example.invoice.dto.ai.AiDocumentResult proposal) {
        try {
            var after=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(capture(document));
            var structured=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(proposal);
            structured.remove("rawResponse"); // Keep structured business evidence, never expose the raw provider payload.
            after.set("aiProposal",exactDecimals(structured));save(document,action,before,json.writeValueAsString(after));
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Không thể lưu đề xuất AI của lượt xử lý",e);}
    }
    private void save(Document document,String action,String before,String after) {
        var event=new AuditLog();event.setEntityType(TYPE);event.setEntityId(document.getId().toString());
        event.setCompany(document.getCompany());event.setAction(action);event.setOldValue(before);event.setNewValue(after);
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null&&auth.isAuthenticated()&&!"anonymousUser".equals(auth.getPrincipal()))event.setUser(users.loadCurrent(auth));
        audit.save(event);
    }
    private Map<String,Object> flat(Object bean,String... names) {
        var values=new LinkedHashMap<String,Object>();var properties=new BeanWrapperImpl(bean);
        for(String name:names){Object value=properties.getPropertyValue(name);values.put(name,value instanceof java.math.BigDecimal decimal?decimal.toPlainString():value);}return values;
    }
    static com.fasterxml.jackson.databind.JsonNode exactDecimals(com.fasterxml.jackson.databind.JsonNode node) {
        if(node.isFloatingPointNumber())return com.fasterxml.jackson.databind.node.TextNode.valueOf(node.decimalValue().toPlainString());
        if(node.isObject()){var object=(com.fasterxml.jackson.databind.node.ObjectNode)node;var names=new ArrayList<String>();object.fieldNames().forEachRemaining(names::add);for(String name:names)object.set(name,exactDecimals(object.get(name)));}
        else if(node.isArray()){var array=(com.fasterxml.jackson.databind.node.ArrayNode)node;for(int i=0;i<array.size();i++)array.set(i,exactDecimals(array.get(i)));}
        return node;
    }
}

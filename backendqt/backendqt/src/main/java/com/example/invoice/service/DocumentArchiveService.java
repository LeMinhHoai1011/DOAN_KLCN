package com.example.invoice.service;

import com.example.invoice.dto.document.DocumentArchive.*;
import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import com.example.invoice.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.*;
import io.minio.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
public class DocumentArchiveService {
    private final DocumentService documents;
    private final DocumentVersionRepository versions;
    private final AuditLogRepository audit;
    private final DocumentReviewRepository reviews;
    private final FieldCorrectionRepository corrections;
    private final ExtractedFieldRepository fields;
    private final ProcessingLogRepository logs;
    private final InvoiceRepository invoices;
    private final MinioClient minio;
    private final ObjectMapper json;
    @Value("${app.minio.bucket:invoice-files}") private String bucket;

    public List<FileVersion> versions(Long id) {
        var document=documents.load(id);
        return versions.findByDocumentId(id).stream().sorted(Comparator.comparing(DocumentVersion::getVersionNumber).reversed())
                .map(v->file(v,document)).toList();
    }
    public Storage storage(Long id) {
        var document=documents.load(id);
        var version=versions.findByDocumentId(id).stream().filter(v->Objects.equals(v.getObjectKey(),document.getFilePath()))
                .max(Comparator.comparing(DocumentVersion::getVersionNumber)).orElse(null);
        var original=versions.findByDocumentId(id).stream().filter(v->Integer.valueOf(1).equals(v.getVersionNumber())).findFirst().map(v->file(v,document)).orElse(null);
        var auth=SecurityContextHolder.getContext().getAuthentication();
        boolean download=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")||a.getAuthority().equals("PERMISSION_DOCUMENT_DOWNLOAD"));
        String status="UNAVAILABLE",mime=null,checksum=null,etag=null,objectVersion=null;Long size=null;boolean matches=false;
        try {
            if(document.getFilePath()==null||document.getFilePath().isBlank())status="NOT_LINKED";
            else {
                var stat=minio.statObject(StatObjectArgs.builder().bucket(bucket).object(document.getFilePath()).build());
                status="AVAILABLE";size=stat.size();mime=stat.contentType();etag=stat.etag();objectVersion=stat.versionId();
                var candidate=stat.userMetadata().get("sha256");
                if(candidate!=null&&candidate.matches("(?i)[a-f0-9]{64}"))checksum=candidate.toLowerCase(Locale.ROOT);
                matches=Objects.equals(size,document.getFileSize())&&mime!=null&&mime.equalsIgnoreCase(document.getFileType());
            }
        } catch(io.minio.errors.ErrorResponseException e) {
            status=Set.of("NoSuchKey","NoSuchObject","NoSuchBucket").contains(e.errorResponse().code())?"MISSING":"UNAVAILABLE";
        } catch(Exception e) {status="UNAVAILABLE";} // No endpoint, key, credentials or technical message in response.
        return new Storage(id,version==null?null:version.getId(),original,status,size,mime,checksum,checksum==null?null:"SHA-256",etag,objectVersion,matches,LocalDateTime.now(),download);
    }
    @PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasAuthority('PERMISSION_DOCUMENT_DOWNLOAD') or hasRole('ADMIN')")
    public DocumentVersion version(Long id,Long versionId) {
        documents.load(id);
        return versions.findByIdAndDocumentId(versionId,id).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy phiên bản chứng từ"));
    }
    @PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_DOWNLOAD') or hasRole('ADMIN')")
    public InputStreamResource download(Long id,Long versionId) {return content(id,versionId);}
    public InputStreamResource preview(Long id,Long versionId) {return content(id,versionId);}
    private InputStreamResource content(Long id,Long versionId) {
        var v=version(id,versionId);
        try {return new InputStreamResource(minio.getObject(GetObjectArgs.builder().bucket(bucket).object(v.getObjectKey()).build()));}
        catch(Exception e){throw new com.example.invoice.exception.PipelineException(PipelineFailures.classify(e,"STORAGE",0),503,e);}
    }
    public History history(Long id) {
        var document=documents.load(id);var events=new ArrayList<Event>();
        var snapshots=audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(DocumentAuditService.TYPE,id.toString());
        for(var e:snapshots)if(sameCompany(e,document))events.add(new Event(e.getId(),"SNAPSHOT",e.getAction(),e.getUser()==null?null:e.getUser().getId(),e.getCreatedAt(),null,null,e.getOldValue()!=null,e.getNewValue()!=null));
        for(var r:reviews.findByDocumentId(id))events.add(new Event(r.getId(),"REVIEW",action(r.getReviewNote()),r.getReviewer()==null?null:r.getReviewer().getId(),r.getReviewedAt(),r.getReviewStatus().name(),note(r.getReviewNote()),false,false));
        for(var f:fields.findByDocumentId(id))for(var c:corrections.findByFieldId(f.getId()))events.add(new Event(c.getId(),"FIELD_CORRECTION","FIELD_CORRECTION",c.getCorrectedBy()==null?null:c.getCorrectedBy().getId(),c.getCreatedAt(),null,f.getFieldName(),false,false));
        for(var log:logs.findByDocumentIdOrderByCreatedAtAsc(id)) {
            if("AI_ASSESSMENT".equals(log.getProcessType()))continue;
            events.add(new Event(log.getId(),"PROCESS_LOG",log.getProcessType()+"_"+log.getAgentStep(),null,log.getCreatedAt(),log.getStatus(),null,false,false));
        }
        if(canReadCash()&&document.getCompany()!=null)for(var e:audit.findByEntityTypeAndCompanyIdOrderByCreatedAtDescIdDesc("FINANCIAL_TRANSACTION",document.getCompany().getId()))
            if(linked(e,document))events.add(new Event(e.getId(),"PAYMENT",e.getAction().split(":",2)[0],e.getUser()==null?null:e.getUser().getId(),e.getCreatedAt(),null,null,e.getOldValue()!=null,e.getNewValue()!=null));
        events.sort(Comparator.comparing(Event::at,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Event::id,Comparator.nullsLast(Comparator.reverseOrder())));
        return new History(events,snapshots.stream().noneMatch(e->"UPLOAD".equals(e.getAction())));
    }
    public Snapshot snapshot(Long id,Long eventId,String source) {
        var document=documents.load(id);var event=audit.findById(eventId).filter(e->DocumentAuditService.TYPE.equals(e.getEntityType())&&id.toString().equals(e.getEntityId())&&sameCompany(e,document))
                .orElse(null);
        if("FIELD_CORRECTION".equals(source)) {
            var c=corrections.findById(eventId).filter(v->id.equals(v.getField().getDocument().getId())).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy chỉnh sửa"));
            var before=json.createObjectNode();var after=json.createObjectNode();
            before.put(c.getField().getFieldName(),c.getOldValue());after.put(c.getField().getFieldName(),c.getNewValue());
            return new Snapshot(c.getId(),"FIELD_CORRECTION",c.getCorrectedBy()==null?null:c.getCorrectedBy().getId(),c.getCreatedAt(),before,after);
        }
        if("PAYMENT".equals(source)&&canReadCash())event=audit.findById(eventId).filter(e->"FINANCIAL_TRANSACTION".equals(e.getEntityType())&&sameCompany(e,document)&&linked(e,document)).orElse(null);
        else if(!"SNAPSHOT".equals(source))event=null;
        if(event==null)throw new ResourceNotFoundException("Không tìm thấy phiên bản dữ liệu");
        return new Snapshot(event.getId(),event.getAction(),event.getUser()==null?null:event.getUser().getId(),event.getCreatedAt(),parse(event.getOldValue()),parse(event.getNewValue()));
    }
    private boolean sameCompany(AuditLog event,Document document){return Objects.equals(event.getCompany()==null?null:event.getCompany().getId(),document.getCompany()==null?null:document.getCompany().getId());}
    private boolean canReadCash(){return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a->java.util.Set.of("ROLE_ADMIN","PERMISSION_ACCOUNTING_VIEW").contains(a.getAuthority()));}
    private boolean linked(AuditLog event,Document document) {
        Long invoiceId=invoices.findByDocumentId(document.getId()).map(Invoice::getId).orElse(null);
        return linked(parse(event.getOldValue()),document.getId(),invoiceId)||linked(parse(event.getNewValue()),document.getId(),invoiceId);
    }
    private boolean linked(JsonNode value,Long documentId,Long invoiceId) {
        if(value==null)return false;var transaction=value.has("transaction")?value.path("transaction"):value;
        return transaction.path("documentId").asLong(-1)==documentId||invoiceId!=null&&transaction.path("invoiceId").asLong(-1)==invoiceId;
    }
    private JsonNode parse(String value){try{return value==null?null:DocumentAuditService.exactDecimals(json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(value));}catch(Exception e){throw new IllegalStateException("Snapshot không thể đọc; không dùng dữ liệu hiện tại thay thế");}}
    private String action(String value){return value==null?"REVIEW":value.split(":",2)[0];}
    private String note(String value){return value==null||!value.contains(":")?null:value.split(":",2)[1].trim();}
    private FileVersion file(DocumentVersion v,Document d){return new FileVersion(v.getId(),v.getVersionNumber(),v.getOriginalFileName(),v.getContentType(),v.getFileSize(),v.getCreatedBy()==null?null:v.getCreatedBy().getId(),v.getCreatedAt(),Objects.equals(v.getObjectKey(),d.getFilePath()),Integer.valueOf(1).equals(v.getVersionNumber()));}
}

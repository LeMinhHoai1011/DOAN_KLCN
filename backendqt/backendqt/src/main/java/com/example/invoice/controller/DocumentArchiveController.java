package com.example.invoice.controller;

import com.example.invoice.dto.document.DocumentArchive.*;
import com.example.invoice.service.DocumentArchiveService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/documents/{id}") @RequiredArgsConstructor
public class DocumentArchiveController {
    private final DocumentArchiveService archive;
    @GetMapping("/storage") @PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<Storage> storage(@PathVariable Long id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(archive.storage(id));}
    @GetMapping("/versions") @PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
    public List<FileVersion> versions(@PathVariable Long id){return archive.versions(id);}
    @GetMapping("/history") @PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<History> history(@PathVariable Long id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(archive.history(id));}
    @GetMapping("/history/{eventId}") @PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<Snapshot> snapshot(@PathVariable Long id,@PathVariable Long eventId,@RequestParam(defaultValue="SNAPSHOT")String source){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(archive.snapshot(id,eventId,source));}
    @GetMapping("/versions/{versionId}/preview") @PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
    public ResponseEntity<Resource> preview(@PathVariable Long id,@PathVariable Long versionId){var v=archive.version(id,versionId);return file(archive.preview(id,versionId),v.getContentType(),v.getOriginalFileName(),true);}
    @GetMapping("/versions/{versionId}/download") @PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_DOWNLOAD') or hasRole('ADMIN')")
    public ResponseEntity<Resource> download(@PathVariable Long id,@PathVariable Long versionId){var v=archive.version(id,versionId);return file(archive.download(id,versionId),v.getContentType(),v.getOriginalFileName(),false);}
    public static ResponseEntity<Resource> file(Resource resource,String mime,String name,boolean inline) {
        String normalized=mime==null?"":mime.toLowerCase(java.util.Locale.ROOT);
        if(normalized.equals("image/jpg"))normalized="image/jpeg";
        String safeMime=java.util.Set.of("application/pdf","image/jpeg","image/png","image/webp").contains(normalized)?normalized:MediaType.APPLICATION_OCTET_STREAM_VALUE;
        var disposition=inline&&!safeMime.equals(MediaType.APPLICATION_OCTET_STREAM_VALUE)?ContentDisposition.inline():ContentDisposition.attachment();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(safeMime))
                .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION,disposition.filename(name==null?"chung-tu":name.replace("\r","").replace("\n",""),StandardCharsets.UTF_8).build().toString()).body(resource);
    }
}

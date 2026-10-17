package com.example.invoice.dto.document;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

public final class DocumentArchive {
    private DocumentArchive() {}
    public record FileVersion(Long id,Integer number,String name,String mimeType,Long size,Long actorId,
            LocalDateTime at,boolean current,boolean original) {}
    public record Storage(Long documentId,Long currentVersionId,FileVersion original, String status,
            Long actualSize,String actualMimeType,String checksum,String checksumAlgorithm,String etag,
            String objectVersionId,boolean metadataMatches,LocalDateTime checkedAt,boolean canDownload) {}
    public record Event(Long id,String source,String action,Long actorId,LocalDateTime at,
            String status,String note,boolean hasBefore,boolean hasAfter) {}
    public record Snapshot(Long id,String action,Long actorId,LocalDateTime at,JsonNode before,JsonNode after) {}
    public record History(List<Event> events,boolean legacyCoverageIncomplete) {}
}

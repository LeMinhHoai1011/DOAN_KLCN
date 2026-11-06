package com.example.invoice.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** One JSON object per labelled document. Failures never expose the endpoint, key or stored values. */
@Component
public class LiveEvaluationStore {
    private static final String ROOT = "evaluation/live/";
    private static final int MAX_SAMPLES = 1000;
    private final MinioClient minio;
    private final ObjectMapper json;
    private final String bucket;

    public LiveEvaluationStore(MinioClient minio, ObjectMapper json, @Value("${app.minio.bucket:invoice-files}") String bucket) {
        this.minio = minio;
        this.json = json;
        this.bucket = bucket;
    }

    private static String prefix(Long companyId) { return ROOT + "company-" + companyId + "/"; }
    private static String key(Long companyId, Long documentId) { return prefix(companyId) + "document-" + documentId + ".json"; }

    public Optional<LiveEvaluationData.Sample> read(Long companyId, Long documentId) {
        try { return Optional.of(load(key(companyId, documentId))); }
        catch (io.minio.errors.ErrorResponseException e) {
            if (Set.of("NoSuchKey", "NoSuchObject").contains(e.errorResponse().code())) return Optional.empty();
            throw unavailable(e);
        } catch (Exception e) { throw unavailable(e); }
    }

    public void write(LiveEvaluationData.Sample sample) {
        try {
            byte[] bytes = json.writeValueAsBytes(sample);
            minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key(sample.getCompanyId(), sample.getDocumentId()))
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1).contentType("application/json").build());
        } catch (Exception e) { throw unavailable(e); }
    }

    /** @param companyId null lists every company; only an administrator may pass null. */
    public List<LiveEvaluationData.Sample> list(Long companyId) {
        var samples = new ArrayList<LiveEvaluationData.Sample>();
        try {
            for (var result : minio.listObjects(ListObjectsArgs.builder().bucket(bucket)
                    .prefix(companyId == null ? ROOT : prefix(companyId)).recursive(true).build())) {
                if (samples.size() >= MAX_SAMPLES) break;
                samples.add(load(result.get().objectName()));
            }
        } catch (Exception e) { throw unavailable(e); }
        return samples;
    }

    private LiveEvaluationData.Sample load(String key) throws Exception {
        try (var stream = minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return json.readValue(stream, LiveEvaluationData.Sample.class);
        }
    }

    private ResponseStatusException unavailable(Exception cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LIVE_EVALUATION_STORAGE_UNAVAILABLE", cause);
    }
}

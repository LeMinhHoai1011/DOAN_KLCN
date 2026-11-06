package com.example.invoice.evaluation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.invoice.evaluation.LiveEvaluationData.Label;
import com.example.invoice.evaluation.LiveEvaluationData.Sample;
import com.example.invoice.evaluation.LiveEvaluationData.State;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class LiveEvaluationStoreTest {
    MinioClient minio=mock(MinioClient.class);
    ObjectMapper json=JsonMapper.builder().addModule(new JavaTimeModule()).build();
    LiveEvaluationStore store=new LiveEvaluationStore(minio,json,"invoices");

    @Test void labelsRoundTripUnderTheirCompanyPrefix()throws Exception {
        var sample=new Sample();sample.setCompanyId(7L);sample.setDocumentId(5L);sample.setSelectedAt(Instant.parse("2026-11-06T00:00:00Z"));
        sample.getFields().put("totalAmount",new Label(State.PRESENT,"180.000"));sample.setItems(List.of(Map.of("amount","10")));
        store.write(sample);
        var captor=ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minio).putObject(captor.capture());
        assertEquals("invoices",captor.getValue().bucket());assertEquals("evaluation/live/company-7/document-5.json",captor.getValue().object());
        byte[] bytes=captor.getValue().stream().readAllBytes();
        when(minio.getObject(any(GetObjectArgs.class))).thenReturn(new GetObjectResponse(Headers.of(),"invoices",null,"key",new ByteArrayInputStream(bytes)));
        var read=store.read(7L,5L).orElseThrow();
        assertEquals("180.000",read.getFields().get("totalAmount").value());assertEquals(sample.getSelectedAt(),read.getSelectedAt());assertEquals("10",read.getItems().getFirst().get("amount"));
    }

    @Test void missingObjectIsEmptyAndOtherFailuresDoNotLeakDetails()throws Exception {
        var error=mock(ErrorResponseException.class);var body=mock(ErrorResponse.class);
        when(error.errorResponse()).thenReturn(body);when(body.code()).thenReturn("NoSuchKey");
        when(minio.getObject(any(GetObjectArgs.class))).thenThrow(error);
        assertTrue(store.read(7L,5L).isEmpty());
        when(body.code()).thenReturn("AccessDenied");
        assertEquals(503,assertThrows(ResponseStatusException.class,()->store.read(7L,5L)).getStatusCode().value());
    }
}

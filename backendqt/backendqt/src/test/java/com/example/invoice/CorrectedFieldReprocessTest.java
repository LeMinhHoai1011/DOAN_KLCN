package com.example.invoice;

import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CorrectedFieldReprocessTest {
    @Autowired DocumentRepository documents;
    @Autowired ExtractedFieldRepository fields;
    @Autowired FieldCorrectionRepository corrections;

    @Test void deletingOldAiFieldsPreservesCorrectedFieldAndItsHistory() {
        var document = new Document(); document.setOriginalFileName("test.png");
        document.setFileType("image/png"); document.setFileSize(1L);
        document = documents.saveAndFlush(document);
        var corrected = field(document, "voucherNumber");
        var obsolete = field(document, "obsolete");
        var history = new FieldCorrection(); history.setField(corrected); history.setNewValue("MANUAL-1");
        corrections.saveAndFlush(history);
        assertEquals(1, fields.deleteByDocumentIdAndSource(document.getId(), "AI"));
        assertTrue(fields.existsById(corrected.getId()));
        assertFalse(fields.existsById(obsolete.getId()));
        assertEquals("MANUAL-1", corrections.findFirstByFieldIdOrderByCreatedAtDescIdDesc(corrected.getId()).orElseThrow().getNewValue());
    }
    private ExtractedField field(Document document, String name) {
        var field = new ExtractedField(); field.setDocument(document); field.setFieldName(name);
        field.setFieldValue("AI value"); field.setSource("AI");
        return fields.saveAndFlush(field);
    }
}

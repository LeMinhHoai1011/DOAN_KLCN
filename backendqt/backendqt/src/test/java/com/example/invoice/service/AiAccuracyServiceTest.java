package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.invoice.entity.AuditLog;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentReview;
import com.example.invoice.repository.AuditLogRepository;
import com.example.invoice.repository.DocumentReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiAccuracyServiceTest {
	private final DocumentReviewRepository reviews = mock(DocumentReviewRepository.class);
	private final AuditLogRepository audit = mock(AuditLogRepository.class);
	private final AiAccuracyService service = new AiAccuracyService(reviews, audit, null, null, new ObjectMapper());

	private static final String AI = """
		{"classification":{"predictedLabel":"VAT_INVOICE","category":"VAT_TU"},
		 "invoice":{"invoiceNumber":"0000123","sellerName":"Công ty A","totalAmount":108,
		  "items":[{"productName":"Ván","quantity":3,"unitPrice":36,"amount":108}]}}
		""";

	@Test
	void listsEveryApprovedDocumentAndScoresEachFieldGroup() {
		Document exact = document(1L, "exact.pdf"), changed = document(2L, "changed.pdf"), noAi = document(3L, "no-ai.pdf");
		when(reviews.findDecisionsBetween(any(), any())).thenReturn(List.of(
				approval(exact, LocalDateTime.of(2026, 11, 1, 9, 0)),
				approval(changed, LocalDateTime.of(2026, 11, 2, 9, 0)),
				approval(noAi, LocalDateTime.of(2026, 11, 3, 9, 0))));
		String kept = AI.replace("\"sellerName\":\"Công ty A\"", "\"sellerName\":\"Công ty B\"")
				.replace("\"quantity\":3", "\"quantity\":3,\"unit\":\"Tấm\"")
				.replace("\"category\":\"VAT_TU\"", "\"category\":\"VAT_TU\",\"accountingAccount\":\"1521\"");
		List<AuditLog> events = new ArrayList<>();
		events.add(event(1L, "AI_EXTRACT", AI)); events.add(event(1L, "WORKFLOW_APPROVE", AI));
		events.add(event(2L, "AI_EXTRACT", AI)); events.add(event(2L, "WORKFLOW_APPROVE", kept));
		events.add(event(3L, "WORKFLOW_APPROVE", AI));
		when(audit.findByEntityTypeAndEntityIdInOrderByCreatedAtAscIdAsc(eq(DocumentAuditService.TYPE), anyList())).thenReturn(events);

		var report = service.report(null, null);

		assertEquals(3, report.documents().size());
		assertEquals(List.of(3L, 2L, 1L), report.documents().stream().map(AiAccuracyService.DocumentRow::documentId).toList(),
				"newest approval first");
		var skipped = report.documents().get(0);
		assertEquals("SKIPPED", skipped.status());
		assertEquals("NO_AI_RESULT", skipped.skipReason());
		assertEquals("CHANGED", report.documents().get(1).status());
		assertEquals(3, report.documents().get(1).changes());
		assertEquals("EXACT", report.documents().get(2).status());
		assertEquals(1.0, report.documents().get(2).accuracy());

		var all = group(report, "ALL");
		// exact: 9 values kept; changed: 9 AI values of which 1 wrong, plus 2 the accountant filled in.
		assertEquals(20, all.compared());
		assertEquals(17, all.correct());
		assertEquals(1, all.wrong());
		assertEquals(2, all.missing());
		assertEquals(17.0 / 18, all.precision(), 1e-9);
		assertEquals(17.0 / 20, all.recall(), 1e-9);
		var header = group(report, "HEADER");
		assertEquals(1, header.wrong());
		assertEquals(5, header.correct());
		var items = group(report, "ITEMS");
		assertEquals(1, items.missing());
		assertEquals(1.0, items.precision());
		var classification = group(report, "CLASSIFICATION");
		assertEquals(1, classification.missing());
	}

	@Test
	void aRerunAfterApprovalIsSkippedWithItsReason() {
		var outcome = service.compareApprovedOutcome(List.of(event(5L, "AI_EXTRACT", AI), event(5L, "WORKFLOW_APPROVE", AI),
				event(5L, "AI_EXTRACT", AI)));
		assertNull(outcome.result());
		assertEquals("NO_APPROVAL_AFTER_AI", outcome.skipReason());
		assertEquals("UNREADABLE_SNAPSHOT", service.compareApprovedOutcome(List.of(event(5L, "AI_EXTRACT", "{bad"),
				event(5L, "WORKFLOW_APPROVE", AI))).skipReason());
	}

	@Test
	void scoresAreBlankWhenNothingWasCompared() {
		var empty = AiAccuracyService.GroupScore.of("ITEMS", 0, 0, 0, 0);
		assertNull(empty.precision());
		assertNull(empty.recall());
		assertNull(empty.f1());
	}

	private static AiAccuracyService.GroupScore group(AiAccuracyService.Report report, String name) {
		return report.groups().stream().filter(g -> name.equals(g.group())).findFirst().orElseThrow();
	}

	private static Document document(Long id, String name) {
		Document document = new Document(); document.setId(id); document.setOriginalFileName(name);
		return document;
	}

	private static DocumentReview approval(Document document, LocalDateTime at) {
		DocumentReview review = new DocumentReview(); review.setDocument(document); review.setReviewNote("APPROVE"); review.setReviewedAt(at);
		return review;
	}

	private static AuditLog event(Long documentId, String action, String snapshot) {
		AuditLog event = new AuditLog(); event.setEntityType(DocumentAuditService.TYPE); event.setEntityId(documentId.toString());
		event.setAction(action); event.setNewValue(snapshot);
		return event;
	}
}

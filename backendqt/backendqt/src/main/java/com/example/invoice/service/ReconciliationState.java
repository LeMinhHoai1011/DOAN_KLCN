package com.example.invoice.service;

import com.example.invoice.entity.Document;
import com.example.invoice.entity.Reconciliation;
import com.example.invoice.entity.ReconciliationBatch;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Outcome of the latest reconciliation run, shared by the business-state view and the approval gate. */
record ReconciliationState(String overall, boolean stale, List<String> unresolved, ReconciliationBatch latestBatch) {
	static final String NOT_RUN = "NOT_RUN";
	private static final Set<String> RESOLVED = Set.of("MATCHED", "ACCEPTED");

	/** @param results newest first */
	static ReconciliationState of(Document document, List<Reconciliation> results) {
		// Latest result of each check; an older mismatch must not override a later decision.
		Map<String, String> checks = new LinkedHashMap<>();
		ReconciliationBatch latestBatch = results.isEmpty() ? null : results.getFirst().getReconciliationBatch();
		for (Reconciliation result : results) {
			if (latestBatch != null && (result.getReconciliationBatch() == null
					|| !Objects.equals(latestBatch.getId(), result.getReconciliationBatch().getId()))) continue;
			String note = result.getComment();
			String key = note == null ? "id:" + result.getId() : note.split(":", 2)[0];
			checks.putIfAbsent(key, result.getStatus() == null ? "UNKNOWN" : result.getStatus());
		}
		boolean stale = latestBatch != null && latestBatch.getCreatedAt() != null && document.getInvoice() != null
				&& document.getInvoice().getUpdatedAt() != null
				&& document.getInvoice().getUpdatedAt().isAfter(latestBatch.getCreatedAt());
		String overall = checks.isEmpty() ? NOT_RUN : stale ? "WARNING" : checks.containsValue("MISMATCH") ? "MISMATCH"
				: checks.values().stream().allMatch("MATCHED"::equals) ? "MATCHED"
				: RESOLVED.containsAll(checks.values()) ? "ACCEPTED" : "WARNING";
		var unresolved = checks.entrySet().stream().filter(check -> !RESOLVED.contains(check.getValue()))
				.map(Map.Entry::getKey).toList();
		return new ReconciliationState(overall, stale, unresolved, latestBatch);
	}

	boolean isLatest(Reconciliation result) {
		return latestBatch == null || result.getReconciliationBatch() != null
				&& Objects.equals(latestBatch.getId(), result.getReconciliationBatch().getId());
	}
}

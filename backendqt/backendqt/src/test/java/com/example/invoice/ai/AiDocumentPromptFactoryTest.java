package com.example.invoice.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AiDocumentPromptFactoryTest {
	@Test
	void includesStructuredCompanyCategoriesAndClassificationEvidenceRules() {
		String prompt = AiDocumentPromptFactory.createWithCategories(List.of("VAT_INVOICE"), List.of(
				new AiDocumentPromptFactory.AccountingCategoryOption(
						"OFFICE_SUPPLIES", "Văn phòng phẩm", "Dùng cho hoạt động văn phòng")),
				"Công ty A", "0123456789", null);

		assertTrue(prompt.contains("OFFICE_SUPPLIES | Văn phòng phẩm | Dùng cho hoạt động văn phòng"));
		assertTrue(prompt.contains("do not classify from the supplier name alone"));
		assertTrue(prompt.contains("exactly one of these codes: OFFICE_SUPPLIES"));
	}
}

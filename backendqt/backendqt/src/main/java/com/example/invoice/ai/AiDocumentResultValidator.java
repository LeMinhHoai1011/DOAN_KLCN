package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.exception.AiProviderException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AiDocumentResultValidator {
	private static final Pattern TAX_CODE = Pattern.compile("\\d{10}(?:-\\d{3})?");
	private static final Pattern EXTRA_FIELD_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]*");
	private static final BigDecimal MONEY_TOLERANCE = new BigDecimal("0.01");

	public ValidatedAiDocumentResult validate(AiDocumentResult result, Set<String> allowedTypes) {
		if (result.documentType() == null || !allowedTypes.contains(result.documentType())) {
			throw new AiProviderException("AI_INVALID_RESPONSE: loại chứng từ không nằm trong danh sách được backend cho phép");
		}
		List<String> warnings = new ArrayList<>(result.warnings() == null ? List.of() : result.warnings());
		BigDecimal confidence = normalizeConfidence(result.classificationConfidence(), warnings);
		validateInvoice(result.invoice(), warnings);
		validateTransactionAssessment(result.transactionAssessment(), warnings);
		validateCompanyRole(result.companyRole(), warnings);
		validateExtraFields(result.extraFields(), warnings);
		return new ValidatedAiDocumentResult(result, confidence, List.copyOf(warnings));
	}

	private BigDecimal normalizeConfidence(BigDecimal value, List<String> warnings) {
		if (value == null) {
			warnings.add("AI did not provide classification confidence");
			return BigDecimal.ZERO;
		}
		if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
			throw new AiProviderException("AI_INVALID_RESPONSE: độ tin cậy phải nằm trong khoảng từ 0,0 đến 1,0");
		}
		return value;
	}

	private void validateInvoice(AiDocumentResult.AiInvoiceExtraction invoice, List<String> warnings) {
		if (invoice == null) return;
		if (invoice.sellerTaxCode() != null && !TAX_CODE.matcher(invoice.sellerTaxCode().trim()).matches()) {
			warnings.add("Seller tax code has an unexpected format");
		}
		if (invoice.buyerTaxCode() != null && !TAX_CODE.matcher(invoice.buyerTaxCode().trim()).matches()) {
			warnings.add("Buyer tax code has an unexpected format");
		}
		if (invoice.invoiceDate() != null) {
			try {
				LocalDate.parse(invoice.invoiceDate());
			} catch (DateTimeParseException exception) {
				warnings.add("Invoice date is not an unambiguous yyyy-MM-dd value");
			}
		}
		if (invoice.subtotal() != null && invoice.vatAmount() != null && invoice.totalAmount() != null
				&& invoice.subtotal().add(invoice.vatAmount()).subtract(invoice.totalAmount()).abs().compareTo(MONEY_TOLERANCE) > 0) {
			warnings.add("Tổng tiền trước thuế cộng VAT không khớp với tổng thanh toán");
		}
	}

	private void validateTransactionAssessment(AiDocumentResult.AiTransactionAssessment assessment, List<String> warnings) {
		if (assessment != null && assessment.confidence() != null) normalizeConfidence(assessment.confidence(), warnings);
	}

	private void validateCompanyRole(AiDocumentResult.AiCompanyRole companyRole, List<String> warnings) {
		if (companyRole != null && companyRole.confidence() != null) normalizeConfidence(companyRole.confidence(), warnings);
	}

	private void validateExtraFields(List<AiDocumentResult.AiExtraField> extraFields, List<String> warnings) {
		if (extraFields == null) return;
		for (AiDocumentResult.AiExtraField field : extraFields) {
			if (field == null || field.name() == null || !EXTRA_FIELD_NAME.matcher(field.name()).matches()) {
				warnings.add("AI returned an extra field with a non-canonical name; persistence will normalize or skip it");
				continue;
			}
			if (field.confidence() != null && (field.confidence().compareTo(BigDecimal.ZERO) < 0
					|| field.confidence().compareTo(BigDecimal.ONE) > 0))
				warnings.add("AI trả về trường bổ sung có độ tin cậy không hợp lệ; trường này sẽ bị bỏ qua");
		}
	}

	public record ValidatedAiDocumentResult(AiDocumentResult result, BigDecimal confidence, List<String> warnings) {
		public boolean requiresReview(BigDecimal threshold) {
			return confidence.compareTo(threshold) < 0 || !warnings.isEmpty();
		}
	}
}

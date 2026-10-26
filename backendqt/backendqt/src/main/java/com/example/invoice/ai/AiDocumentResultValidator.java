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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AiDocumentResultValidator {
	private static final Logger log = LoggerFactory.getLogger(AiDocumentResultValidator.class);
	private static final Pattern TAX_CODE = Pattern.compile("\\d{10}(?:-\\d{3})?");
	private static final Pattern EXTRA_FIELD_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]*");
	private static final BigDecimal MONEY_TOLERANCE = new BigDecimal("0.01");
	public static final int MAX_INVOICE_NUMBER_LENGTH = 255;
	public static final int MAX_INVOICE_SERIES_LENGTH = 255;
	public static final int MAX_TAX_CODE_LENGTH = 255;
	public static final int MAX_SELLER_PHONE_LENGTH = 100;
	public static final int MAX_PAYMENT_METHOD_LENGTH = 255;
	public static final int MAX_TAX_AUTHORITY_CODE_LENGTH = 255;
	private static final int MAX_AMOUNT_IN_WORDS_LENGTH = 8000;
	private static final int MAX_REASONABLE_NAME_LENGTH = 1000;
	private static final int MAX_REASONABLE_ADDRESS_LENGTH = 4000;
	private static final int MAX_REASONABLE_ITEM_NAME_LENGTH = 2000;

	public ValidatedAiDocumentResult validate(AiDocumentResult result, Set<String> allowedTypes) {
		return validate(result, allowedTypes, null);
	}

	public ValidatedAiDocumentResult validate(AiDocumentResult result, Set<String> allowedTypes,
			Set<String> allowedAccountingCategories) {
		if (result.documentType() == null || !allowedTypes.contains(result.documentType())) {
			log.warn("AI_SCHEMA_VALIDATION_FAILED field=documentType value={}", result.documentType());
			throw new UnresolvedDocumentTypeException();
		}
		List<String> warnings = new ArrayList<>(result.warnings() == null ? List.of() : result.warnings());
		if (warnings.stream().noneMatch(AiDocumentResultValidator::hasDocumentTypeEvidence)) {
			warnings.add("MISSING_DOCUMENT_TYPE_EVIDENCE: AI không cung cấp căn cứ nhận diện loại chứng từ");
		}
		BigDecimal confidence = normalizeConfidence(result.classificationConfidence(), warnings);
		result = DocumentTypeValidationPolicy.validate(result, warnings);
		if (isInvoiceType(result.documentType()) && result.invoice() == null) {
			warnings.add("MISSING_INVOICE_EXTRACTION: Thiếu dữ liệu hóa đơn có cấu trúc");
		}
		if (result.accountingCategoryCode() != null && allowedAccountingCategories != null
				&& !allowedAccountingCategories.contains(result.accountingCategoryCode())) {
			log.warn("AI_CATEGORY_VALIDATION_FAILED categoryCode={} allowedCategoryCount={}",
					result.accountingCategoryCode(), allowedAccountingCategories.size());
			warnings.add("INVALID_ACCOUNTING_CATEGORY: AI trả nhóm nghiệp vụ không nằm trong danh sách active; đề xuất này bị bỏ qua");
			result = withoutAccountingCategory(result);
		}
        boolean retail = "RETAIL_INVOICE".equals(result.documentType());
		validateInvoice(result.invoice(), warnings, retail);
        if (retail && result.invoice()!=null) {
            var invoice=result.invoice();
            var rows=invoice.items()==null ? List.<RetailInvoiceRules.Row>of() : invoice.items().stream()
                    .map(item -> item==null ? new RetailInvoiceRules.Row(null,null,null)
                            : new RetailInvoiceRules.Row(item.quantity(),item.unitPrice(),item.amount())).toList();
            for (var check : RetailInvoiceRules.evaluate(invoice.retailDetails(),rows,invoice.totalAmount(),invoice.vatAmount(),false)) {
                if (check.missing()) warnings.add("RETAIL_DATA_INSUFFICIENT: "+check.message());
                else if (check.declared().subtract(check.calculated()).abs().compareTo(MONEY_TOLERANCE)>0)
                    warnings.add("RETAIL_AMOUNT_MISMATCH: "+check.type()+"; "+check.message());
            }
        }
		validateTransactionAssessment(result.transactionAssessment(), warnings);
		validateCompanyRole(result.companyRole(), warnings);
		validateExtraFields(result.extraFields(), warnings);
		return new ValidatedAiDocumentResult(result, confidence, List.copyOf(warnings));
	}

	private AiDocumentResult withoutAccountingCategory(AiDocumentResult result) {
		return new AiDocumentResult(result.provider(), result.model(), result.documentType(),
				result.classificationConfidence(), null, result.accountingAccount(), result.rawText(), result.invoice(),
				result.fields(), result.warnings(), result.rawResponse(), result.durationMs(), result.documentDirection(),
				result.transactionAssessment(), result.extraFields(), result.companyRole());
	}

	private boolean isInvoiceType(String typeCode) {
		if (typeCode == null) return false;
		String normalized = typeCode.trim().toUpperCase(java.util.Locale.ROOT)
				.replace('-', '_').replace(' ', '_');
		return "INVOICE".equals(normalized) || "VAT_INVOICE".equals(normalized)
				|| normalized.endsWith("_INVOICE");
	}

	private static boolean hasDocumentTypeEvidence(String warning) {
		if (warning == null) return false;
		String prefix = "DOCUMENT_TYPE_REASON:";
		return warning.regionMatches(true, 0, prefix, 0, prefix.length())
				&& !warning.substring(prefix.length()).isBlank();
	}

	private BigDecimal normalizeConfidence(BigDecimal value, List<String> warnings) {
		if (value == null) {
			warnings.add("AI did not provide classification confidence");
			return null;
		}
		if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
			throw new AiProviderException("AI_INVALID_RESPONSE: độ tin cậy phải nằm trong khoảng từ 0,0 đến 1,0");
		}
		return value;
	}

	public static class UnresolvedDocumentTypeException extends AiProviderException {
		public UnresolvedDocumentTypeException() {
			super("DOCUMENT_TYPE_UNRESOLVED: AI chưa xác định được loại chứng từ hợp lệ; cần kiểm tra thủ công");
		}
	}

	private void validateInvoice(AiDocumentResult.AiInvoiceExtraction invoice, List<String> warnings, boolean retail) {
		if (invoice == null) return;
		if (!retail && (invoice.invoiceNumber() == null || invoice.invoiceNumber().isBlank()))
			warnings.add("MISSING_INVOICE_NUMBER: thiếu số hóa đơn");
		if (invoice.invoiceDate() == null || invoice.invoiceDate().isBlank())
			warnings.add("MISSING_INVOICE_DATE: thiếu ngày hóa đơn");
		if (invoice.sellerName() == null || invoice.sellerName().isBlank())
			warnings.add("MISSING_SELLER_NAME: thiếu tên người bán");
		if (!retail && (invoice.sellerTaxCode() == null || invoice.sellerTaxCode().isBlank()))
			warnings.add("MISSING_SELLER_TAX_CODE: thiếu mã số thuế người bán");
		if (invoice.totalAmount() == null)
			warnings.add("MISSING_TOTAL_AMOUNT: thiếu tổng tiền thanh toán");
		warnLength(invoice.invoiceNumber(), MAX_INVOICE_NUMBER_LENGTH, "INVOICE_NUMBER_INVALID_LENGTH", warnings);
		warnLength(invoice.invoiceSeries(), MAX_INVOICE_SERIES_LENGTH, "INVOICE_SERIES_INVALID_LENGTH", warnings);
		warnLength(invoice.sellerTaxCode(), MAX_TAX_CODE_LENGTH, "SELLER_TAX_CODE_INVALID_LENGTH", warnings);
		warnLength(invoice.buyerTaxCode(), MAX_TAX_CODE_LENGTH, "BUYER_TAX_CODE_INVALID_LENGTH", warnings);
		warnLength(invoice.sellerName(), MAX_REASONABLE_NAME_LENGTH, "SELLER_NAME_ABNORMAL_LENGTH", warnings);
		warnLength(invoice.buyerName(), MAX_REASONABLE_NAME_LENGTH, "BUYER_NAME_ABNORMAL_LENGTH", warnings);
		warnLength(invoice.sellerAddress(), MAX_REASONABLE_ADDRESS_LENGTH, "SELLER_ADDRESS_ABNORMAL_LENGTH", warnings);
		warnLength(invoice.buyerAddress(), MAX_REASONABLE_ADDRESS_LENGTH, "BUYER_ADDRESS_ABNORMAL_LENGTH", warnings);
		warnLength(invoice.sellerPhone(), MAX_SELLER_PHONE_LENGTH, "SELLER_PHONE_INVALID_LENGTH", warnings);
		warnLength(invoice.paymentMethod(), MAX_PAYMENT_METHOD_LENGTH, "PAYMENT_METHOD_INVALID_LENGTH", warnings);
		warnLength(invoice.amountInWords(), MAX_AMOUNT_IN_WORDS_LENGTH, "AMOUNT_IN_WORDS_ABNORMAL_LENGTH", warnings);
		warnLength(invoice.taxAuthorityCode(), MAX_TAX_AUTHORITY_CODE_LENGTH, "TAX_AUTHORITY_CODE_INVALID_LENGTH", warnings);
		if (invoice.items() == null || invoice.items().isEmpty())
            warnings.add("INVOICE_ITEMS_MISSING: Hóa đơn phải có ít nhất một dòng hàng hóa");
        if (!retail && invoice.subtotal() == null) warnings.add("MISSING_SUBTOTAL: Thiếu tổng trước thuế");
        if (!retail && invoice.vatAmount() == null) warnings.add("MISSING_VAT_AMOUNT: Thiếu tiền thuế");
        if (invoice.items() != null) {
			for (int index = 0; index < invoice.items().size(); index++)
				validateInvoiceItem(invoice.items().get(index), index + 1, warnings, retail);
		}
		if (invoice.sellerTaxCode() != null && !TAX_CODE.matcher(invoice.sellerTaxCode().trim()).matches()) {
			warnings.add("INVALID_SELLER_TAX_CODE: mã số thuế người bán không hợp lệ");
		}
		if (invoice.buyerTaxCode() != null && !TAX_CODE.matcher(invoice.buyerTaxCode().trim()).matches()) {
			warnings.add("INVALID_BUYER_TAX_CODE: mã số thuế người mua không hợp lệ");
		}
		if (invoice.invoiceDate() != null) {
			try {
				LocalDate.parse(invoice.invoiceDate());
			} catch (DateTimeParseException exception) {
				warnings.add("INVALID_INVOICE_DATE: ngày hóa đơn không phải yyyy-MM-dd hợp lệ");
			}
		}
		if (invoice.signDate() != null) {
			try {
				LocalDate.parse(invoice.signDate());
			} catch (DateTimeParseException exception) {
				warnings.add("INVALID_SIGN_DATE: ngày ký không phải yyyy-MM-dd hợp lệ");
			}
		}
		if (!retail && invoice.subtotal() != null && invoice.vatAmount() != null && invoice.totalAmount() != null
				&& invoice.subtotal().add(invoice.vatAmount()).subtract(invoice.totalAmount()).abs().compareTo(MONEY_TOLERANCE) > 0) {
			warnings.add("TOTAL_MISMATCH: tổng tiền trước thuế cộng VAT không khớp với tổng thanh toán");
		}
		warnNegative(invoice.subtotal(), "NEGATIVE_SUBTOTAL", warnings);
		warnNegative(invoice.vatAmount(), "NEGATIVE_VAT_AMOUNT", warnings);
		warnNegative(invoice.totalAmount(), "NEGATIVE_TOTAL_AMOUNT", warnings);
	}

	private void validateInvoiceItem(AiDocumentResult.AiInvoiceItemExtraction item, int row,
			List<String> warnings, boolean retail) {
		if (item == null) {
			warnings.add("INVALID_INVOICE_ITEM: row=" + row + ", item is null");
			return;
		}
		warnLength(item.productName(), MAX_REASONABLE_ITEM_NAME_LENGTH,
				"INVOICE_ITEM_NAME_ABNORMAL_LENGTH", warnings);
		if (item.productName() == null || item.productName().isBlank())
			warnings.add("MISSING_INVOICE_ITEM_NAME: row=" + row);
		if (item.quantity() == null || item.unitPrice() == null || item.amount() == null)
            warnings.add("MISSING_INVOICE_ITEM_AMOUNTS: row=" + row + ", thiếu số lượng, đơn giá hoặc thành tiền");
        warnNegative(item.quantity(), "NEGATIVE_ITEM_QUANTITY: row=" + row, warnings);
		warnNegative(item.unitPrice(), "NEGATIVE_ITEM_UNIT_PRICE: row=" + row, warnings);
		warnNegative(item.taxAmount(), "NEGATIVE_ITEM_TAX_AMOUNT: row=" + row, warnings);
		warnNegative(item.amount(), "NEGATIVE_ITEM_AMOUNT: row=" + row, warnings);
		if (item.taxRate() != null && (item.taxRate().compareTo(BigDecimal.ZERO) < 0
				|| item.taxRate().compareTo(BigDecimal.ONE) > 0))
			warnings.add("INVALID_ITEM_TAX_RATE: row=" + row + ", expected decimal fraction from 0 to 1");
		if (!retail && item.quantity() != null && item.unitPrice() != null && item.amount() != null
				&& item.quantity().multiply(item.unitPrice()).subtract(item.amount()).abs()
						.compareTo(MONEY_TOLERANCE) > 0)
			warnings.add("ITEM_AMOUNT_MISMATCH: row=" + row + ", quantity * unitPrice does not match amount");
		if (!retail && item.amount() != null && item.taxRate() != null && item.taxAmount() != null
				&& item.amount().multiply(item.taxRate()).subtract(item.taxAmount()).abs()
						.compareTo(MONEY_TOLERANCE) > 0)
			warnings.add("ITEM_TAX_MISMATCH: row=" + row + ", amount * taxRate does not match taxAmount");
	}

	private void warnNegative(BigDecimal value, String code, List<String> warnings) {
		if (value != null && value.compareTo(BigDecimal.ZERO) < 0) warnings.add(code);
	}

	private void warnLength(String value, int maximum, String code, List<String> warnings) {
		if (value != null && value.length() > maximum) warnings.add(code + ": length=" + value.length() + ", max=" + maximum);
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
			return confidence == null || confidence.compareTo(threshold) < 0 || warnings.stream().anyMatch(this::isCritical);
		}

		public List<String> reviewReasons(BigDecimal threshold) {
			List<String> reasons = new ArrayList<>();
			if (confidence == null)
				reasons.add("MISSING_CLASSIFICATION_CONFIDENCE: AI không cung cấp độ tin cậy");
			else if (confidence.compareTo(threshold) < 0)
				reasons.add("LOW_CLASSIFICATION_CONFIDENCE: confidence=" + confidence + ", threshold=" + threshold);
			warnings.stream().filter(warning -> isCritical(warning)
					|| AiDocumentResultValidator.hasDocumentTypeEvidence(warning)).forEach(reasons::add);
			return List.copyOf(reasons);
		}

		private boolean isCritical(String warning) {
			return AssessmentWarnings.critical(warning);
		}

	}
}

package com.example.invoice.ai;

import com.example.invoice.ai.DocumentTypeSchema.Requirement;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.ai.AiDocumentResult.AiFieldNote;
import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceExtraction;
import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceItemExtraction;
import com.example.invoice.dto.document.DocumentAssessment.Warning;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-field result of one AI run, for the accountant: the value as read (raw), the value kept (normalized), a status
 * and the reasons. Built from the validated result, its warnings and the field notes; it changes nothing.
 *
 * <p>Statuses: PRESENT_VALID, PRESENT_NORMALIZED (format changed, value not), AMBIGUOUS, INVALID, SUSPECT,
 * REPAIRED_WITH_EVIDENCE, SUGGESTED (value kept, a correction is only proposed), CLEARED, MISSING_REQUIRED,
 * ABSENT_OPTIONAL, NOT_APPLICABLE.
 *
 * <p>{@code applied} is false when the AI values were not written to the invoice because an accountant already
 * edited it; the report then shows what the AI read, for comparison only.
 */
public record FieldValidationReport(int version, String documentType, boolean applied, String note,
		List<FieldStatus> fields, List<FieldStatus> items, List<AiFieldNote> notes, List<ItemDiscount> itemDiscounts) {
	public static final int VERSION = 1;

	/**
	 * A printed row discount with the row values it was read with, so it can be shown next to the stored goods row
	 * that still has exactly these values (and hidden once someone changes that row).
	 */
	public record ItemDiscount(Integer row, String productName, BigDecimal quantity, BigDecimal unitPrice,
			BigDecimal amount, BigDecimal discountAmount) {
		public boolean matches(String name, BigDecimal otherQuantity, BigDecimal otherPrice, BigDecimal otherAmount) {
			return Objects.equals(trim(productName), trim(name)) && same(quantity, otherQuantity)
					&& same(unitPrice, otherPrice) && same(amount, otherAmount);
		}

		private static String trim(String value) { return value == null ? null : value.trim(); }

		private static boolean same(BigDecimal left, BigDecimal right) {
			return left == null ? right == null : right != null && left.compareTo(right) == 0;
		}
	}
	/** Stored as one extracted_fields row (JSON value), so no table or column is added. */
	public static final String FIELD = "aiFieldValidation";
	public static final String SOURCE = "AI_VALIDATION";

	public record FieldStatus(String field, Integer row, String requirement, String status, String rawValue,
			String normalizedValue, List<String> reasons) {}

	private static final Pattern ROW = Pattern.compile("(?:^|[ :;,])row=(\\d+)");

	public static FieldValidationReport build(AiDocumentResult result, List<String> warnings, boolean applied) {
		List<AiFieldNote> notes = result.notes();
		String type = result.documentType();
		List<Warning> parsed = warnings.stream().map(AssessmentWarnings::normalize).toList();
		List<FieldStatus> fields = new ArrayList<>();
		List<FieldStatus> items = new ArrayList<>();
		List<ItemDiscount> itemDiscounts = new ArrayList<>();
		if (DocumentTypeSchema.isInvoice(type)) {
			AiInvoiceExtraction invoice = result.invoice();
			Set<Integer> noTaxRows = AiDocumentResultValidator.noTaxRows(notes);
			boolean allNoTax = invoice != null && invoice.items() != null && !invoice.items().isEmpty()
					&& noTaxRows.size() == invoice.items().size();
			Map<String, Requirement> schema = DocumentTypeSchema.fields(type, null);
			for (String name : DocumentTypeSchema.INVOICE_FIELDS) {
				Requirement requirement = schema.getOrDefault(name, Requirement.OPTIONAL);
				if ("vatAmount".equals(name) && allNoTax) requirement = Requirement.NOT_APPLICABLE;
				fields.add(status(name, null, requirement, invoice == null ? null : headerValue(invoice, name), notes, parsed));
			}
			if (invoice != null && invoice.items() != null) {
				Map<Integer, BigDecimal> discounts = InvoiceItemRepairs.discounts(notes);
				for (int index = 0; index < invoice.items().size(); index++) {
					AiInvoiceItemExtraction item = invoice.items().get(index);
					if (item == null) continue;
					int row = index + 1;
					if (discounts.containsKey(row)) itemDiscounts.add(new ItemDiscount(row, item.productName(), item.quantity(),
							item.unitPrice(), item.amount(), discounts.get(row)));
					for (String column : DocumentTypeSchema.ITEM_FIELDS) {
						Requirement requirement = switch (column) {
							case "productName", "quantity", "unitPrice", "amount" -> Requirement.REQUIRED;
							case "taxRate", "taxAmount" -> noTaxRows.contains(row) ? Requirement.NOT_APPLICABLE : Requirement.OPTIONAL;
							default -> Requirement.OPTIONAL;
						};
						// The row discount has no invoice column; it lives in the notes.
						String value = InvoiceItemRepairs.DISCOUNT_FIELD.equals(column) ? plain(discounts.get(row)) : itemValue(item, column);
						items.add(status(column, row, requirement, value, notes, parsed));
					}
				}
			}
		} else {
			String receiptKind = extraValue(result, "receiptKind");
			for (var entry : DocumentTypeSchema.fields(type, receiptKind).entrySet())
				fields.add(status(entry.getKey(), null, entry.getValue(), extraValue(result, entry.getKey()), notes, parsed));
		}
		String note = applied ? null
				: "Hóa đơn đã được kế toán chỉnh sửa; giá trị AI chỉ để đối chiếu, không ghi đè dữ liệu đã sửa";
		return new FieldValidationReport(VERSION, type, applied, note, List.copyOf(fields), List.copyOf(items), List.copyOf(notes),
				List.copyOf(itemDiscounts));
	}

	private static FieldStatus status(String field, Integer row, Requirement requirement, String value,
			List<AiFieldNote> notes, List<Warning> warnings) {
		// Several steps may touch one value: the first note holds what was read, the last one the current state.
		AiFieldNote first = null, note = null;
		for (AiFieldNote candidate : notes)
			if (field.equals(candidate.field()) && Objects.equals(row, candidate.row())) {
				if (first == null) first = candidate;
				note = candidate;
			}
		List<String> reasons = new ArrayList<>();
		for (Warning warning : warnings) {
			if (!field.equals(warning.affectedField()) && !(row != null && "items".equals(warning.affectedField()))) continue;
			Integer warningRow = row(warning.message());
			if (row == null ? warningRow != null : !row.equals(warningRow)) continue;
			if (!reasons.contains(warning.code())) reasons.add(warning.code());
		}
		if (note != null && note.reason() != null && !reasons.contains(note.reason())) reasons.add(note.reason());
		boolean present = value != null && !value.isBlank();
		String status;
		if (present && note != null) status = switch (note.status()) {
			case "NORMALIZED", "VALID" -> "PRESENT_NORMALIZED";
			case "REPAIRED" -> "REPAIRED_WITH_EVIDENCE";
			default -> note.status();
		};
		else if (present) status = "PRESENT_VALID";
		else if (note != null && Set.of("INVALID", "AMBIGUOUS", "CLEARED").contains(note.status())) status = note.status();
		else if (note != null && note.normalizedValue() != null && note.normalizedValue().startsWith("EXEMPT")
				|| note != null && "NOT_DECLARED_KKKNT".equals(note.normalizedValue())) status = "NOT_APPLICABLE";
		else status = switch (requirement) {
			case REQUIRED -> "MISSING_REQUIRED";
			case OPTIONAL -> "ABSENT_OPTIONAL";
			case NOT_APPLICABLE -> "NOT_APPLICABLE";
		};
		String raw = first != null ? first.rawValue() : value;
		return new FieldStatus(field, row, requirement.name(), status, raw, present ? value : null, List.copyOf(reasons));
	}

	private static Integer row(String message) {
		if (message == null) return null;
		Matcher matcher = ROW.matcher(message);
		return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
	}

	private static String extraValue(AiDocumentResult result, String name) {
		if (result.extraFields() == null) return null;
		return result.extraFields().stream().filter(f -> f != null && name.equals(f.name()))
				.map(AiDocumentResult.AiExtraField::value).filter(Objects::nonNull).findFirst().orElse(null);
	}

	private static String headerValue(AiInvoiceExtraction invoice, String name) {
		return switch (name) {
			case "invoiceNumber" -> invoice.invoiceNumber();
			case "invoiceSeries" -> invoice.invoiceSeries();
			case "invoiceDate" -> invoice.invoiceDate();
			case "sellerName" -> invoice.sellerName();
			case "sellerTaxCode" -> invoice.sellerTaxCode();
			case "sellerAddress" -> invoice.sellerAddress();
			case "sellerPhone" -> invoice.sellerPhone();
			case "buyerName" -> invoice.buyerName();
			case "buyerTaxCode" -> invoice.buyerTaxCode();
			case "buyerAddress" -> invoice.buyerAddress();
			case "subtotal" -> plain(invoice.subtotal());
			case "vatAmount" -> plain(invoice.vatAmount());
			case "totalAmount" -> plain(invoice.totalAmount());
			case "paymentMethod" -> invoice.paymentMethod();
			case "amountInWords" -> invoice.amountInWords();
			case "taxAuthorityCode" -> invoice.taxAuthorityCode();
			case "signDate" -> invoice.signDate();
			default -> null;
		};
	}

	private static String itemValue(AiInvoiceItemExtraction item, String column) {
		return switch (column) {
			case "productName" -> item.productName();
			case "quantity" -> plain(item.quantity());
			case "unit" -> item.unit();
			case "unitPrice" -> plain(item.unitPrice());
			case "taxRate" -> plain(item.taxRate());
			case "taxAmount" -> plain(item.taxAmount());
			case "amount" -> plain(item.amount());
			default -> null;
		};
	}

	private static String plain(BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros().toPlainString();
	}
}

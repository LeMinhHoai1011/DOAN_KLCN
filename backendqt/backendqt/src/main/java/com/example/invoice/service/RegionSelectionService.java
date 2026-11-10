package com.example.invoice.service;

import com.example.invoice.dto.document.RegionDtos.RegionProvenance;
import com.example.invoice.dto.document.RegionDtos.RegionRect;
import com.example.invoice.dto.document.RegionDtos.RegionSelectionBatchRequest;
import com.example.invoice.dto.document.RegionDtos.RegionSelectionRequest;
import com.example.invoice.dto.document.RegionDtos.RegionSelectionResponse;
import com.example.invoice.dto.document.RegionDtos.RegionTextResponse;
import com.example.invoice.dto.document.RegionDtos.RegionWord;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.ExtractedField;
import com.example.invoice.entity.FieldCorrection;
import com.example.invoice.entity.AuditLog;
import com.example.invoice.entity.ReviewStatus;
import com.example.invoice.entity.User;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.AuditLogRepository;
import com.example.invoice.repository.ExtractedFieldRepository;
import com.example.invoice.dto.invoice.ExtractedFieldResponse.FieldLocation;
import com.example.invoice.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.HashMap;
import com.example.invoice.repository.OCRResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the OCR words inside a rectangle the accountant drew on the preview, and records where a
 * manually picked value came from (source MANUAL_REGION). Coordinates are pixels of the original page.
 * Provenance is kept in the existing audit_logs table (entity type {@link #AUDIT_TYPE}); no table of its own.
 */
@Service
@RequiredArgsConstructor
public class RegionSelectionService {
	public static final String SOURCE_MANUAL_REGION = "MANUAL_REGION";
	public static final String AUDIT_TYPE = "DOCUMENT_REGION_SELECTION";
	public static final String AUDIT_ACTION = "FIELD_REGION_SELECTION";
	/** Invoice columns edited through the invoice form; their region provenance is recorded after save. */
	static final Set<String> INVOICE_REGION_FIELDS = Set.of(
			"invoiceNumber", "invoiceSeries", "invoiceDate", "sellerName", "sellerTaxCode", "sellerAddress",
			"sellerPhone", "buyerName", "buyerTaxCode", "buyerAddress", "subtotal", "vatAmount",
			"totalAmount", "paymentMethod", "amountInWords", "taxAuthorityCode", "signDate");
	static final double MIN_SIDE_PX = 4;
	static final double EDGE_TOLERANCE_PX = 1;
	/** A word belongs to the region when at least this share of its own box lies inside it. */
	static final double WORD_OVERLAP = 0.5;

	private final DocumentService documentService;
	private final UserService userService;
	private final OCRResultRepository ocrResults;
	private final AuditLogRepository auditLogs;
	private final ExtractedFieldRepository extractedFields;
	private final ObjectMapper json = new ObjectMapper();

	@Transactional(readOnly = true)
	public RegionTextResponse extractText(Long documentId, RegionRect rect, Authentication authentication) {
		requireCorrector(userService.loadCurrent(authentication));
		documentService.load(documentId); // company / ownership scope
		Page page = validatedPage(documentId, rect);
		List<RegionWord> inside = new ArrayList<>();
		for (JsonNode node : page.node.path("words")) {
			RegionWord word = word(node, page);
			if (word != null && overlapShare(word, rect) >= WORD_OVERLAP) inside.add(word);
		}
		List<List<RegionWord>> lines = groupLines(inside);
		List<String> lineTexts = lines.stream().map(line -> String.join(" ", line.stream().map(RegionWord::text).toList())).toList();
		return new RegionTextResponse(rect.page(), page.width, page.height, String.join("\n", lineTexts), lineTexts,
				lines.stream().flatMap(List::stream).toList());
	}

	@Transactional
	public List<RegionSelectionResponse> recordInvoiceSelections(Long documentId, RegionSelectionBatchRequest request,
			Authentication authentication) {
		User actor = userService.loadCurrent(authentication);
		requireCorrector(actor);
		Document document = documentService.lockForEdit(documentId);
		requireEditable(document);
		List<RegionSelectionResponse> saved = new ArrayList<>();
		for (RegionSelectionRequest item : request.selections()) {
			ExtractedField field = null;
			String fieldName = item.fieldName();
			if (item.fieldId() != null) {
				// Location only for an AI field: its value is unchanged (value changes go through the correction API).
				field = extractedFields.findById(item.fieldId())
						.filter(candidate -> candidate.getDocument() != null && documentId.equals(candidate.getDocument().getId()))
						.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trường trích xuất"));
				if (!"AI".equalsIgnoreCase(field.getSource()))
					throw new BadRequestException("Trường này không thuộc dữ liệu trích xuất có thể chỉnh sửa");
				fieldName = field.getFieldName();
			} else if (fieldName == null || !INVOICE_REGION_FIELDS.contains(fieldName)) {
				throw new BadRequestException("Trường không hỗ trợ chọn vùng: " + fieldName);
			}
			Page page = validatedPage(documentId, item.region());
			Map<String, Object> value = provenance(fieldName, item.region(), item.extractedText(), item.finalValue());
			page.addSize(value);
			if (field != null) { value.put("fieldId", field.getId()); value.put("locationOnly", true); }
			AuditLog event = auditLogs.save(event(document, actor, value));
			saved.add(new RegionSelectionResponse(event == null ? null : event.getId(), fieldName, item.region().page(),
					item.region().x(), item.region().y(), item.region().width(), item.region().height(),
					item.extractedText(), item.finalValue().trim(), SOURCE_MANUAL_REGION));
		}
		return saved;
	}

	/** Called inside the field-correction transaction; the correction's reason is already MANUAL_REGION. */
	public void recordForCorrection(Document document, ExtractedField field, FieldCorrection correction,
			RegionProvenance provenance, User actor) {
		Page page = validatedPage(document.getId(), provenance.region());
		Map<String, Object> value = provenance(field.getFieldName(), provenance.region(), provenance.extractedText(),
				correction.getNewValue());
		page.addSize(value);
		value.put("fieldId", field.getId());
		value.put("correctionId", correction.getId());
		auditLogs.save(event(document, actor, value));
	}

	static double overlapShare(RegionWord word, RegionRect rect) {
		double left = Math.max(word.x(), rect.x()), top = Math.max(word.y(), rect.y());
		double right = Math.min(word.x() + word.width(), rect.x() + rect.width());
		double bottom = Math.min(word.y() + word.height(), rect.y() + rect.height());
		double area = Math.max(0, right - left) * Math.max(0, bottom - top);
		double own = word.width() * word.height();
		return own <= 0 ? 0 : area / own;
	}

	/** Reading order: words whose vertical centres are within half a word height share a line. */
	static List<List<RegionWord>> groupLines(List<RegionWord> words) {
		List<RegionWord> sorted = new ArrayList<>(words);
		sorted.sort(Comparator.comparingDouble((RegionWord w) -> w.y() + w.height() / 2).thenComparingDouble(RegionWord::x));
		List<List<RegionWord>> lines = new ArrayList<>();
		List<RegionWord> current = null;
		double centre = 0, height = 0;
		for (RegionWord word : sorted) {
			double wordCentre = word.y() + word.height() / 2;
			if (current != null && Math.abs(wordCentre - centre) <= Math.max(1, Math.min(height, word.height()) / 2)) {
				current.add(word);
				centre = (centre * (current.size() - 1) + wordCentre) / current.size();
				height = Math.max(height, word.height());
			} else {
				current = new ArrayList<>(List.of(word));
				lines.add(current);
				centre = wordCentre; height = word.height();
			}
		}
		lines.forEach(line -> line.sort(Comparator.comparingDouble(RegionWord::x)));
		return lines;
	}

	private record Page(JsonNode node, double width, double height) {
		void addSize(Map<String, Object> value) { value.put("pageWidth", width); value.put("pageHeight", height); }
	}

	/**
	 * Latest manually drawn location per extracted field (by field id), read from the region-selection audit
	 * trail. Returned to the UI as a location with source MANUAL_REGION, so it survives a reload.
	 */
	@Transactional(readOnly = true)
	public Map<Long, FieldLocation> manualLocations(Long documentId) {
		Map<Long, FieldLocation> result = new HashMap<>();
		JsonNode layoutPages = null;
		for (AuditLog event : auditLogs.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(AUDIT_TYPE, documentId.toString())) {
			if (!AUDIT_ACTION.equals(event.getAction()) || event.getNewValue() == null) continue;
			JsonNode value;
			try { value = json.readTree(event.getNewValue()); }
			catch (JsonProcessingException unreadable) { continue; }
			if (!value.hasNonNull("fieldId") || result.containsKey(value.path("fieldId").asLong())) continue;
			int page = value.path("page").asInt(1);
			double pageWidth = value.path("pageWidth").asDouble(0), pageHeight = value.path("pageHeight").asDouble(0);
			if (pageWidth <= 0 || pageHeight <= 0) { // events recorded before page size was stored
				if (layoutPages == null) layoutPages = layoutPages(documentId);
				for (JsonNode node : layoutPages) if (node.path("page").asInt(node.path("pageNumber").asInt(1)) == page) {
					pageWidth = node.path("width").asDouble(node.path("imageWidth").asDouble());
					pageHeight = node.path("height").asDouble(node.path("imageHeight").asDouble());
				}
			}
			if (pageWidth <= 0 || pageHeight <= 0) continue;
			result.put(value.path("fieldId").asLong(), new FieldLocation(page,
					(int) Math.round(value.path("x").asDouble()), (int) Math.round(value.path("y").asDouble()),
					(int) Math.round(value.path("width").asDouble()), (int) Math.round(value.path("height").asDouble()),
					(int) Math.round(pageWidth), (int) Math.round(pageHeight), BigDecimal.ONE, FieldLocation.SOURCE_MANUAL_REGION));
		}
		return result;
	}

	private JsonNode layoutPages(Long documentId) {
		String layout = ocrResults.findFirstByDocumentIdOrderByProcessedAtDesc(documentId)
				.map(com.example.invoice.entity.OCRResult::getLayoutJson).orElse(null);
		if (layout == null || layout.isBlank()) return json.createArrayNode();
		try { JsonNode root = json.readTree(layout); return root.isArray() ? root : root.path("pages"); }
		catch (JsonProcessingException exception) { return json.createArrayNode(); }
	}

	private Page validatedPage(Long documentId, RegionRect rect) {
		if (rect == null || rect.page() == null || rect.x() == null || rect.y() == null || rect.width() == null || rect.height() == null)
			throw new BadRequestException("Thiếu toạ độ vùng chọn");
		for (double value : new double[] {rect.x(), rect.y(), rect.width(), rect.height()})
			if (!Double.isFinite(value)) throw new BadRequestException("Toạ độ vùng chọn không hợp lệ");
		if (rect.x() < 0 || rect.y() < 0) throw new BadRequestException("Toạ độ vùng chọn không hợp lệ");
		if (rect.width() < MIN_SIDE_PX || rect.height() < MIN_SIDE_PX) throw new BadRequestException("Vùng chọn quá nhỏ");
		String layout = ocrResults.findFirstByDocumentIdOrderByProcessedAtDesc(documentId)
				.map(com.example.invoice.entity.OCRResult::getLayoutJson).orElse(null);
		if (layout == null || layout.isBlank()) throw new BadRequestException("Chứng từ chưa có bố cục OCR để chọn vùng");
		JsonNode root;
		try { root = json.readTree(layout); }
		catch (JsonProcessingException exception) { throw new BadRequestException("Bố cục OCR của chứng từ bị lỗi"); }
		JsonNode pages = root.isArray() ? root : root.path("pages");
		for (JsonNode node : pages) {
			int number = node.has("page") ? node.path("page").asInt() : node.path("pageNumber").asInt(1);
			if (number != rect.page()) continue;
			double width = node.has("width") ? node.path("width").asDouble() : node.path("imageWidth").asDouble();
			double height = node.has("height") ? node.path("height").asDouble() : node.path("imageHeight").asDouble();
			if (width <= 0 || height <= 0) throw new BadRequestException("Bố cục OCR của trang thiếu kích thước");
			if (rect.x() + rect.width() > width + EDGE_TOLERANCE_PX || rect.y() + rect.height() > height + EDGE_TOLERANCE_PX)
				throw new BadRequestException("Vùng chọn vượt ra ngoài trang");
			return new Page(node, width, height);
		}
		throw new BadRequestException("Không tìm thấy trang " + rect.page() + " trong bố cục OCR");
	}

	private static RegionWord word(JsonNode node, Page page) {
		String text = node.path("text").asText("").trim();
		if (text.isEmpty()) return null;
		double x = node.path("x").asDouble(), y = node.path("y").asDouble();
		double w = node.path("width").asDouble(), h = node.path("height").asDouble();
		if (x <= 1 && y <= 1 && w <= 1 && h <= 1) { x *= page.width; y *= page.height; w *= page.width; h *= page.height; }
		if (w <= 0 || h <= 0) return null;
		Double confidence = node.hasNonNull("confidence") ? node.path("confidence").asDouble() : null;
		return new RegionWord(node.path("id").asText(null), text, confidence, x, y, w, h);
	}

	private static Map<String, Object> provenance(String fieldName, RegionRect rect, String extractedText, String finalValue) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("source", SOURCE_MANUAL_REGION);
		value.put("fieldName", fieldName);
		value.put("page", rect.page());
		value.put("x", rect.x()); value.put("y", rect.y());
		value.put("width", rect.width()); value.put("height", rect.height());
		value.put("extractedText", extractedText);
		value.put("finalValue", finalValue == null ? null : finalValue.trim());
		return value;
	}

	private AuditLog event(Document document, User actor, Map<String, Object> value) {
		AuditLog event = new AuditLog();
		event.setEntityType(AUDIT_TYPE);
		event.setEntityId(document.getId().toString());
		event.setCompany(document.getCompany());
		event.setUser(actor);
		event.setAction(AUDIT_ACTION);
		try { event.setNewValue(json.writeValueAsString(value)); }
		catch (JsonProcessingException exception) { throw new IllegalStateException("Không thể lưu vùng chọn", exception); }
		return event;
	}

	private static void requireEditable(Document document) {
		if (document.getStatus() != DocumentStatus.PROCESSED && document.getStatus() != DocumentStatus.NEED_REVIEW)
			throw new BadRequestException("Chỉ có thể chỉnh sửa trường khi chứng từ đang xử lý hoặc cần duyệt");
		if (document.getReviewStatus() == ReviewStatus.APPROVED)
			throw new BadRequestException("Chứng từ đã được duyệt và chỉ đọc");
	}

	static void requireCorrector(User user) {
		boolean allowed = user.getUserRoles().stream().map(assignment -> assignment.getRole().getCode())
				.anyMatch(code -> "ADMIN".equals(code) || "ACCOUNTANT".equals(code));
		if (!allowed) throw new AccessDeniedException("Chỉ kế toán hoặc quản trị viên mới có thể chọn vùng trên chứng từ");
	}
}

package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.invoice.entity.AuditLog;
import com.example.invoice.repository.AuditLogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldNameCatalogTest {
	@Test
	void foldsVietnameseBeforeBuildingTheKey() {
		assertEquals("soTienVietBangChuAmountInWords", FieldNameCatalog.key("Số tiền viết bằng chữ (Amount in words)"));
		assertEquals("dienThoaiDuongDayNong", FieldNameCatalog.key("Điện thoại đường dây nóng"));
		assertEquals("nganHangAcbChiNhanh1", FieldNameCatalog.key("  Ngân hàng: ACB / Chi nhánh #1 "));
		assertEquals("warehouseCode", FieldNameCatalog.key("warehouse code"));
		assertEquals("deliveryCode", FieldNameCatalog.key("DeliveryCode"));
		assertNull(FieldNameCatalog.key("  "));
		assertNull(FieldNameCatalog.key("123 abc"));
		assertNull(FieldNameCatalog.key("…"));
	}

	@Test
	void legacyKeyReproducesTheOldAccentDroppingNames() {
		assertEquals("sTiNViTBNgChAmountInWords", FieldNameCatalog.legacyKey("Số tiền viết bằng chữ (Amount in words)"));
		assertEquals("hNhThCThanhToN", FieldNameCatalog.legacyKey("Hình thức thanh toán"));
		assertEquals("mSThu", FieldNameCatalog.legacyKey("Mã số thuế"));
	}

	@Test
	void unambiguousInvoiceLabelsMapToInvoiceColumnsWhateverTheCaseOrAccents() {
		assertEquals("amountInWords", FieldNameCatalog.storageKey("Số tiền viết bằng chữ (Amount in words)"));
		assertEquals("amountInWords", FieldNameCatalog.storageKey("SO TIEN VIET BANG CHU"));
		assertEquals("paymentMethod", FieldNameCatalog.storageKey("Hình thức thanh toán (Payment method)"));
		assertEquals("taxAuthorityCode", FieldNameCatalog.storageKey("Mã của CQT"));
		assertEquals("taxAuthorityCode", FieldNameCatalog.storageKey("Mã cơ quan thuế cấp"));
		assertEquals("invoiceSeries", FieldNameCatalog.storageKey("Ký hiệu (Serial)"));
		assertEquals("signDate", FieldNameCatalog.storageKey("Ngày ký"));
		assertEquals("sellerTaxCode", FieldNameCatalog.storageKey("issuerTaxCode"));
		assertEquals("totalAmount", FieldNameCatalog.storageKey("Tổng cộng tiền thanh toán (Total payment)"));
	}

	@Test
	void sellerOrBuyerAmbiguousLabelsStayExtendedFields() {
		assertEquals("taxCode", FieldNameCatalog.storageKey("Mã số thuế"));
		assertEquals("address", FieldNameCatalog.storageKey("Địa chỉ"));
		assertEquals("unitName", FieldNameCatalog.storageKey("Tên đơn vị"));
		assertEquals("so", FieldNameCatalog.storageKey("Số"));
		assertEquals("ngay", FieldNameCatalog.storageKey("Ngày"));
	}

	@Test
	void duplicateKeysKeepEveryDistinctValue() {
		var names = new java.util.LinkedHashSet<String>(List.of("taxCode", "invoiceNumber", "keptManual"));
		var values = new java.util.HashMap<String, String>(java.util.Map.of("taxCode", "0318334251"));
		assertEquals("taxCode2", DocumentAiResultPersistenceService.availableName("taxCode", "3700650344", names, values));
		assertNull(DocumentAiResultPersistenceService.availableName("taxCode", " 0318334251 ", names, values));
		assertNull(DocumentAiResultPersistenceService.availableName("invoiceNumber", "X", names, values));
		assertNull(DocumentAiResultPersistenceService.availableName("keptManual", "X", names, values));
		assertEquals("fresh", DocumentAiResultPersistenceService.availableName("fresh", "X", names, values));
	}

	@Test
	void displayNamesComeFromCatalogThenAiSnapshotThenUniqueLegacyLabel() {
		AuditLogRepository audit = mock(AuditLogRepository.class);
		AuditLog ai = new AuditLog();
		ai.setAction("AI_EXTRACT");
		ai.setNewValue("""
				{"aiProposal":{"fields":[{"fieldName":"Số tài khoản ngân hàng","fieldValue":"1"},
				  {"fieldName":"Tên đơn vị","fieldValue":"A"},{"fieldName":"Tên đơn vị (bên mua)","fieldValue":"B"}],
				 "extraFields":[{"name":"MSĐVCQHVNS","label":"Mã số đơn vị có quan hệ với ngân sách","value":"x"}]}}
				""");
		when(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc("DOCUMENT_SNAPSHOT_V1", "7")).thenReturn(List.of(ai));
		var resolved = new ExtractedFieldNameResolver(audit).resolve(7L, List.of(
				"amountInWords", "taxCode2", "sTIKhoNNgNHNg", "soTaiKhoanNganHang", "msVcqhvns", "hNhThCThanhToN", "dN", "s", "unknownKey"));

		assertEquals("Số tiền bằng chữ", resolved.get("amountInWords").displayName());
		assertEquals("CATALOG", resolved.get("amountInWords").source());
		assertEquals("Mã số thuế (2)", resolved.get("taxCode2").displayName());
		assertEquals("Số tài khoản ngân hàng", resolved.get("sTIKhoNNgNHNg").displayName()); // legacy key of the snapshot name
		assertEquals("AI_ORIGINAL", resolved.get("sTIKhoNNgNHNg").source());
		assertEquals("Số tài khoản ngân hàng", resolved.get("soTaiKhoanNganHang").displayName()); // new key, same name
		assertEquals("Hình thức thanh toán", resolved.get("hNhThCThanhToN").displayName());
		assertEquals("LEGACY_LABEL", resolved.get("hNhThCThanhToN").source());
		for (String unresolved : List.of("dN", "s", "unknownKey")) {
			assertNull(resolved.get(unresolved).displayName(), unresolved);
			assertEquals("UNRESOLVED", resolved.get(unresolved).source());
		}
	}

	@Test
	void theTextThatProducedTheKeyWinsOverAnotherLabelTheAiAttached() {
		AuditLogRepository audit = mock(AuditLogRepository.class);
		AuditLog ai = new AuditLog();
		ai.setAction("AI_EXTRACT");
		ai.setNewValue("""
				{"aiProposal":{"fields":[{"fieldName":"Họ tên người mua hàng (Buyer)"}],
				 "extraFields":[{"name":"Họ tên người mua hàng (Buyer)","label":"Người mua hàng"},
				  {"name":"MÃ SỐ THUẾ (TAX CODE)","label":null},{"name":"Mã số thuế (Tax code)","label":"MST"},
				  {"name":"signer","label":"Ký bởi"}]}}
				""");
		when(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc("DOCUMENT_SNAPSHOT_V1", "8")).thenReturn(List.of(ai));
		var resolved = new ExtractedFieldNameResolver(audit).resolve(8L, List.of("hTNNgIMuaHNgBuyer", "mSThuTaxCode", "ngIMuaHNg", "signer"));
		assertEquals("Ký bởi", resolved.get("signer").displayName());
		assertEquals("Họ tên người mua hàng (Buyer)", resolved.get("hTNNgIMuaHNgBuyer").displayName());
		assertEquals("AI_ORIGINAL", resolved.get("hTNNgIMuaHNgBuyer").source());
		assertEquals("MÃ SỐ THUẾ (TAX CODE)", resolved.get("mSThuTaxCode").displayName()); // same text, other case
		assertEquals("Người mua hàng", resolved.get("ngIMuaHNg").displayName()); // key produced by the label itself
	}

	@Test
	void ambiguousSnapshotNamesAreNotGuessed() {
		var originals = new java.util.HashMap<String, java.util.Set<String>>();
		originals.put("tNNV", new java.util.LinkedHashSet<>(List.of("Tên đơn vị", "Tên nhân viên")));
		assertNull(ExtractedFieldNameResolver.fromOriginals("tNNV", originals));
		assertTrue(ExtractedFieldNameResolver.fromLegacyLabels("s") == null);
	}
}

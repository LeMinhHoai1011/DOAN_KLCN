package com.example.invoice.service;

import com.example.invoice.dto.invoice.InvoiceItemRequest;
import com.example.invoice.dto.invoice.InvoiceItemResponse;
import com.example.invoice.dto.invoice.InvoiceRequest;
import com.example.invoice.dto.invoice.InvoiceResponse;
import com.example.invoice.dto.invoice.ExtractedFieldResponse;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.ExtractedField;
import com.example.invoice.entity.Invoice;
import com.example.invoice.entity.InvoiceItem;
import com.example.invoice.entity.User;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.InvoiceRepository;
import com.example.invoice.repository.ExtractedFieldRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceService {
	private final InvoiceRepository invoiceRepository;
	private final ExtractedFieldRepository extractedFieldRepository;
	private final DocumentService documentService;
	private final UserService userService;
	private final OcrFieldLocator ocrFieldLocator;

	@Transactional
	public InvoiceResponse create(InvoiceRequest request) {
		Invoice invoice = new Invoice();
		apply(invoice, request);
		return toResponse(invoiceRepository.save(invoice));
	}

	@Transactional(readOnly = true)
	public List<InvoiceResponse> findAll() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		User user = userService.loadCurrent(auth);
		if (hasRole(user, "ADMIN")) {
			return invoiceRepository.findAll().stream().map(this::toResponse).toList();
		}
		if (hasRole(user, "ACCOUNTANT")) {
			if (user.getCompany() == null) return List.of();
			return invoiceRepository.findAllByDocumentCompanyId(user.getCompany().getId()).stream().map(this::toResponse).toList();
		}
		if (isEmployee(user)) {
			return invoiceRepository.findAllByDocumentUploadedById(user.getId()).stream().map(this::toResponse).toList();
		}
		if (user.getCompany() == null) return java.util.List.of();
		return invoiceRepository.findAllByDocumentCompanyId(user.getCompany().getId()).stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public InvoiceResponse findById(Long id) {
		return toResponse(load(id));
	}

	@Transactional(readOnly = true)
	public InvoiceResponse findByDocumentId(Long documentId) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		User user = userService.loadCurrent(auth);
		if (hasRole(user, "ADMIN")) {
			return invoiceRepository.findByDocumentId(documentId)
					.map(this::toResponse)
					.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn của chứng từ"));
		}
		if (hasRole(user, "ACCOUNTANT")) {
			if (user.getCompany() == null) throw new ResourceNotFoundException("Không tìm thấy hóa đơn của chứng từ");
			return invoiceRepository.findByDocumentIdAndDocumentCompanyId(documentId, user.getCompany().getId())
					.map(this::toResponse).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn của chứng từ"));
		}
		if (isEmployee(user)) {
			return invoiceRepository.findByDocumentIdAndDocumentUploadedById(documentId, user.getId())
					.map(this::toResponse)
					.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn của chứng từ"));
		}
		if (user.getCompany() == null) throw new ResourceNotFoundException("Không tìm thấy hóa đơn của chứng từ");
		return invoiceRepository.findByDocumentIdAndDocumentCompanyId(documentId, user.getCompany().getId())
				.map(this::toResponse)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn của chứng từ"));
	}

	@Transactional(readOnly = true)
	public List<ExtractedFieldResponse> findExtractedFieldsByDocumentId(Long documentId) {
		Document document = documentService.load(documentId);
		return extractedFieldRepository.findByDocumentId(document.getId()).stream()
				.map(this::toExtractedFieldResponse).toList();
	}

	@Transactional
	public InvoiceResponse update(Long id, InvoiceRequest request) {
		Invoice invoice = load(id);
		apply(invoice, request);
		return toResponse(invoice);
	}

	@Transactional
	public void delete(Long id) {
		invoiceRepository.delete(load(id));
	}

	private void apply(Invoice invoice, InvoiceRequest request) {
		Document document = documentService.load(request.documentId());
		invoice.setDocument(document);
		invoice.setInvoiceNumber(request.invoiceNumber());
		invoice.setInvoiceDate(request.invoiceDate());
		invoice.setSellerName(request.sellerName());
		invoice.setSellerTaxCode(request.sellerTaxCode());
		invoice.setSellerAddress(request.sellerAddress());
		invoice.setBuyerName(request.buyerName());
		invoice.setBuyerTaxCode(request.buyerTaxCode());
		invoice.setBuyerAddress(request.buyerAddress());
		invoice.setSubtotal(request.subtotal());
		invoice.setVatAmount(request.vatAmount());
		invoice.setTotalAmount(request.totalAmount());
		invoice.setAiGenerated(false);
		invoice.getItems().clear();
		if (request.items() != null) {
			for (InvoiceItemRequest itemRequest : request.items()) {
				InvoiceItem item = new InvoiceItem();
				item.setInvoice(invoice);
				item.setProductName(itemRequest.productName());
				item.setQuantity(itemRequest.quantity());
				item.setUnitPrice(itemRequest.unitPrice());
				item.setAmount(itemRequest.amount());
				invoice.getItems().add(item);
			}
		}
	}

	private Invoice load(Long id) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		User user = userService.loadCurrent(auth);
		if (hasRole(user, "ADMIN")) {
			return invoiceRepository.findById(id)
					.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn"));
		}
		if (hasRole(user, "ACCOUNTANT")) {
			if (user.getCompany() == null) throw new ResourceNotFoundException("Không tìm thấy hóa đơn");
			return invoiceRepository.findByIdAndDocumentCompanyId(id, user.getCompany().getId())
					.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn"));
		}
		if (isEmployee(user)) {
			return invoiceRepository.findByIdAndDocumentUploadedById(id, user.getId())
					.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn"));
		}
		if (user.getCompany() == null) throw new ResourceNotFoundException("Không tìm thấy hóa đơn");
		return invoiceRepository.findByIdAndDocumentCompanyId(id, user.getCompany().getId())
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn"));
	}

	private InvoiceResponse toResponse(Invoice invoice) {
		List<InvoiceItemResponse> items = invoice.getItems().stream()
				.map(item -> new InvoiceItemResponse(item.getId(), item.getProductName(), item.getQuantity(), item.getUnitPrice(), item.getAmount()))
				.toList();
		return new InvoiceResponse(invoice.getId(), invoice.getDocument().getId(), invoice.getInvoiceNumber(),
				invoice.getInvoiceDate(), invoice.getSellerName(), invoice.getSellerTaxCode(), invoice.getSellerAddress(),
				invoice.getBuyerName(), invoice.getBuyerTaxCode(), invoice.getBuyerAddress(), invoice.getSubtotal(),
				invoice.getVatAmount(), invoice.getTotalAmount(), items);
	}

	private ExtractedFieldResponse toExtractedFieldResponse(ExtractedField field) {
		return new ExtractedFieldResponse(field.getId(), field.getFieldName(), field.getFieldValue(), field.getSource(),
				field.getConfidence(), ocrFieldLocator.locate(field.getDocument().getId(), field.getFieldValue()));
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getUserRoles().stream()
				.anyMatch(assignment -> roleCode.equals(assignment.getRole().getCode()));
	}

	private boolean isEmployee(User user) {
		return hasRole(user, "EMPLOYEE") || hasRole(user, "USER");
	}
}


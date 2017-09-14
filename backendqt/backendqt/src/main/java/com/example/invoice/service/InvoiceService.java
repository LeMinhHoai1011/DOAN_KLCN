package com.example.invoice.service;

import com.example.invoice.dto.invoice.InvoiceItemRequest;
import com.example.invoice.dto.invoice.InvoiceItemResponse;
import com.example.invoice.dto.invoice.InvoiceRequest;
import com.example.invoice.dto.invoice.InvoiceResponse;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.Invoice;
import com.example.invoice.entity.InvoiceItem;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.InvoiceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceService {
	private final InvoiceRepository invoiceRepository;
	private final DocumentService documentService;

	@Transactional
	public InvoiceResponse create(InvoiceRequest request) {
		Invoice invoice = new Invoice();
		apply(invoice, request);
		return toResponse(invoiceRepository.save(invoice));
	}

	public List<InvoiceResponse> findAll() {
		return invoiceRepository.findAll().stream().map(this::toResponse).toList();
	}

	public InvoiceResponse findById(Long id) {
		return toResponse(load(id));
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
		return invoiceRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
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
}

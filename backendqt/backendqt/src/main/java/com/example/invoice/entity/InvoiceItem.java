package com.example.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "invoice_items")
public class InvoiceItem {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "item_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "invoice_id", nullable = false)
	private Invoice invoice;

	@Column(name = "item_name", nullable = false)
	private String productName;

	@Column(precision = 19, scale = 2)
	private BigDecimal quantity;

	private String unit;

	@Column(precision = 19, scale = 2)
	private BigDecimal unitPrice;

	@Column(precision = 5, scale = 2)
	private BigDecimal taxRate;

	@Column(precision = 19, scale = 2)
	private BigDecimal taxAmount;

	@Column(name = "total_amount", precision = 19, scale = 2)
	private BigDecimal amount;
}

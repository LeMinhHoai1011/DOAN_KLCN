package com.example.invoice.dto.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Explicit replacement: [] clears the table; omitted/null is rejected. Totals are preserved. */
public record InvoiceItemsUpdateRequest(
        @NotNull List<@NotNull @Valid InvoiceItemRequest> items) {}

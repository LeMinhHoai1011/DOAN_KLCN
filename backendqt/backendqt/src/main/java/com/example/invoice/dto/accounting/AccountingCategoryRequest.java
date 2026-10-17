package com.example.invoice.dto.accounting;
import com.example.invoice.entity.AccountingCategoryType;
import jakarta.validation.constraints.*;
public record AccountingCategoryRequest(
 @NotBlank @Size(max=50) @Pattern(regexp="[A-Za-z][A-Za-z0-9_]*", message="Mã chỉ gồm chữ, số và dấu gạch dưới") String categoryCode,
 @NotBlank @Size(max=200) String categoryName,
 @Size(max=255) String description,
 AccountingCategoryType categoryType,
 @NotNull Boolean active,
 @Positive Long companyId) {}

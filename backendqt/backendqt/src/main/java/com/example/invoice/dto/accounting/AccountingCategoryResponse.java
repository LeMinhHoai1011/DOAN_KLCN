package com.example.invoice.dto.accounting;
import com.example.invoice.entity.AccountingCategoryType;
public record AccountingCategoryResponse(Long id, String categoryCode, String categoryName, String description,
    Long companyId, AccountingCategoryType categoryType, boolean active) {
 public AccountingCategoryResponse(Long id, String code, String name, String description) {
   this(id, code, name, description, null, null, true);
 }
}

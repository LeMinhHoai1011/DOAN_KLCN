package com.example.invoice.dto.document;
import java.math.BigDecimal;
public record CompanyRoleResponse(String role, BigDecimal confidence, String reason) {}

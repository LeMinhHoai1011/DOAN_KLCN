package com.example.invoice.dto.document;
import jakarta.validation.constraints.NotBlank;
public record ReviewActionRequest(@NotBlank String action, String note) {}

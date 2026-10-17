package com.example.invoice.dto.accounting;
import java.time.LocalDateTime;
public record PaymentHistoryEvent(Long id,String action,Long actorId,LocalDateTime at,String oldValue,String newValue) {}

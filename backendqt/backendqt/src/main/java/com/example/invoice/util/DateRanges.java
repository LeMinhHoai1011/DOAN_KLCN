package com.example.invoice.util;
import java.time.LocalDate;
import com.example.invoice.exception.BadRequestException;
/** ISO calendar dates have no timezone; endpoints include both boundary days. */
public final class DateRanges {
 private DateRanges() {}
 public static void validate(LocalDate from, LocalDate to) {
  if(from!=null && to!=null && from.isAfter(to)) throw new BadRequestException("Ngày bắt đầu không được sau ngày kết thúc.");
 }
}

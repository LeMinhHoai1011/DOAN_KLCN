package com.example.invoice.util;
import com.example.invoice.dto.FinancialTimeSeriesResponse;
import com.example.invoice.exception.BadRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
/** Aggregate calendar dates, then fill absent buckets. Never convert LocalDate to an instant. */
public final class FinancialSeries {
 private FinancialSeries() {}
 public static List<FinancialTimeSeriesResponse> build(List<Object[]> rows, LocalDate from, LocalDate to, boolean monthly) {
  DateRanges.validate(from,to);
  var values=new TreeMap<LocalDate,BigDecimal[]>();
  for(var row:rows){LocalDate day=(LocalDate)row[0];if(day==null)continue;
   if((from!=null&&day.isBefore(from))||(to!=null&&day.isAfter(to)))continue;
   LocalDate key=monthly?day.withDayOfMonth(1):day;
   var sums=values.computeIfAbsent(key,k->new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO});
   sums[0]=sums[0].add((BigDecimal)row[1]);sums[1]=sums[1].add((BigDecimal)row[2]);
  }
  if(from==null)from=values.isEmpty()?null:values.firstKey();
  if(to==null)to=values.isEmpty()?null:values.lastKey();
  if(from==null||to==null)return List.of();
  if(monthly){from=from.withDayOfMonth(1);to=to.withDayOfMonth(1);}
  long size=monthly?java.time.temporal.ChronoUnit.MONTHS.between(from,to):java.time.temporal.ChronoUnit.DAYS.between(from,to);
  if(size>10000)throw new BadRequestException("Khoảng thời gian quá lớn. Vui lòng chọn kỳ tổng hợp ngắn hơn.");
  var result=new ArrayList<FinancialTimeSeriesResponse>();
  for(LocalDate day=from;!day.isAfter(to);day=monthly?day.plusMonths(1):day.plusDays(1)){
   var sums=values.getOrDefault(day,new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO});
   result.add(new FinancialTimeSeriesResponse(monthly?YearMonth.from(day).toString():day.toString(),sums[0],sums[1],sums[0].subtract(sums[1])));
  }
  return result;
 }
}

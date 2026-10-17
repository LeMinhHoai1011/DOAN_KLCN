package com.example.invoice.service;
import com.example.invoice.util.DateRanges;
import com.example.invoice.dto.invoice.InvoiceResponse;
import com.example.invoice.exception.BadRequestException;
import java.time.LocalDate;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DateContractTest {
 @Test void rangesIncludeLeapAndCrossYearDates(){
  assertDoesNotThrow(()->DateRanges.validate(LocalDate.of(2024,2,29),LocalDate.of(2024,3,1)));
  assertDoesNotThrow(()->DateRanges.validate(LocalDate.of(2025,12,31),LocalDate.of(2026,1,1)));
  assertDoesNotThrow(()->DateRanges.validate(null,null));
  assertThrows(BadRequestException.class,()->DateRanges.validate(LocalDate.of(2026,2,1),LocalDate.of(2026,1,31)));
 }
 @Test void excelInvoiceRangeIncludesBothDaysAndUsesCalendarDateStyle() throws Exception {
  InvoiceService invoices=mock(InvoiceService.class);
  FinancialTransactionService payments=mock(FinancialTransactionService.class);
  InvoiceResponse first=mock(InvoiceResponse.class),last=mock(InvoiceResponse.class),outside=mock(InvoiceResponse.class);
  when(first.invoiceDate()).thenReturn(LocalDate.of(2024,2,29));when(last.invoiceDate()).thenReturn(LocalDate.of(2024,3,1));when(outside.invoiceDate()).thenReturn(LocalDate.of(2024,3,2));
  when(invoices.findAll()).thenReturn(List.of(first,last,outside));
  ExcelExportService service=new ExcelExportService(invoices,payments);
  byte[] bytes=service.invoices(LocalDate.of(2024,2,29),LocalDate.of(2024,3,1),null);
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
   var sheet=book.getSheetAt(0);assertEquals(2,sheet.getLastRowNum());
   assertEquals("dd/mm/yyyy",sheet.getRow(1).getCell(2).getCellStyle().getDataFormatString());
   assertEquals(LocalDate.of(2024,2,29),sheet.getRow(1).getCell(2).getLocalDateTimeCellValue().toLocalDate());
   assertEquals(LocalDate.of(2024,3,1),sheet.getRow(2).getCell(2).getLocalDateTimeCellValue().toLocalDate());
  }
  assertThrows(BadRequestException.class,()->service.invoices(LocalDate.of(2024,3,1),LocalDate.of(2024,2,29),null));
 }
}

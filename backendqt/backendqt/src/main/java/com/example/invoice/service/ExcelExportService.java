package com.example.invoice.service;

import com.example.invoice.dto.accounting.FinancialTransactionResponse;
import com.example.invoice.dto.invoice.InvoiceResponse;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExcelExportService {
	private final InvoiceService invoiceService;
	private final FinancialTransactionService transactionService;

	@Transactional(readOnly = true)
	public byte[] invoices(LocalDate fromDate, LocalDate toDate, String search) {
		List<InvoiceResponse> rows = invoiceService.findAll().stream()
				.filter(i -> fromDate == null || (i.invoiceDate() != null && !i.invoiceDate().isBefore(fromDate)))
				.filter(i -> toDate == null || (i.invoiceDate() != null && !i.invoiceDate().isAfter(toDate)))
				.filter(i -> matches(i, search)).toList();
		try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = book.createSheet("Hoa don"); String[] headers = {"STT", "So hoa don", "Ngay hoa don", "Ben ban", "MST ben ban", "Ben mua", "MST ben mua", "Tien truoc thue", "Tien thue", "Tong thanh toan"};
			header(sheet, headers); int n = 1;
			for (InvoiceResponse i : rows) { Row r = sheet.createRow(n); number(r, 0, n++); text(r,1,i.invoiceNumber()); date(r,2,i.invoiceDate()); text(r,3,i.sellerName()); text(r,4,i.sellerTaxCode()); text(r,5,i.buyerName()); text(r,6,i.buyerTaxCode()); money(r,7,i.subtotal()); money(r,8,i.vatAmount()); money(r,9,i.totalAmount()); }
			widths(sheet, headers.length); book.write(out); return out.toByteArray();
		} catch (Exception e) { throw new IllegalStateException("Không thể tạo tệp Excel xuất hóa đơn", e); }
	}

	@Transactional(readOnly = true)
	public byte[] financialReport(LocalDate fromDate, LocalDate toDate, Authentication authentication) {
		List<FinancialTransactionResponse> rows = transactionService.findAll(fromDate, toDate, null, null, null, null, null, Pageable.unpaged(), authentication).getContent();
		BigDecimal income = rows.stream().filter(r -> "INCOME".equals(r.transactionType())).map(FinancialTransactionResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal expense = rows.stream().filter(r -> "EXPENSE".equals(r.transactionType())).map(FinancialTransactionResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
		try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet summary=book.createSheet("Tong quan"); header(summary,new String[]{"Tu ngay","Den ngay","Tong thu","Tong chi","Dong tien","So giao dich"}); Row s=summary.createRow(1); date(s,0,fromDate);date(s,1,toDate);money(s,2,income);money(s,3,expense);money(s,4,income.subtract(expense));number(s,5,rows.size()); widths(summary,6);
			Sheet details=book.createSheet("Thu chi"); header(details,new String[]{"STT","Ngay","Loai","So tien","Nhom chi phi","Mo ta","Trang thai"}); int n=1; for(FinancialTransactionResponse r:rows){Row row=details.createRow(n);number(row,0,n++);date(row,1,r.transactionDate());text(row,2,r.transactionType());money(row,3,r.amount());text(row,4,r.categoryName());text(row,5,r.description());text(row,6,r.status());} widths(details,7); book.write(out); return out.toByteArray();
		} catch (Exception e) { throw new IllegalStateException("Không thể tạo tệp Excel báo cáo tài chính", e); }
	}
	private boolean matches(InvoiceResponse i,String q){if(q==null||q.isBlank())return true;String x=q.toLowerCase();return join(i.invoiceNumber(),i.sellerName(),i.sellerTaxCode(),i.buyerName(),i.buyerTaxCode()).toLowerCase().contains(x);}
	private String join(String... v){return String.join(" ",java.util.Arrays.stream(v).map(x->x==null?"":x).toList());}
	private void header(Sheet s,String[] h){Row r=s.createRow(0);for(int i=0;i<h.length;i++){Cell c=r.createCell(i);c.setCellValue(h[i]);}}
	private void text(Row r,int i,String v){r.createCell(i).setCellValue(v==null?"":v);} private void number(Row r,int i,int v){r.createCell(i).setCellValue(v);} private void date(Row r,int i,LocalDate v){if(v!=null)r.createCell(i).setCellValue(v);} private void money(Row r,int i,BigDecimal v){r.createCell(i).setCellValue(v==null?0:v.doubleValue());}
	private void widths(Sheet s,int n){for(int i=0;i<n;i++)s.autoSizeColumn(i);}
}

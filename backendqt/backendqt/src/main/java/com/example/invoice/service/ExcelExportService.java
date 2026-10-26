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
    @org.springframework.beans.factory.annotation.Autowired private RetailInvoiceDetailsService retailDetails;
	private final InvoiceService invoiceService;
	private final FinancialTransactionService transactionService;
    private final DocumentService documentService;

    /** Same query/scope/date/search as the document list, across all matching pages. */
    @Transactional(readOnly = true)
    public byte[] documents(LocalDate dateFrom, LocalDate dateTo,
            com.example.invoice.entity.DocumentStatus processingStatus,
            com.example.invoice.entity.ReviewStatus reviewStatus, Long typeId, String search,
            org.springframework.data.domain.Sort sort, Authentication authentication) {
        var documents = documentService.findPage(dateFrom, dateTo, null, processingStatus, reviewStatus,
                typeId, null, search, Pageable.unpaged(sort), authentication).getContent();
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Chung tu");
            String[] headers = {"ID chung tu", "Ten tep", "Ngay tai len", "Loai chung tu", "Trang thai xu ly",
                    "Trang thai duyet", "So hoa don", "Ngay hoa don", "Ben ban", "MST ben ban", "Ben mua",
                    "MST ben mua", "Tien truoc thue", "Tien thue", "Tong thanh toan", "Gio ban le",
                    "Giam gia bill", "Cach tinh giam gia", "Thue ban le", "Cach tinh thue", "Phi ban le",
                    "Cach tinh phi", "Tien khach dua", "Tien thua", "Can xac nhan lai can cu ban le"};
            header(sheet, headers);
            int n = 1;
            for (var document : documents) {
                Row row = sheet.createRow(n++);
                text(row, 0, document.id().toString()); text(row, 1, document.originalFileName());
                text(row, 2, document.createdAt() == null ? null : document.createdAt().toString());
                text(row, 3, document.documentType());
                text(row, 4, document.processingStatus() == null ? null : document.processingStatus().name());
                text(row, 5, document.reviewStatus() == null ? null : document.reviewStatus().name());
                InvoiceResponse invoice;
                try { invoice = invoiceService.findByDocumentId(document.id()); }
                catch (com.example.invoice.exception.ResourceNotFoundException missingInvoice) { continue; }
                text(row, 6, invoice.invoiceNumber()); date(row, 7, invoice.invoiceDate());
                text(row, 8, invoice.sellerName()); text(row, 9, invoice.sellerTaxCode());
                text(row, 10, invoice.buyerName()); text(row, 11, invoice.buyerTaxCode());
                if (invoice.subtotal() != null) money(row, 12, invoice.subtotal());
                if (invoice.vatAmount() != null) money(row, 13, invoice.vatAmount());
                if (invoice.totalAmount() != null) money(row, 14, invoice.totalAmount());
                if ("RETAIL_INVOICE".equals(document.documentType()) && retailDetails!=null) {
                    var view=retailDetails.readForDocument(document.id());
                    text(row,24,Boolean.toString(view.stale() || view.details()==null));
                    if (view.details()!=null) {
                        var details=view.details(); text(row,15,details.transactionTime());
                        adjustment(row,16,details.billDiscount()); adjustment(row,18,details.tax()); adjustment(row,20,details.fees());
                        if (details.tenderedAmount()!=null) money(row,22,details.tenderedAmount());
                        if (details.changeAmount()!=null) money(row,23,details.changeAmount());
                    }
                }
            }
            widths(sheet, headers.length); book.write(out); return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tạo tệp Excel danh sách chứng từ", exception);
        }
    }

    private void adjustment(Row row,int column,com.example.invoice.dto.invoice.RetailInvoiceDetails.Adjustment adjustment) {
        if (adjustment==null) return;
        if (adjustment.amount()!=null) money(row,column,adjustment.amount());
        text(row,column+1,adjustment.treatment()==null ? null : adjustment.treatment().name());
    }

	@Transactional(readOnly = true)
	public byte[] invoices(LocalDate fromDate, LocalDate toDate, String search) {
        com.example.invoice.util.DateRanges.validate(fromDate, toDate);
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
	public byte[] financialReport(LocalDate fromDate, LocalDate toDate, Authentication authentication) { return financialReportScoped(fromDate,toDate,null,authentication); }
    @Transactional(readOnly=true)
    public byte[] financialReportScoped(LocalDate fromDate,LocalDate toDate,Long companyId,Authentication authentication) {
        com.example.invoice.util.DateRanges.validate(fromDate, toDate);
		List<FinancialTransactionResponse> all = transactionService.findAll(fromDate, toDate, null, null, null, null, companyId, Pageable.unpaged(), authentication).getContent();
        List<FinancialTransactionResponse> rows = all.stream().filter(t -> "CONFIRMED".equals(t.paymentConfirmation())).toList();
		BigDecimal income = rows.stream().filter(r -> "INCOME".equals(r.transactionType())).map(FinancialTransactionResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal expense = rows.stream().filter(r -> "EXPENSE".equals(r.transactionType())).map(FinancialTransactionResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
		try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet summary=book.createSheet("Tong quan"); header(summary,new String[]{"Tu ngay","Den ngay","Tong thu","Tong chi","Dong tien","So giao dich"}); Row s=summary.createRow(1); date(s,0,fromDate);date(s,1,toDate);money(s,2,income);money(s,3,expense);money(s,4,income.subtract(expense));number(s,5,rows.size()); widths(summary,6);
			Sheet details=book.createSheet("Thu chi"); header(details,new String[]{"STT","Ngay","Loai","So tien","Nhom chi phi","Mo ta","Trang thai"}); int n=1; for(FinancialTransactionResponse r:rows){Row row=details.createRow(n);number(row,0,n++);date(row,1,r.transactionDate());text(row,2,r.transactionType());money(row,3,r.amount());text(row,4,r.categoryName());text(row,5,r.description());text(row,6,r.status());} widths(details,7);
            Sheet excluded=book.createSheet("Khong tinh thanh toan");header(excluded,new String[]{"ID","Xac minh","Gia tri ban ghi - khong phai tien da tra","Chung tu ID","Hoa don ID","Trang thai nghiep vu"});int z=1;
            for(var r:all){if("CONFIRMED".equals(r.paymentConfirmation()))continue;Row row=excluded.createRow(z++);text(row,0,r.id().toString());text(row,1,r.paymentConfirmation());money(row,2,r.amount());text(row,3,r.documentId()==null?"":r.documentId().toString());text(row,4,r.invoiceId()==null?"":r.invoiceId().toString());text(row,5,r.status());}widths(excluded,6);
            book.write(out); return out.toByteArray();
		} catch (Exception e) { throw new IllegalStateException("Không thể tạo tệp Excel báo cáo tài chính", e); }
	}
	private boolean matches(InvoiceResponse i,String q){if(q==null||q.isBlank())return true;String x=q.toLowerCase();return join(i.invoiceNumber(),i.sellerName(),i.sellerTaxCode(),i.buyerName(),i.buyerTaxCode()).toLowerCase().contains(x);}
	private String join(String... v){return String.join(" ",java.util.Arrays.stream(v).map(x->x==null?"":x).toList());}
	private void header(Sheet s,String[] h){Row r=s.createRow(0);for(int i=0;i<h.length;i++){Cell c=r.createCell(i);c.setCellValue(h[i]);}}
	private void text(Row r,int i,String v){r.createCell(i).setCellValue(v==null?"":v);} private void number(Row r,int i,int v){r.createCell(i).setCellValue(v);} private void date(Row r,int i,LocalDate v){if(v!=null){Cell cell=r.createCell(i);cell.setCellValue(v);CellStyle style=r.getSheet().getWorkbook().createCellStyle();style.setDataFormat(r.getSheet().getWorkbook().createDataFormat().getFormat("dd/mm/yyyy"));cell.setCellStyle(style);}} private void money(Row r,int i,BigDecimal v){r.createCell(i).setCellValue(v==null?0:v.doubleValue());}
	private void widths(Sheet s,int n){for(int i=0;i<n;i++)s.autoSizeColumn(i);}
}

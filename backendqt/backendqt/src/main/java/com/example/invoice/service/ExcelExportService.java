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
    public byte[] documents(LocalDate dateFrom, LocalDate dateTo, Long companyId,
            com.example.invoice.entity.DocumentStatus processingStatus,
            com.example.invoice.entity.ReviewStatus reviewStatus, Long typeId, String search,
            org.springframework.data.domain.Sort sort, Authentication authentication) {
        var documents = documentService.findPage(dateFrom, dateTo, companyId, processingStatus, reviewStatus,
                typeId, null, search, Pageable.unpaged(sort), authentication).getContent();
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Chung tu");
            String[] headers = {"ID chung tu", "Ten tep", "Ngay tai len", "Loai chung tu", "Trang thai xu ly",
                    "Trang thai duyet", "So hoa don", "Ngay hoa don", "Ben ban", "MST ben ban", "Ben mua",
                    "MST ben mua", "Tien truoc thue", "Tien thue", "Tong thanh toan", "Gio ban le",
                    "Giam gia bill", "Cach tinh giam gia", "Thue ban le", "Cach tinh thue", "Phi ban le",
                    "Cach tinh phi", "Tien khach dua", "Tien thua", "Can xac nhan lai can cu ban le",
                    "ID hoa don", "Ky hieu hoa don", "Dia chi ben ban", "Dien thoai ben ban", "Dia chi ben mua",
                    "Phuong thuc thanh toan", "So tien bang chu", "Ma co quan thue", "Ngay ky",
                    "Dinh dang tep", "Kich thuoc tep", "ID cong ty", "ID loai chung tu", "Ten loai chung tu",
                    "Do tin cay AI", "ID nguoi tai len", "Ngay cap nhat", "Huong chung tu", "Co so tien dong ban le"};
            header(sheet, headers);
            Sheet items = book.createSheet("Chi tiet hang hoa");
            String[] itemHeaders = {"ID chung tu", "ID hoa don", "So hoa don", "STT dong", "ID mat hang",
                    "Ten hang hoa", "So luong", "Don vi", "Don gia", "Thue suat", "Tien thue", "Thanh tien", "Giam gia dong"};
            header(items, itemHeaders);
            Sheet fields = book.createSheet("Du lieu trich xuat");
            String[] fieldHeaders = {"ID chung tu", "Ten tep", "ID truong", "Ten truong", "Gia tri hien tai", "Nguon",
                    "Do tin cay", "Gia tri AI goc", "Gia tri da sua", "Da sua thu cong", "ID nguoi sua", "Thoi gian sua"};
            header(fields, fieldHeaders);
            Sheet locations = book.createSheet("Vi tri trich xuat");
            String[] locationHeaders = {"ID chung tu", "ID truong", "Ten truong", "Trang", "X", "Y", "Chieu rong",
                    "Chieu cao", "Chieu rong trang", "Chieu cao trang", "Do tin cay khop"};
            header(locations, locationHeaders);
            int n = 1;
            for (var document : documents) {
                Row row = sheet.createRow(n++);
                text(row, 0, document.id().toString()); text(row, 1, document.originalFileName());
                text(row, 2, document.createdAt() == null ? null : document.createdAt().toString());
                text(row, 3, document.documentType());
                text(row, 4, document.processingStatus() == null ? null : document.processingStatus().name());
                text(row, 5, document.reviewStatus() == null ? null : document.reviewStatus().name());
                text(row,34,document.fileType()); decimal(row,35,document.fileSize()==null?null:BigDecimal.valueOf(document.fileSize()));
                text(row,36,document.companyId()==null?null:document.companyId().toString());
                text(row,37,document.typeId()==null?null:document.typeId().toString()); text(row,38,document.documentTypeName());
                decimal(row,39,document.aiConfidence()); text(row,40,document.uploadedById()==null?null:document.uploadedById().toString());
                text(row,41,document.updatedAt()==null?null:document.updatedAt().toString()); text(row,42,document.documentDirection());
                // Extraction can exist even when no structured invoice has been created.
                for (var field : invoiceService.findExtractedFieldsByDocumentId(document.id(), true)) {
                    Row f=fields.createRow(fields.getLastRowNum()+1);
                    text(f,0,document.id().toString()); text(f,1,document.originalFileName()); text(f,2,field.id().toString());
                    text(f,3,field.fieldName()); text(f,4,field.fieldValue()); text(f,5,field.source()); decimal(f,6,field.confidence());
                    text(f,7,field.originalAiValue()); text(f,8,field.correctedValue()); text(f,9,Boolean.toString(field.manuallyCorrected()));
                    text(f,10,field.correctedById()==null?null:field.correctedById().toString());
                    text(f,11,field.correctedAt()==null?null:field.correctedAt().toString());
                    if(field.locations()!=null) for(var location:field.locations()) {
                        Row l=locations.createRow(locations.getLastRowNum()+1);
                        text(l,0,document.id().toString()); text(l,1,field.id().toString()); text(l,2,field.fieldName());
                        number(l,3,location.page()); number(l,4,location.x()); number(l,5,location.y()); number(l,6,location.width());
                        number(l,7,location.height()); number(l,8,location.pageWidth()); number(l,9,location.pageHeight()); decimal(l,10,location.matchConfidence());
                    }
                }
                InvoiceResponse invoice;
                try { invoice = invoiceService.findByDocumentId(document.id()); }
                catch (com.example.invoice.exception.ResourceNotFoundException missingInvoice) { continue; }
                text(row, 6, invoice.invoiceNumber()); date(row, 7, invoice.invoiceDate());
                text(row, 8, invoice.sellerName()); text(row, 9, invoice.sellerTaxCode());
                text(row, 10, invoice.buyerName()); text(row, 11, invoice.buyerTaxCode());
                if (invoice.subtotal() != null) money(row, 12, invoice.subtotal());
                if (invoice.vatAmount() != null) money(row, 13, invoice.vatAmount());
                if (invoice.totalAmount() != null) money(row, 14, invoice.totalAmount());
                text(row,25,invoice.id().toString()); text(row,26,invoice.invoiceSeries()); text(row,27,invoice.sellerAddress());
                text(row,28,invoice.sellerPhone()); text(row,29,invoice.buyerAddress()); text(row,30,invoice.paymentMethod());
                text(row,31,invoice.amountInWords()); text(row,32,invoice.taxAuthorityCode()); date(row,33,invoice.signDate());
                int itemNumber=0;
                if(invoice.items()!=null) for(var item:invoice.items()) {
                    Row line=items.createRow(items.getLastRowNum()+1);
                    text(line,0,document.id().toString()); text(line,1,invoice.id().toString()); text(line,2,invoice.invoiceNumber());
                    number(line,3,++itemNumber); text(line,4,item.id()==null?null:item.id().toString()); text(line,5,item.productName());
                    decimal(line,6,item.quantity()); text(line,7,item.unit()); decimal(line,8,item.unitPrice());
                    decimal(line,9,item.taxRate()); decimal(line,10,item.taxAmount()); decimal(line,11,item.amount());
                }
                if ("RETAIL_INVOICE".equals(document.documentType()) && retailDetails!=null) {
                    var view=retailDetails.readForDocument(document.id());
                    text(row,24,Boolean.toString(view.stale() || view.details()==null));
                    if (view.details()!=null) {
                        var details=view.details(); text(row,15,details.transactionTime());
                        text(row,43,details.lineAmountBasis()==null?null:details.lineAmountBasis().name());
                        if(details.lineDiscounts()!=null) for(var discount:details.lineDiscounts()) {
                            for(int r=1;r<=items.getLastRowNum();r++) {
                                Row line=items.getRow(r);
                                if(document.id().toString().equals(line.getCell(0).getStringCellValue())
                                        && line.getCell(3).getNumericCellValue()==discount.rowNumber()) decimal(line,12,discount.amount());
                            }
                        }
                        adjustment(row,16,details.billDiscount()); adjustment(row,18,details.tax()); adjustment(row,20,details.fees());
                        if (details.tenderedAmount()!=null) money(row,22,details.tenderedAmount());
                        if (details.changeAmount()!=null) money(row,23,details.changeAmount());
                    }
                }
            }
            widths(sheet, headers.length); widths(items,itemHeaders.length); widths(fields,fieldHeaders.length); widths(locations,locationHeaders.length);
            book.write(out); return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tạo tệp Excel danh sách chứng từ", exception);
        }
    }

    private void adjustment(Row row,int column,com.example.invoice.dto.invoice.RetailInvoiceDetails.Adjustment adjustment) {
        if (adjustment==null) return;
        if (adjustment.amount()!=null) money(row,column,adjustment.amount());
        text(row,column+1,adjustment.treatment()==null ? null : adjustment.treatment().name());
    }

    private void decimal(Row row, int column, BigDecimal value) {
        if(value!=null) money(row,column,value);
    }

	@Transactional(readOnly = true)
	public byte[] invoices(LocalDate fromDate, LocalDate toDate, String search) { return invoices(fromDate, toDate, null, search); }

	@Transactional(readOnly = true)
	public byte[] invoices(LocalDate fromDate, LocalDate toDate, Long companyId, String search) {
        com.example.invoice.util.DateRanges.validate(fromDate, toDate);
		List<InvoiceResponse> rows = invoiceService.findAll(companyId).stream()
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

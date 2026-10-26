package com.example.invoice.ai;

import com.example.invoice.dto.invoice.RetailInvoiceDetails;
import com.example.invoice.dto.invoice.RetailInvoiceDetails.*;
import java.math.*;
import java.time.LocalTime;
import java.util.*;

/** Shared by AI review and persisted reconciliation; never rewrites source amounts. */
public final class RetailInvoiceRules {
    private RetailInvoiceRules() {}
    public record Row(BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {}
    public record Check(String type, BigDecimal declared, BigDecimal calculated, boolean missing, String message) {}

    public static List<String> problems(RetailInvoiceDetails details, int rowCount) {
        var problems = new ArrayList<String>();
        if (details == null) return List.of("RETAIL_DETAILS_MISSING: Chưa có căn cứ tính tiền bán lẻ");
        if (details.transactionTime()!=null) try { LocalTime.parse(details.transactionTime()); }
        catch (RuntimeException e) { problems.add("RETAIL_TIME_INVALID: Giờ giao dịch phải theo HH:mm hoặc HH:mm:ss"); }
        if (details.lineAmountBasis()==null || details.lineAmountBasis()==LineAmountBasis.UNKNOWN)
            problems.add("RETAIL_LINE_BASIS_UNKNOWN: Chưa rõ thành tiền dòng trước hay sau giảm giá");
        if (details.lineDiscounts()==null) problems.add("RETAIL_LINE_DISCOUNTS_UNKNOWN: Chưa xác minh giảm giá dòng");
        else {
            var seen = new HashSet<Integer>();
            for (var discount : details.lineDiscounts()) {
                if (discount==null || discount.rowNumber()<1 || discount.rowNumber()>rowCount
                        || !seen.add(discount.rowNumber()) || !money(discount.amount()))
                    problems.add("RETAIL_LINE_DISCOUNT_INVALID: Dòng giảm giá trùng, sai vị trí hoặc sai số tiền");
            }
        }
        adjustment(details.billDiscount(),"billDiscount",problems);
        adjustment(details.tax(),"tax",problems); adjustment(details.fees(),"fees",problems);
        if (details.tenderedAmount()!=null && !money(details.tenderedAmount())) problems.add("RETAIL_CASH_INVALID: Tiền khách đưa không hợp lệ");
        if (details.changeAmount()!=null && !money(details.changeAmount())) problems.add("RETAIL_CASH_INVALID: Tiền thừa không hợp lệ");
        return problems;
    }
    private static boolean money(BigDecimal amount) {
        return amount!=null && amount.signum()>=0 && amount.scale()<=2 && amount.precision()-amount.scale()<=17;
    }
    private static void adjustment(Adjustment adjustment,String field,List<String> problems) {
        if (adjustment==null || adjustment.treatment()==null || adjustment.treatment()==Treatment.UNKNOWN) {
            problems.add("RETAIL_ADJUSTMENT_UNKNOWN: field="+field+"; chưa rõ khoản đã gồm hay cần cộng/trừ"); return;
        }
        if (adjustment.amount()!=null && !money(adjustment.amount())
                || adjustment.treatment()==Treatment.SEPARATE && adjustment.amount()==null
                || adjustment.treatment()==Treatment.NOT_PRESENT && adjustment.amount()!=null && adjustment.amount().signum()!=0)
            problems.add("RETAIL_ADJUSTMENT_INVALID: field="+field+"; số tiền không phù hợp cách tính");
    }
    private static BigDecimal separate(Adjustment adjustment) {
        return adjustment.treatment()==Treatment.SEPARATE ? adjustment.amount() : BigDecimal.ZERO;
    }
    public static List<Check> evaluate(RetailInvoiceDetails details,List<Row> rows,BigDecimal total,
            BigDecimal printedTax,boolean stale) {
        var problems = new ArrayList<>(problems(details,rows.size()));
        if (total!=null && total.signum()<0 || printedTax!=null && printedTax.signum()<0
                || rows.stream().anyMatch(row -> row.quantity()!=null && row.quantity().signum()<0
                    || row.unitPrice()!=null && row.unitPrice().signum()<0 || row.amount()!=null && row.amount().signum()<0))
            problems.add("RETAIL_NEGATIVE_AMOUNT: Dữ liệu bán lẻ âm cần kiểm tra thủ công");
        if (stale || !problems.isEmpty()) return List.of(new Check("RETAIL_METADATA",null,null,true,
                stale ? "Căn cứ tính tiền đã cũ sau hiệu chỉnh hóa đơn; cần xác nhận lại" : String.join("; ",problems)));
        var checks = new ArrayList<Check>();
        var discounts = new HashMap<Integer,BigDecimal>();
        details.lineDiscounts().forEach(d -> discounts.put(d.rowNumber(),d.amount()));
        BigDecimal sum = rows.isEmpty() ? null : BigDecimal.ZERO;
        for (int index=0;index<rows.size();index++) {
            Row row=rows.get(index); BigDecimal discount=discounts.getOrDefault(index+1,BigDecimal.ZERO);
            BigDecimal expected=row.quantity()==null || row.unitPrice()==null ? null : row.quantity().multiply(row.unitPrice());
            if (expected!=null && details.lineAmountBasis()==LineAmountBasis.AFTER_DISCOUNT) expected=expected.subtract(discount);
            checks.add(new Check("ITEM_"+(index+1)+"_AMOUNT",row.amount(),round(expected),expected==null || row.amount()==null,
                    "Bán lẻ: số lượng × đơn giá, trừ giảm giá dòng chỉ khi thành tiền đã gồm giảm giá"));
            if (sum!=null) sum=row.amount()==null ? null : sum.add(row.amount());
        }
        if (sum!=null) {
            if (details.lineAmountBasis()==LineAmountBasis.BEFORE_DISCOUNT)
                sum=sum.subtract(discounts.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));
            sum=sum.subtract(separate(details.billDiscount())).add(separate(details.tax())).add(separate(details.fees()));
        }
        checks.add(new Check("RETAIL_TOTAL",total,round(sum),sum==null || total==null,
                "Tổng dòng sau giảm giá − giảm giá bill riêng + thuế riêng + phí riêng; không cộng lại khoản INCLUDED"));
        if (printedTax!=null && details.tax().amount()!=null)
            checks.add(new Check("RETAIL_TAX",printedTax,details.tax().amount(),false,"Đối chiếu hai trường thuế cùng đọc từ bill"));
        else if (printedTax!=null && details.tax().treatment()==Treatment.NOT_PRESENT && printedTax.signum()!=0)
            checks.add(new Check("RETAIL_TAX",printedTax,BigDecimal.ZERO,false,"Có thuế nhưng căn cứ lại ghi không có khoản thuế"));
        if (details.tenderedAmount()!=null && details.changeAmount()!=null)
            checks.add(new Check("RETAIL_CASH_CHANGE",total,round(details.tenderedAmount().subtract(details.changeAmount())),total==null,
                    "Tiền khách đưa − tiền thừa; chỉ kiểm tra số học, không xác nhận đã thu/chi"));
        return checks;
    }
    private static BigDecimal round(BigDecimal value) { return value==null ? null : value.setScale(2,RoundingMode.HALF_UP); }
}

package com.example.invoice.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure VAT invoice checks, shared by document reconciliation and the offline evaluation. */
public final class InvoiceReconciliationRules {
    private InvoiceReconciliationRules() {}
    /** Invoices print whole dong, so a recalculated value may differ by less than one dong from rounding alone. */
    public static final BigDecimal ROUNDING_LIMIT = BigDecimal.ONE;
    public static final String MATCHED = "MATCHED";
    public static final String MISMATCH = "MISMATCH";
    public static final String INSUFFICIENT_DATA = "INSUFFICIENT_DATA";

    public record Row(BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate, BigDecimal taxAmount, BigDecimal amount) {}
    public record Check(String type, BigDecimal declared, BigDecimal calculated, boolean missing, String message) {}

    public static List<Check> evaluate(List<Row> rows, BigDecimal subtotal, BigDecimal vatAmount, BigDecimal totalAmount) {
        List<Check> result = new ArrayList<>();
        if (rows.isEmpty())
            result.add(new Check("ITEMS_PRESENT", null, null, true,
                    "Chưa đủ dữ liệu hàng hóa để đối soát; cần bổ sung ít nhất một dòng hàng hóa"));
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            result.add(new Check("ITEM_" + (index + 1) + "_AMOUNT", row.amount(), multiply(row.quantity(), row.unitPrice()),
                    row.quantity() == null || row.unitPrice() == null || row.amount() == null,
                    "Thành tiền dòng = số lượng × đơn giá; cần quantity, unitPrice và amount"));
            BigDecimal vat = lineTax(row.amount(), row.taxRate());
            result.add(new Check("ITEM_" + (index + 1) + "_VAT", row.taxAmount(), vat,
                    row.amount() == null || row.taxRate() == null || row.taxAmount() == null,
                    "VAT dòng theo thuế suất; cần amount, taxRate và taxAmount"));
        }
        BigDecimal itemSum = completeSum(rows.stream().map(Row::amount).toList());
        BigDecimal vatSum = completeSum(rows.stream().map(Row::taxAmount).toList());
        result.add(new Check("SUBTOTAL", subtotal, itemSum, subtotal == null || rows.isEmpty() || itemSum == null,
                "Tổng dòng so với tổng trước thuế; cần items[].amount và subtotal"));
        result.add(new Check("VAT_TOTAL", vatAmount, vatSum, vatAmount == null || rows.isEmpty() || vatSum == null,
                "Tổng VAT dòng, hỗ trợ nhiều thuế suất; cần items[].taxAmount và vatAmount"));
        BigDecimal total = subtotal == null || vatAmount == null ? null : round(subtotal.add(vatAmount));
        result.add(new Check("TOTAL", totalAmount, total, subtotal == null || vatAmount == null || totalAmount == null,
                "Tổng trước thuế + VAT so với thanh toán; cần subtotal, vatAmount và totalAmount"));
        return result;
    }

    public static String status(BigDecimal declared, BigDecimal calculated, boolean missing) {
        if (missing || declared == null || calculated == null) return INSUFFICIENT_DATA;
        return difference(declared, calculated).abs().compareTo(ROUNDING_LIMIT) < 0 ? MATCHED : MISMATCH;
    }

    public static BigDecimal difference(BigDecimal declared, BigDecimal calculated) {
        return round(calculated.subtract(declared));
    }

    /** Line calculations follow the invoice: whole dong, with a fraction from 0.5 rounded up. */
    public static BigDecimal toDong(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).setScale(2);
    }

    /** A row's tax is its amount times its rate, never a share of the invoice total. */
    public static BigDecimal lineTax(BigDecimal amount, BigDecimal taxRate) {
        BigDecimal rate = rate(taxRate);
        return amount == null || rate == null ? null : toDong(amount.multiply(rate));
    }

    /** Values up to 1 are fractions, larger values are percentages. */
    private static BigDecimal rate(BigDecimal value) {
        return value == null ? null : value.abs().compareTo(BigDecimal.ONE) <= 0 ? value : value.movePointLeft(2);
    }

    private static BigDecimal multiply(BigDecimal left, BigDecimal right) {
        return left == null || right == null ? null : toDong(left.multiply(right));
    }

    private static BigDecimal completeSum(List<BigDecimal> values) {
        if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) return null;
        return round(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

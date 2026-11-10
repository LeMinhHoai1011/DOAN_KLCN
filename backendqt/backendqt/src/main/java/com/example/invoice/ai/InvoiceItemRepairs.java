package com.example.invoice.ai;

import com.example.invoice.dto.ai.AiDocumentResult.AiFieldNote;
import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceExtraction;
import com.example.invoice.dto.ai.AiDocumentResult.AiInvoiceItemExtraction;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic clean-up of the goods rows the model returns, applied once, in validation, so the stored invoice,
 * the review screen and the benchmark all see the same rows. Persistence never repeats it.
 *
 * <p>A value is changed only with two independent pieces of evidence, and the value before the change is kept as
 * a field note. Anything weaker is left as returned and reported for the accountant.
 *
 * <ol>
 *   <li>Wrapped name lines: a row with a name but no number at all continues the previous row's name, unless the
 *       text reads as its own line (promotion, gift, discount).</li>
 *   <li>Unit glued to the name ("Ống 27 x 4m Mét", "Dây CV 2.5 Mét 200"): moved to the unit column.</li>
 *   <li>Decimal comma read as grouping (×1000 too large): applied when quantity × price ÷ 1000 equals the printed
 *       amount exactly and exactly one of them is a whole thousand. A near match (a possible discount) is only
 *       reported.</li>
 *   <li>Thousands dot read as a decimal point (179.629 for 179.629 đ): scaled ×1000 only when the printed subtotal
 *       confirms it.</li>
 *   <li>Row tax: never derived. Row taxes that are all copies of the invoice tax total are cleared (they are not
 *       row values); other row taxes that miss the printed total are kept and reported, with amount × rate as a
 *       suggestion in the field notes.</li>
 * </ol>
 */
public final class InvoiceItemRepairs {
    private InvoiceItemRepairs() {}

    public static final String ROWS_MERGED = "ITEM_ROWS_MERGED";
    public static final String UNIT_SPLIT = "ITEM_UNIT_SPLIT";
    public static final String SCALE_REPAIRED = "ITEM_SCALE_REPAIRED";
    /** Row taxes that only repeated the invoice total were cleared. */
    public static final String ROW_TAX_CLEARED = "ITEM_ROW_TAX_CLEARED";
    /** Kept for warnings stored by earlier versions; no longer produced. */
    public static final String ROW_TAX_DERIVED = "ITEM_ROW_TAX_DERIVED";
    /** Reason of a field note: the row tax was a copy of the row amount. */
    public static final String ROW_TAX_COPIED_AMOUNT = "ROW_TAX_COPIED_FROM_AMOUNT";
    /** A row discount was found in the tax column and moved. */
    public static final String DISCOUNT_IN_TAX_COLUMN = "ITEM_DISCOUNT_IN_TAX_COLUMN";
    /** Field name of a row discount in the notes and the field report (the invoice has no discount column). */
    public static final String DISCOUNT_FIELD = "discountAmount";
    public static final Set<String> CODES = Set.of(ROWS_MERGED, UNIT_SPLIT, SCALE_REPAIRED, ROW_TAX_CLEARED, ROW_TAX_DERIVED,
            DISCOUNT_IN_TAX_COLUMN);
    /** Not repairs: these need a person and stay critical. */
    public static final String SCALE_SUSPECT = "ITEM_SCALE_SUSPECT";
    public static final String ROW_TAX_TOTAL_MISMATCH = "ITEM_ROW_TAX_TOTAL_MISMATCH";

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    /** Units printed on Vietnamese invoices, compared without accents and case. */
    private static final Set<String> UNITS = Set.of("met", "md", "m2", "m3", "cai", "chiec", "bo", "tam", "cuon", "kg",
            "goi", "hop", "thung", "chai", "lit", "chuyen", "cay", "soi", "doi", "vien", "ram", "quyen", "bao", "tui",
            "pcs", "set", "box", "lo", "can", "thanh", "tan", "khoi", "kien", "bich");
    private static final Pattern TRAILING_WORD = Pattern.compile("(.*\\S)\\s+(\\S+)", Pattern.DOTALL);
    private static final Pattern NUMBER = Pattern.compile("[\\d.,]+");
    /** Text-only lines that are rows of their own, not the rest of the previous name. */
    private static final Pattern STANDALONE_LINE = Pattern.compile(
            "(khuyenmai|khuyenmai|quatang|hangtang|chietkhau|giamgia|discount|free|mienphi|hangmau|khongthutien)");

    public static AiInvoiceExtraction apply(AiInvoiceExtraction invoice, List<String> warnings) {
        return apply(invoice, warnings, new ArrayList<>());
    }

    /**
     * {@code notes} holds the notes already written for this invoice; row numbers in it are moved along with merged
     * rows, and a note is added for every value changed here.
     */
    public static AiInvoiceExtraction apply(AiInvoiceExtraction invoice, List<String> warnings, List<AiFieldNote> notes) {
        if (invoice == null || invoice.items() == null || invoice.items().isEmpty()) return invoice;
        List<AiInvoiceItemExtraction> rows = mergeWrappedRows(invoice.items(), warnings, notes);
        List<AiInvoiceItemExtraction> split = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) split.add(splitUnit(rows.get(index), warnings, notes, index + 1));
        rows = clearTaxCopiedFromAmount(split, warnings, notes);
        rows = moveDiscountOutOfTax(rows, invoice.vatAmount(), warnings, notes);
        rows = repairScale(rows, invoice.subtotal(), warnings, notes);
        AiInvoiceExtraction withRows = withItems(invoice, rows);
        return withItems(withRows, reviewRowTax(withRows, warnings, notes));
    }

    /**
     * Invoices that print VAT only once, under the totals, have no row tax column; the model then copies the row's
     * own "Thành tiền" into taxAmount. A row tax equal to the row amount is impossible (VAT is at most 10%), so it is
     * cleared. Tax stays at invoice level (printed rate × subtotal); amount × rate is kept only as a suggestion.
     */
    static List<AiInvoiceItemExtraction> clearTaxCopiedFromAmount(List<AiInvoiceItemExtraction> rows, List<String> warnings,
            List<AiFieldNote> notes) {
        List<AiInvoiceItemExtraction> result = new ArrayList<>(rows);
        int cleared = 0;
        for (int index = 0; index < rows.size(); index++) {
            AiInvoiceItemExtraction row = rows.get(index);
            if (row == null || row.taxAmount() == null || row.amount() == null || row.amount().signum() <= 0
                    || row.taxAmount().compareTo(row.amount()) != 0) continue;
            BigDecimal suggestion = InvoiceReconciliationRules.lineTax(row.amount(), row.taxRate());
            notes.add(new AiFieldNote("taxAmount", index + 1, row.taxAmount().toPlainString(),
                    suggestion == null ? null : suggestion.stripTrailingZeros().toPlainString(), "CLEARED", ROW_TAX_COPIED_AMOUNT));
            result.set(index, new AiInvoiceItemExtraction(row.productName(), row.quantity(), row.unit(), row.unitPrice(),
                    row.taxRate(), null, row.amount()));
            cleared++;
        }
        if (cleared > 0) warnings.add(ROW_TAX_CLEARED + ": Tiền thuế của " + cleared + " dòng bằng đúng thành tiền (bị chép từ cột "
                + "Thành tiền); hóa đơn chỉ in thuế ở phần tổng nên đã để trống tiền thuế dòng, giá trị gợi ý nằm trong báo cáo trường");
        return result;
    }

    /** One-based row → printed row discount, from the notes (returned by the model or moved out of the tax column). */
    public static java.util.Map<Integer, BigDecimal> discounts(List<AiFieldNote> notes) {
        java.util.Map<Integer, BigDecimal> result = new java.util.HashMap<>();
        for (AiFieldNote note : notes) {
            if (!DISCOUNT_FIELD.equals(note.field()) || note.row() == null || note.normalizedValue() == null) continue;
            try { result.put(note.row(), new BigDecimal(note.normalizedValue())); }
            catch (NumberFormatException ignored) { /* Not a usable amount. */ }
        }
        return result;
    }

    /**
     * Invoices with a "Tiền chiết khấu" column: the model has no discount field and puts the discount in taxAmount,
     * often with the quantity's decimal comma dropped ("3,000" → 3000). Moved only when every row with a row tax
     * shows the same pattern and three independent checks agree:
     * <ol>
     *   <li>quantity × price − that value = the printed amount, exactly (with the quantity ÷ 1000 when needed);</li>
     *   <li>that value is not amount × rate;</li>
     *   <li>the row values do not add up to the printed tax total.</li>
     * </ol>
     * The value then becomes the row discount (kept in the notes, the invoice has no discount column) and the row
     * tax is left empty; a quantity read ×1000 is set back. Otherwise nothing changes.
     */
    static List<AiInvoiceItemExtraction> moveDiscountOutOfTax(List<AiInvoiceItemExtraction> rows, BigDecimal vatAmount,
            List<String> warnings, List<AiFieldNote> notes) {
        java.util.Map<Integer, BigDecimal> quantities = new java.util.HashMap<>();
        BigDecimal taxSum = BigDecimal.ZERO;
        int candidates = 0;
        for (int index = 0; index < rows.size(); index++) {
            AiInvoiceItemExtraction row = rows.get(index);
            if (row == null || row.taxAmount() == null || row.taxAmount().signum() <= 0) continue;
            if (row.quantity() == null || row.unitPrice() == null || row.amount() == null) return rows;
            BigDecimal rowTax = InvoiceReconciliationRules.lineTax(row.amount(), row.taxRate());
            if (rowTax != null && rowTax.subtract(row.taxAmount()).abs().compareTo(InvoiceReconciliationRules.ROUNDING_LIMIT) < 0)
                return rows;
            BigDecimal quantity = quantityExplainingDiscount(row);
            if (quantity == null) return rows;
            quantities.put(index, quantity);
            taxSum = taxSum.add(row.taxAmount());
            candidates++;
        }
        if (candidates == 0) return rows;
        if (vatAmount != null && taxSum.subtract(vatAmount).abs().compareTo(BigDecimal.valueOf(candidates)) < 0) return rows;
        List<AiInvoiceItemExtraction> result = new ArrayList<>(rows);
        boolean rescaled = false;
        for (var entry : quantities.entrySet()) {
            int index = entry.getKey();
            AiInvoiceItemExtraction row = rows.get(index);
            BigDecimal quantity = entry.getValue();
            notes.add(new AiFieldNote(DISCOUNT_FIELD, index + 1, row.taxAmount().toPlainString(),
                    row.taxAmount().stripTrailingZeros().toPlainString(), "REPAIRED", DISCOUNT_IN_TAX_COLUMN));
            notes.add(new AiFieldNote("taxAmount", index + 1, row.taxAmount().toPlainString(), null, "CLEARED", DISCOUNT_IN_TAX_COLUMN));
            if (quantity.compareTo(row.quantity()) != 0) {
                rescaled = true;
                notes.add(new AiFieldNote("quantity", index + 1, row.quantity().toPlainString(),
                        quantity.stripTrailingZeros().toPlainString(), "REPAIRED", SCALE_REPAIRED));
            }
            result.set(index, new AiInvoiceItemExtraction(row.productName(), quantity, row.unit(), row.unitPrice(),
                    row.taxRate(), null, row.amount()));
        }
        warnings.add(DISCOUNT_IN_TAX_COLUMN + ": Giá trị ở cột tiền thuế của " + candidates + " dòng là tiền chiết khấu "
                + "(số lượng × đơn giá − giá trị = thành tiền); đã chuyển sang chiết khấu dòng và để trống tiền thuế dòng"
                + (rescaled ? "; số lượng đọc thiếu dấu phẩy thập phân (×1000) đã được sửa" : ""));
        return result;
    }

    /** The quantity (as read, or ÷1000 when it is a whole thousand) for which quantity × price − tax = amount. */
    private static BigDecimal quantityExplainingDiscount(AiInvoiceItemExtraction row) {
        List<BigDecimal> options = new ArrayList<>();
        options.add(row.quantity());
        if (row.quantity().compareTo(THOUSAND) >= 0 && row.quantity().remainder(THOUSAND).signum() == 0)
            options.add(row.quantity().movePointLeft(3));
        for (BigDecimal quantity : options) {
            BigDecimal net = InvoiceReconciliationRules.toDong(quantity.multiply(row.unitPrice()).subtract(row.taxAmount()));
            if (net.subtract(row.amount()).abs().compareTo(InvoiceReconciliationRules.ROUNDING_LIMIT) < 0) return quantity;
        }
        return null;
    }

    static List<AiInvoiceItemExtraction> mergeWrappedRows(List<AiInvoiceItemExtraction> items, List<String> warnings) {
        return mergeWrappedRows(items, warnings, new ArrayList<>());
    }

    static List<AiInvoiceItemExtraction> mergeWrappedRows(List<AiInvoiceItemExtraction> items, List<String> warnings,
            List<AiFieldNote> notes) {
        List<AiInvoiceItemExtraction> rows = new ArrayList<>();
        int[] newRow = new int[items.size() + 1];
        int merged = 0;
        for (int index = 0; index < items.size(); index++) {
            AiInvoiceItemExtraction row = items.get(index);
            if (row == null) { newRow[index + 1] = rows.size(); continue; }
            boolean nameOnly = row.productName() != null && !row.productName().isBlank() && row.quantity() == null
                    && row.unitPrice() == null && row.amount() == null && row.taxAmount() == null && row.taxRate() == null;
            AiInvoiceItemExtraction previous = rows.isEmpty() ? null : rows.getLast();
            // A previous name that already ends with its own unit ("Chỉ 122 (21 x 0.8) mm Cuộn") is complete: the text-only
            // line is then the rest of a row the model lost, not of this one, and is left for the accountant.
            if (nameOnly && previous != null && previous.productName() != null && !endsWithOwnUnit(previous)
                    && !STANDALONE_LINE.matcher(fold(row.productName())).find()) {
                rows.removeLast();
                String name = previous.productName().strip() + " " + row.productName().strip();
                rows.add(new AiInvoiceItemExtraction(name, previous.quantity(), blank(previous.unit()) ? row.unit() : previous.unit(),
                        previous.unitPrice(), previous.taxRate(), previous.taxAmount(), previous.amount()));
                notes.add(new AiFieldNote("productName", rows.size(), row.productName(), name, "REPAIRED", ROWS_MERGED));
                merged++;
            } else {
                rows.add(row);
            }
            newRow[index + 1] = rows.size();
        }
        if (merged > 0) warnings.add(ROWS_MERGED + ": Đã gộp " + merged + " dòng chỉ có chữ vào tên hàng của dòng phía trên");
        // Empty (null) rows are dropped, as before; notes follow the rows to their new numbers.
        if (merged > 0 || rows.size() != items.size()) renumber(notes, newRow);
        return rows;
    }

    private static boolean endsWithOwnUnit(AiInvoiceItemExtraction row) {
        if (blank(row.unit())) return false;
        Matcher last = TRAILING_WORD.matcher(row.productName().strip());
        return last.matches() && fold(last.group(2)).equals(fold(row.unit()));
    }

    /** Notes written before a merge point at the model's row numbers; they now point at the merged rows. */
    private static void renumber(List<AiFieldNote> notes, int[] newRow) {
        for (int index = 0; index < notes.size(); index++) {
            AiFieldNote note = notes.get(index);
            if (note.row() == null || ROWS_MERGED.equals(note.reason()) || note.row() >= newRow.length) continue;
            notes.set(index, new AiFieldNote(note.field(), newRow[note.row()], note.rawValue(), note.normalizedValue(),
                    note.status(), note.reason()));
        }
    }

    static AiInvoiceItemExtraction splitUnit(AiInvoiceItemExtraction row, List<String> warnings) {
        return splitUnit(row, warnings, new ArrayList<>(), 0);
    }

    static AiInvoiceItemExtraction splitUnit(AiInvoiceItemExtraction row, List<String> warnings, List<AiFieldNote> notes, int rowNumber) {
        if (row == null || row.productName() == null || row.productName().isBlank()) return row;
        // A number in the unit column means the columns shifted (existing rule).
        if (row.unit() != null && NUMBER.matcher(row.unit().strip()).matches()) {
            InvoiceItemUnit.NameAndUnit repaired = InvoiceItemUnit.repair(row.productName(), row.unit());
            notes.add(new AiFieldNote("unit", rowNumber, row.unit(), repaired.unit(), "REPAIRED", "UNIT_COLUMN_SHIFT"));
            if (!Objects.equals(repaired.productName(), row.productName()))
                notes.add(new AiFieldNote("productName", rowNumber, row.productName(), repaired.productName(), "REPAIRED", "UNIT_COLUMN_SHIFT"));
            return with(row, repaired.productName(), repaired.unit());
        }
        String name = row.productName().strip();
        // "<name> <unit> <quantity>": drop a trailing number equal to the row quantity first.
        Matcher last = TRAILING_WORD.matcher(name);
        if (last.matches() && NUMBER.matcher(last.group(2)).matches() && sameNumber(last.group(2), row.quantity())) {
            Matcher beforeNumber = TRAILING_WORD.matcher(last.group(1));
            if (beforeNumber.matches() && isUnitFor(beforeNumber.group(2), row.unit())) name = last.group(1);
        }
        Matcher trailing = TRAILING_WORD.matcher(name);
        if (!trailing.matches() || !isUnitFor(trailing.group(2), row.unit())) {
            if (name.equals(row.productName().strip())) return row;
            notes.add(new AiFieldNote("productName", rowNumber, row.productName(), name, "REPAIRED", UNIT_SPLIT));
            return with(row, name, row.unit());
        }
        String rest = trailing.group(1).strip();
        // Short names may end with a unit-like word that belongs to the name ("Thép tấm"): keep them.
        if (rest.split("\\s+").length < 2) return row;
        warnings.add(UNIT_SPLIT + ": Tách đơn vị \"" + trailing.group(2) + "\" khỏi tên hàng \"" + rest + "\"");
        notes.add(new AiFieldNote("productName", rowNumber, row.productName(), rest, "REPAIRED", UNIT_SPLIT));
        return with(row, rest, blank(row.unit()) ? trailing.group(2) : row.unit());
    }

    /** The word is this row's own unit, or (when the unit column is empty) a known unit word. */
    private static boolean isUnitFor(String word, String unit) {
        String folded = fold(word);
        return blank(unit) ? UNITS.contains(folded) : folded.equals(fold(unit));
    }

    static List<AiInvoiceItemExtraction> repairScale(List<AiInvoiceItemExtraction> rows, BigDecimal subtotal, List<String> warnings) {
        return repairScale(rows, subtotal, warnings, new ArrayList<>());
    }

    static List<AiInvoiceItemExtraction> repairScale(List<AiInvoiceItemExtraction> rows, BigDecimal subtotal, List<String> warnings,
            List<AiFieldNote> notes) {
        // Too large by 1000 (decimal comma read as grouping).
        List<AiInvoiceItemExtraction> result = new ArrayList<>();
        boolean tooLarge = false;
        // A printed row discount is part of the arithmetic: quantity × price = amount + discount.
        java.util.Map<Integer, BigDecimal> discounts = discounts(notes);
        for (int index = 0; index < rows.size(); index++) {
            AiInvoiceItemExtraction row = rows.get(index);
            if (row == null) { result.add(null); continue; }
            BigDecimal gross = row.amount() == null ? null : row.amount().add(discounts.getOrDefault(index + 1, BigDecimal.ZERO));
            InvoiceItemScale.QuantityAndPrice scale = InvoiceItemScale.repair(row.quantity(), row.unitPrice(), gross);
            boolean changed = scale.quantity() != row.quantity() || scale.unitPrice() != row.unitPrice();
            if (!changed) { result.add(row); continue; }
            BigDecimal recomputed = InvoiceReconciliationRules.toDong(scale.quantity().multiply(scale.unitPrice()));
            boolean exact = recomputed.subtract(gross).abs().compareTo(InvoiceReconciliationRules.ROUNDING_LIMIT) < 0;
            String field = scale.quantity() != row.quantity() ? "quantity" : "unitPrice";
            BigDecimal before = "quantity".equals(field) ? row.quantity() : row.unitPrice();
            BigDecimal after = "quantity".equals(field) ? scale.quantity() : scale.unitPrice();
            if (!exact) {
                // quantity × price only comes near the amount (perhaps a discount): one piece of evidence is not enough.
                notes.add(new AiFieldNote(field, index + 1, before.toPlainString(), after.stripTrailingZeros().toPlainString(),
                        "SUGGESTED", SCALE_SUSPECT));
                warnings.add(SCALE_SUSPECT + ": field=" + field + "; row=" + (index + 1) + "; " + before.toPlainString()
                        + " có thể lớn gấp 1000 lần (đề xuất " + after.stripTrailingZeros().toPlainString()
                        + ") nhưng thành tiền không khớp tuyệt đối; giữ nguyên để kế toán kiểm tra");
                result.add(row);
                continue;
            }
            tooLarge = true;
            notes.add(new AiFieldNote(field, index + 1, before.toPlainString(), after.stripTrailingZeros().toPlainString(),
                    "REPAIRED", SCALE_REPAIRED));
            result.add(new AiInvoiceItemExtraction(row.productName(), scale.quantity(), row.unit(), scale.unitPrice(),
                    row.taxRate(), row.taxAmount(), row.amount()));
        }
        if (tooLarge) warnings.add(SCALE_REPAIRED + ": Sửa số lượng/đơn giá lớn gấp 1000 lần do đọc nhầm dấu phẩy thập phân");
        // Too small by 1000 (thousands dot read as a decimal point): only when the printed subtotal says so.
        if (subtotal == null || subtotal.signum() <= 0) return result;
        BigDecimal original = BigDecimal.ZERO, scaled = BigDecimal.ZERO;
        boolean any = false;
        for (AiInvoiceItemExtraction row : result) {
            if (row == null) continue;
            if (row.amount() == null) return result;
            original = original.add(row.amount());
            boolean small = looksThousandsDotted(row.amount());
            any |= small;
            scaled = scaled.add(small ? row.amount().multiply(THOUSAND) : row.amount());
        }
        BigDecimal tolerance = BigDecimal.valueOf(result.size());
        if (!any || original.subtract(subtotal).abs().compareTo(tolerance) <= 0
                || scaled.subtract(subtotal).abs().compareTo(tolerance) > 0) return result;
        List<AiInvoiceItemExtraction> repaired = new ArrayList<>();
        for (int index = 0; index < result.size(); index++) {
            AiInvoiceItemExtraction row = result.get(index);
            if (row == null || !looksThousandsDotted(row.amount())) { repaired.add(row); continue; }
            int rowNumber = index + 1;
            BigDecimal amount = row.amount().multiply(THOUSAND).stripTrailingZeros();
            note(notes, "amount", rowNumber, row.amount(), amount);
            BigDecimal price = row.unitPrice();
            if (price != null && looksThousandsDotted(price)) {
                price = price.multiply(THOUSAND).stripTrailingZeros();
                note(notes, "unitPrice", rowNumber, row.unitPrice(), price);
            }
            BigDecimal quantity = row.quantity();
            // "50.000" read as 50000 while the amount was read as 179.629: the quantity is the other side of the same mistake.
            if (quantity != null && price != null && quantity.compareTo(THOUSAND) >= 0
                    && quantity.remainder(THOUSAND).signum() == 0
                    && quantity.movePointLeft(3).multiply(price).subtract(amount).abs().compareTo(BigDecimal.ONE) <= 0) {
                quantity = quantity.movePointLeft(3).stripTrailingZeros();
                note(notes, "quantity", rowNumber, row.quantity(), quantity);
            }
            BigDecimal tax = row.taxAmount();
            if (tax != null && looksThousandsDotted(tax)) {
                tax = tax.multiply(THOUSAND).stripTrailingZeros();
                note(notes, "taxAmount", rowNumber, row.taxAmount(), tax);
            }
            repaired.add(new AiInvoiceItemExtraction(row.productName(), quantity, row.unit(), price, row.taxRate(), tax, amount));
        }
        warnings.add(SCALE_REPAIRED + ": Sửa thành tiền nhỏ hơn 1000 lần do đọc dấu chấm ngăn nghìn thành dấu thập phân (khớp tổng tiền hàng)");
        return repaired;
    }

    private static void note(List<AiFieldNote> notes, String field, int row, BigDecimal before, BigDecimal after) {
        notes.add(new AiFieldNote(field, row, before.toPlainString(), after.toPlainString(), "REPAIRED", SCALE_REPAIRED));
    }

    /** 179.629 (three decimals, not a whole number): typical of "179.629" printed with a thousands dot. */
    private static boolean looksThousandsDotted(BigDecimal value) {
        if (value == null || value.signum() <= 0) return false;
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() > 0 && stripped.scale() <= 3 && value.movePointRight(3).stripTrailingZeros().scale() <= 0;
    }

    /**
     * Row taxes are checked against the printed tax total. When every row only repeats that total, they were copied
     * and are cleared. Otherwise they are kept: a mismatch is reported, never replaced by a computed value.
     */
    static List<AiInvoiceItemExtraction> reviewRowTax(AiInvoiceExtraction invoice, List<String> warnings, List<AiFieldNote> notes) {
        if (!InvoiceItemTax.rowTaxUnreliable(invoice)) return invoice.items();
        List<AiInvoiceItemExtraction> rows = invoice.items();
        long present = rows.stream().filter(Objects::nonNull).count();
        boolean copied = present > 1 && rows.stream().filter(Objects::nonNull)
                .allMatch(row -> row.taxAmount() != null && row.taxAmount().compareTo(invoice.vatAmount()) == 0);
        List<AiInvoiceItemExtraction> result = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            AiInvoiceItemExtraction row = rows.get(index);
            if (row == null) { result.add(null); continue; }
            BigDecimal suggestion = InvoiceReconciliationRules.lineTax(row.amount(), row.taxRate());
            if (copied) {
                notes.add(new AiFieldNote("taxAmount", index + 1, row.taxAmount().toPlainString(),
                        suggestion == null ? null : suggestion.toPlainString(), "CLEARED", ROW_TAX_CLEARED));
                result.add(new AiInvoiceItemExtraction(row.productName(), row.quantity(), row.unit(), row.unitPrice(),
                        row.taxRate(), null, row.amount()));
            } else {
                if (suggestion != null && row.taxAmount() != null && suggestion.compareTo(row.taxAmount()) != 0)
                    notes.add(new AiFieldNote("taxAmount", index + 1, row.taxAmount().toPlainString(), suggestion.toPlainString(),
                            "SUGGESTED", ROW_TAX_TOTAL_MISMATCH));
                result.add(row);
            }
        }
        if (copied) warnings.add(ROW_TAX_CLEARED + ": Tiền thuế các dòng chỉ lặp lại tổng thuế của hóa đơn; đã để trống, "
                + "giá trị đề xuất (thành tiền × thuế suất) nằm trong báo cáo trường");
        else warnings.add(ROW_TAX_TOTAL_MISMATCH + ": Tổng tiền thuế các dòng không bằng tổng thuế in trên hóa đơn; "
                + "giữ nguyên giá trị đọc được để kế toán kiểm tra");
        return result;
    }

    private static AiInvoiceItemExtraction with(AiInvoiceItemExtraction row, String name, String unit) {
        return new AiInvoiceItemExtraction(name, row.quantity(), unit, row.unitPrice(), row.taxRate(), row.taxAmount(), row.amount());
    }

    private static AiInvoiceExtraction withItems(AiInvoiceExtraction i, List<AiInvoiceItemExtraction> items) {
        return new AiInvoiceExtraction(i.invoiceNumber(), i.invoiceSeries(), i.invoiceDate(), i.sellerName(), i.sellerTaxCode(),
                i.sellerAddress(), i.sellerPhone(), i.buyerName(), i.buyerTaxCode(), i.buyerAddress(), i.subtotal(), i.vatAmount(),
                i.totalAmount(), i.paymentMethod(), i.amountInWords(), i.taxAuthorityCode(), i.signDate(), items, i.retailDetails());
    }

    private static boolean sameNumber(String printed, BigDecimal quantity) {
        if (quantity == null) return false;
        String digits = printed.replace(".", "").replace(",", "");
        try { return new BigDecimal(digits).compareTo(quantity) == 0; } catch (NumberFormatException e) { return false; }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static String fold(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return decomposed.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}

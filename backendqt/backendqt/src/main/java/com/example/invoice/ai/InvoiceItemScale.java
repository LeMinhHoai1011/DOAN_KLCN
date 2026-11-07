package com.example.invoice.ai;

import java.math.BigDecimal;

/**
 * Repairs a misread decimal comma: printed "50,000" (50) or "80.000,000" (80000) can come back a thousand times too
 * large. The printed row amount decides, and only an unambiguous factor changes; anything else is left for review.
 * A row amount may be lower than quantity × price by a discount, which rows do not store.
 */
public final class InvoiceItemScale {
    private InvoiceItemScale() {}

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal MAX_DISCOUNT_FACTOR = new BigDecimal("1.25");

    public record QuantityAndPrice(BigDecimal quantity, BigDecimal unitPrice) {}

    public static QuantityAndPrice repair(BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
        QuantityAndPrice unchanged = new QuantityAndPrice(quantity, unitPrice);
        if (quantity == null || unitPrice == null || amount == null || amount.signum() == 0) return unchanged;
        BigDecimal scaledDown = InvoiceReconciliationRules.toDong(quantity.multiply(unitPrice).movePointLeft(3));
        boolean exact = scaledDown.subtract(amount).abs().compareTo(InvoiceReconciliationRules.ROUNDING_LIMIT) < 0;
        boolean discounted = scaledDown.compareTo(amount) > 0 && scaledDown.compareTo(amount.multiply(MAX_DISCOUNT_FACTOR)) <= 0;
        if (!exact && !discounted) return unchanged;
        boolean quantityScaled = wholeThousands(quantity);
        boolean priceScaled = wholeThousands(unitPrice);
        if (quantityScaled == priceScaled) return unchanged;
        return quantityScaled
                ? new QuantityAndPrice(quantity.movePointLeft(3), unitPrice)
                : new QuantityAndPrice(quantity, unitPrice.movePointLeft(3));
    }

    private static boolean wholeThousands(BigDecimal value) {
        return value.signum() != 0 && value.remainder(THOUSAND).signum() == 0;
    }
}

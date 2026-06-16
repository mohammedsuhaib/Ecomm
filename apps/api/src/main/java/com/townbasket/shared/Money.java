package com.townbasket.shared;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable money value type shared across modules. Amounts are stored as
 * {@link BigDecimal} to avoid floating-point rounding on prices.
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final Currency INR = Currency.getInstance("INR");

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }

    public static Money inr(BigDecimal amount) {
        return new Money(amount, INR);
    }
}

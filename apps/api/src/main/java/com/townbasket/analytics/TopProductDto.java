package com.townbasket.analytics;

import java.math.BigDecimal;

/** Top-selling variant by quantity for the given period. */
public record TopProductDto(
        String productName,
        String variantLabel,
        int totalQty,
        BigDecimal totalRevenue) {}

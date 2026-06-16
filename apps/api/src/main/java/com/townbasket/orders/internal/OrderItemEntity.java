package com.townbasket.orders.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA entity for {@code orders.order_items}. Module-internal. Snapshots the
 * product name/label/price at the time of sale so catalog changes never mutate
 * history.
 *
 * <p><strong>cost_price is an INTERNAL COGS snapshot.</strong> It is persisted
 * for future gross-profit analytics and must NEVER be copied into any order DTO.
 *
 * <p>The owning {@code @ManyToOne} sets {@code order_id} on insert.
 */
@Entity
@Table(name = "order_items", schema = "orders")
class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private String label;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "cost_price", nullable = false)
    private BigDecimal costPrice;

    @Column(nullable = false)
    private int qty;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;

    protected OrderItemEntity() {
        // JPA
    }

    OrderItemEntity(Long variantId, String productName, String label,
                    BigDecimal unitPrice, BigDecimal costPrice, int qty, BigDecimal lineTotal) {
        this.variantId = variantId;
        this.productName = productName;
        this.label = label;
        this.unitPrice = unitPrice;
        this.costPrice = costPrice;
        this.qty = qty;
        this.lineTotal = lineTotal;
    }

    void setOrder(OrderEntity order) {
        this.order = order;
    }

    Long getVariantId() {
        return variantId;
    }

    String getProductName() {
        return productName;
    }

    String getLabel() {
        return label;
    }

    BigDecimal getUnitPrice() {
        return unitPrice;
    }

    int getQty() {
        return qty;
    }

    BigDecimal getLineTotal() {
        return lineTotal;
    }

    /** INTERNAL ONLY — COGS snapshot; must not be exposed in any API response. */
    BigDecimal getCostPrice() {
        return costPrice;
    }
}

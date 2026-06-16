package com.townbasket.cart.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA entity for {@code cart.cart_items}. Module-internal. {@code variant_id} is
 * a plain catalog id (no cross-schema FK). The owning {@code @ManyToOne} sets
 * {@code cart_id} on insert (a unidirectional {@code @OneToMany @JoinColumn}
 * would insert a null FK first and violate the NOT NULL constraint).
 */
@Entity
@Table(name = "cart_items", schema = "cart")
class CartItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(nullable = false)
    private int qty;

    protected CartItemEntity() {
        // JPA
    }

    CartItemEntity(CartEntity cart, Long variantId, int qty) {
        this.cart = cart;
        this.variantId = variantId;
        this.qty = qty;
    }

    Long getId() {
        return id;
    }

    Long getVariantId() {
        return variantId;
    }

    int getQty() {
        return qty;
    }

    void setQty(int qty) {
        this.qty = qty;
    }
}

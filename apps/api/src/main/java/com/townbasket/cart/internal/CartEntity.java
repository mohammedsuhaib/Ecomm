package com.townbasket.cart.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for {@code cart.carts}. Module-internal. Anonymous cart keyed by a
 * server-generated UUID (no auth until M4).
 */
@Entity
@Table(name = "carts", schema = "cart")
class CartEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "checked_out", nullable = false)
    private boolean checkedOut;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CartItemEntity> items = new ArrayList<>();

    protected CartEntity() {
        // JPA
    }

    CartEntity(UUID id) {
        this(id, null);
    }

    CartEntity(UUID id, Long userId) {
        this.id = id;
        this.userId = userId;
        this.checkedOut = false;
        this.updatedAt = Instant.now();
    }

    UUID getId() {
        return id;
    }

    Long getUserId() {
        return userId;
    }

    void setUserId(Long userId) {
        this.userId = userId;
    }

    boolean isCheckedOut() {
        return checkedOut;
    }

    void setCheckedOut(boolean checkedOut) {
        this.checkedOut = checkedOut;
    }

    List<CartItemEntity> getItems() {
        return items;
    }

    void touch() {
        this.updatedAt = Instant.now();
    }
}

package com.townbasket.serviceability.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * JPA entity for {@code serviceability.stores}. Module-internal.
 */
@Entity
@Table(name = "stores", schema = "serviceability")
class StoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(name = "delivery_radius_m", nullable = false)
    private int deliveryRadiusM;

    @Column(name = "opening_time", nullable = false)
    private LocalTime openingTime;

    @Column(name = "closing_time", nullable = false)
    private LocalTime closingTime;

    @Column(name = "min_order_value", nullable = false)
    private BigDecimal minOrderValue;

    @Column(nullable = false)
    private boolean active;

    protected StoreEntity() {
        // JPA
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getAddress() {
        return address;
    }

    double getLat() {
        return lat;
    }

    double getLng() {
        return lng;
    }

    int getDeliveryRadiusM() {
        return deliveryRadiusM;
    }

    LocalTime getOpeningTime() {
        return openingTime;
    }

    LocalTime getClosingTime() {
        return closingTime;
    }

    BigDecimal getMinOrderValue() {
        return minOrderValue;
    }

    boolean isActive() {
        return active;
    }
}

package com.townbasket.serviceability.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
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

    /** Public contact number for customers; null until staff set one. */
    @Column(name = "support_phone")
    private String supportPhone;

    /**
     * GST registration number, null until the store is registered. Validated
     * and normalised by {@link Gstin} on the way in.
     */
    @Column(name = "gstin")
    private String gstin;

    @Column(nullable = false)
    private boolean active;

    /** Manual closure: closed while now < closedUntil, whatever the hours say. */
    @Column(name = "closed_until")
    private Instant closedUntil;

    @Column(name = "closed_reason")
    private String closedReason;

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

    String getSupportPhone() {
        return supportPhone;
    }

    String getGstin() {
        return gstin;
    }

    boolean isActive() {
        return active;
    }

    Instant getClosedUntil() {
        return closedUntil;
    }

    String getClosedReason() {
        return closedReason;
    }

    // Settings are edited from the admin app; identity and `active` are not.
    void updateSettings(String name, String address, double lat, double lng,
                        int deliveryRadiusM, LocalTime openingTime, LocalTime closingTime,
                        BigDecimal minOrderValue, String supportPhone, String gstin) {
        this.name = name;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.deliveryRadiusM = deliveryRadiusM;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.minOrderValue = minOrderValue;
        this.supportPhone = supportPhone;
        this.gstin = gstin;
    }

    void closeUntil(Instant until, String reason) {
        this.closedUntil = until;
        this.closedReason = reason;
    }

    void reopen() {
        this.closedUntil = null;
        this.closedReason = null;
    }
}

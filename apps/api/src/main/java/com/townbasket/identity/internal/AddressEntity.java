package com.townbasket.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity for {@code identity.addresses}. Module-internal. Carries a plain
 * {@code user_id} (queried via the repository) rather than a mapped
 * {@code @ManyToOne} collection on the user, per the module's aggregate design.
 */
@Entity
@Table(name = "addresses", schema = "identity")
class AddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column
    private String label;

    @Column(nullable = false)
    private String line;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AddressEntity() {
        // JPA
    }

    AddressEntity(Long userId, String label, String line, double lat, double lng, boolean isDefault) {
        this.userId = userId;
        this.label = label;
        this.line = line;
        this.lat = lat;
        this.lng = lng;
        this.isDefault = isDefault;
    }

    Long getId() {
        return id;
    }

    Long getUserId() {
        return userId;
    }

    String getLabel() {
        return label;
    }

    void setLabel(String label) {
        this.label = label;
    }

    String getLine() {
        return line;
    }

    void setLine(String line) {
        this.line = line;
    }

    double getLat() {
        return lat;
    }

    void setLat(double lat) {
        this.lat = lat;
    }

    double getLng() {
        return lng;
    }

    void setLng(double lng) {
        this.lng = lng;
    }

    boolean isDefault() {
        return isDefault;
    }

    void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}

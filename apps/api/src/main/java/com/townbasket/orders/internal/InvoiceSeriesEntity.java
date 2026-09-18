package com.townbasket.orders.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for {@code orders.invoice_series} — one row per financial year,
 * holding the last GST invoice sequence issued in it. Module-internal.
 *
 * <p>Deliberately a table rather than a Postgres sequence: sequences are
 * non-transactional, so a rolled-back invoice issue would burn a number and
 * leave a permanent hole in a series that CGST Rule 46(b) requires to be
 * consecutive. Incrementing a row inside the issuing transaction means a
 * rollback takes the number back with it.
 */
@Entity
@Table(name = "invoice_series", schema = "orders")
class InvoiceSeriesEntity {

    /** Financial-year label, e.g. {@code "25-26"} (see {@link InvoiceNumbers}). */
    @Id
    @Column(name = "fy", nullable = false, updatable = false)
    private String fy;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    protected InvoiceSeriesEntity() {
        // JPA
    }

    /** Take the next number in this year's series. Caller must hold the row lock. */
    long nextSeq() {
        lastSeq += 1;
        return lastSeq;
    }
}

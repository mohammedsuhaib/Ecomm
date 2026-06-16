package com.townbasket.payments;

/** Payment status. Part of the payments public API. */
public enum PaymentStatus {
    /** UPI initiated, awaiting confirmation (transient; not used by FakeProvider). */
    PENDING,
    /** Payment captured (UPI success). */
    PAID,
    /** Payment failed. */
    FAILED,
    /** COD: no prepayment; cash to be collected on delivery. */
    COD_PENDING
}

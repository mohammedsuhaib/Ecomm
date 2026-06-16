package com.townbasket.payments;

/** Payment methods offered at checkout. Part of the payments public API. */
public enum PaymentMethod {
    /** Cash on Delivery — confirmed at placement, collected on delivery. */
    COD,
    /** Online UPI (Paytm PG live in M5; FakeProvider auto-succeeds in M3/test). */
    UPI
}

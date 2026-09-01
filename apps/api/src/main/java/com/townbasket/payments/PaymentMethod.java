package com.townbasket.payments;

/** Payment methods offered at checkout. Part of the payments public API. */
public enum PaymentMethod {
    /**
     * Pay on delivery — confirmed at placement, collected at the door in
     * CASH OR BY UPI, whichever the customer prefers. The enum name is kept
     * as COD because it is persisted and part of the REST contract; only the
     * wording customers and staff see says "Pay on Delivery".
     */
    COD,
    /** Online UPI (Paytm PG live in M5; FakeProvider auto-succeeds in M3/test). */
    UPI
}

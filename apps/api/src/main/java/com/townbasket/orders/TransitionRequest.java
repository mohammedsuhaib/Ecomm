package com.townbasket.orders;

/**
 * Admin order-transition request. {@code to} is the target status name
 * (CONFIRMED, PACKING, READY_FOR_DELIVERY, OUT_FOR_DELIVERY, DELIVERY_FAILED,
 * DELIVERED, CANCELLED).
 * {@code deliveryOtp} is required when transitioning to DELIVERED; {@code reason}
 * is recorded on the timeline — REQUIRED for DELIVERY_FAILED (staff need to know
 * why the bag came back) and required-by-convention for CANCELLED.
 *
 * <p>A CANCELLED reason is shown to the customer on their order screen, so it is
 * staff's own words and is rendered verbatim — it cannot be translated. The one
 * exception is {@link #CUSTOMER_REQUEST}: the customer's self-service cancel
 * goes through this same transition, and a server-authored English sentence
 * ("Cancelled by customer") shown back to a Kannada customer is the storefront's
 * problem to phrase, not the API's. That reserved token is the API saying *who*
 * cancelled; every other value is staff saying *why*.
 */
public record TransitionRequest(String to, String deliveryOtp, String reason) {

    /**
     * Reserved {@code reason} marking a cancellation the customer performed
     * themselves, rather than a reason staff typed. Clients translate it; they
     * render anything else as given.
     */
    public static final String CUSTOMER_REQUEST = "CUSTOMER_REQUEST";
}

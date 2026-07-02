package com.townbasket.delivery;

/** Body for POST /delivery/orders/{id}/deliver — the customer's delivery OTP. */
public record DeliverRequest(String otp) {}

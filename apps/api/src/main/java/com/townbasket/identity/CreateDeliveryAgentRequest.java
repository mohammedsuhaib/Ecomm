package com.townbasket.identity;

/**
 * Admin request to onboard a delivery agent. The agent logs in with
 * {@code email} + {@code password} (same as staff). {@code password} must be at
 * least 8 characters; {@code email} must be unique.
 */
public record CreateDeliveryAgentRequest(String name, String email, String password) {
}

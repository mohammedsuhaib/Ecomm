package com.townbasket.identity;

/**
 * Account roles. {@code CUSTOMER} logs in via phone OTP (Firebase); {@code
 * STORE_STAFF} and {@code ADMIN} log in with email + password. The enum names
 * are the canonical role values stored in {@code identity.users.role} and
 * carried in the {@code role} JWT claim.
 */
public enum Role {
    CUSTOMER,
    STORE_STAFF,
    DELIVERY_AGENT,
    ADMIN
}

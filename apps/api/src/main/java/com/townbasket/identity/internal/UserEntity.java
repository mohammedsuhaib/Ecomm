package com.townbasket.identity.internal;

import com.townbasket.identity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity for {@code identity.users}. Module-internal. A user is either a
 * phone-OTP CUSTOMER (has {@code phone} + {@code firebaseUid}, no password) or a
 * STORE_STAFF / ADMIN (has {@code email} + {@code passwordHash}). Addresses and
 * refresh tokens are NOT mapped as collections here — they carry a plain
 * {@code user_id} and are queried via their repositories (avoids the
 * null-FK-on-insert trap and keeps the aggregate small).
 */
@Entity
@Table(name = "users", schema = "identity")
class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column
    private String phone;

    @Column
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column
    private String name;

    @Column(name = "firebase_uid")
    private String firebaseUid;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
        // JPA
    }

    private UserEntity(Role role) {
        this.role = role;
        this.active = true;
        this.updatedAt = Instant.now();
    }

    /** A fresh phone-OTP customer (no email/password). */
    static UserEntity customer(String phone, String firebaseUid) {
        UserEntity u = new UserEntity(Role.CUSTOMER);
        u.phone = phone;
        u.firebaseUid = firebaseUid;
        return u;
    }

    Long getId() {
        return id;
    }

    Role getRole() {
        return role;
    }

    String getPhone() {
        return phone;
    }

    String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    String getFirebaseUid() {
        return firebaseUid;
    }

    void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }

    boolean isActive() {
        return active;
    }

    void touch() {
        this.updatedAt = Instant.now();
    }
}

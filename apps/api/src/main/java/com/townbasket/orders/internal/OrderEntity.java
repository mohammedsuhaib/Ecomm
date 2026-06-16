package com.townbasket.orders.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for {@code orders.orders}. Module-internal. Holds the price
 * snapshot, the customer/address details, payment + order status, and the
 * delivery OTP. The COGS snapshot lives on {@link OrderItemEntity}.
 */
@Entity
@Table(name = "orders", schema = "orders")
class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id")
    private UUID cartId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String phone;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "payment_status", nullable = false)
    private String paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "delivery_otp", nullable = false)
    private String deliveryOtp;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItemEntity> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderEventEntity> events = new ArrayList<>();

    protected OrderEntity() {
        // JPA
    }

    OrderEntity(UUID cartId, Long storeId, String customerName, String phone, String addressLine,
                double lat, double lng, String paymentMethod, String paymentStatus, OrderStatus status,
                BigDecimal subtotal, BigDecimal total, String deliveryOtp, String idempotencyKey) {
        this.cartId = cartId;
        this.storeId = storeId;
        this.customerName = customerName;
        this.phone = phone;
        this.addressLine = addressLine;
        this.lat = lat;
        this.lng = lng;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
        this.status = status;
        this.subtotal = subtotal;
        this.total = total;
        this.deliveryOtp = deliveryOtp;
        this.idempotencyKey = idempotencyKey;
        this.placedAt = Instant.now();
    }

    Long getId() {
        return id;
    }

    UUID getCartId() {
        return cartId;
    }

    Long getStoreId() {
        return storeId;
    }

    String getCustomerName() {
        return customerName;
    }

    String getPhone() {
        return phone;
    }

    String getAddressLine() {
        return addressLine;
    }

    double getLat() {
        return lat;
    }

    double getLng() {
        return lng;
    }

    String getPaymentMethod() {
        return paymentMethod;
    }

    String getPaymentStatus() {
        return paymentStatus;
    }

    void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    OrderStatus getStatus() {
        return status;
    }

    void setStatus(OrderStatus status) {
        this.status = status;
    }

    BigDecimal getSubtotal() {
        return subtotal;
    }

    BigDecimal getTotal() {
        return total;
    }

    String getDeliveryOtp() {
        return deliveryOtp;
    }

    Instant getPlacedAt() {
        return placedAt;
    }

    List<OrderItemEntity> getItems() {
        return items;
    }

    void addItem(OrderItemEntity item) {
        item.setOrder(this);
        items.add(item);
    }

    List<OrderEventEntity> getEvents() {
        return events;
    }

    void addEvent(OrderEventEntity event) {
        event.setOrder(this);
        events.add(event);
    }
}

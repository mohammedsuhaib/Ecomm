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
import jakarta.persistence.Version;
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

    // Optimistic lock: of two concurrent writes to the same order (e.g. a rider
    // double-tapping "confirm delivery"), only one commits — the loser rolls
    // back, taking its duplicate event row and outbox publications with it,
    // and surfaces as a 409 (see GlobalExceptionHandler).
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "cart_id")
    private UUID cartId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "assigned_agent_id")
    private Long assignedAgentId;

    @Column(name = "public_token", nullable = false, updatable = false)
    private UUID publicToken;

    // The short, speakable order number customers quote (see OrderCodes). A
    // display label, not a capability — access is login + ownership.
    @Column(name = "public_code", nullable = false, updatable = false)
    private String publicCode;

    // GST invoice number and the moment it was issued. Both stay null until an
    // invoice is actually issued, then never change: a tax invoice, once handed
    // out, must reproduce byte-for-byte on a re-download (CGST Rule 46(b)).
    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoiced_at")
    private Instant invoicedAt;

    // The supplier GSTIN this invoice was issued under. Snapshotted with the
    // number above, because staff can change the store's GSTIN and the PDF is
    // re-rendered on every download — reading it live would reprint an old
    // invoice under a registration it was never issued with.
    @Column(name = "invoice_gstin")
    private String invoiceGstin;

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

    // GST extracted from the tax-inclusive total (sum of per-line cgst+sgst);
    // informational — total already includes it.
    @Column(name = "total_tax", nullable = false)
    private BigDecimal totalTax;

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

    OrderEntity(UUID cartId, Long userId, Long storeId, String customerName, String phone, String addressLine,
                double lat, double lng, String paymentMethod, String paymentStatus, OrderStatus status,
                BigDecimal subtotal, BigDecimal total, String deliveryOtp, String idempotencyKey) {
        this.cartId = cartId;
        this.userId = userId;
        this.publicToken = UUID.randomUUID();
        this.publicCode = OrderCodes.newCode();
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
        this.totalTax = BigDecimal.ZERO; // set from the line snapshots by placeOrder
        this.deliveryOtp = deliveryOtp;
        this.idempotencyKey = idempotencyKey;
        this.placedAt = Instant.now();
    }

    Long getId() {
        return id;
    }

    Long getUserId() {
        return userId;
    }

    Long getAssignedAgentId() {
        return assignedAgentId;
    }

    void setAssignedAgentId(Long assignedAgentId) {
        this.assignedAgentId = assignedAgentId;
    }

    UUID getPublicToken() {
        return publicToken;
    }

    String getPublicCode() {
        return publicCode;
    }

    String getInvoiceNumber() {
        return invoiceNumber;
    }

    Instant getInvoicedAt() {
        return invoicedAt;
    }

    String getInvoiceGstin() {
        return invoiceGstin;
    }

    /**
     * Record that a GST invoice was issued for this order, under the supplier
     * GSTIN in force at that moment. Write-once, all three fields together: an
     * already-issued invoice is kept as it was, so a re-download reproduces the
     * same document rather than minting a second invoice for one supply — or
     * reprinting this one under a GSTIN edited since.
     *
     * @param gstin the store's GSTIN, or null if it is not registered yet, in
     *     which case the invoice omits the line exactly as it did before the
     *     number could be set at all
     */
    void markInvoiced(String number, Instant at, String gstin) {
        if (this.invoiceNumber == null) {
            this.invoiceNumber = number;
            this.invoicedAt = at;
            this.invoiceGstin = gstin;
        }
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

    BigDecimal getTotalTax() {
        return totalTax;
    }

    void setTotalTax(BigDecimal totalTax) {
        this.totalTax = totalTax;
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

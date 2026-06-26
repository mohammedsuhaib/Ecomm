package com.townbasket.orders.internal;

import com.townbasket.cart.CartDto;
import com.townbasket.cart.CartItemDto;
import com.townbasket.cart.CartService;
import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.VariantView;
import com.townbasket.inventory.InventoryService;
import com.townbasket.inventory.ReservationLine;
import com.townbasket.orders.AddressDto;
import com.townbasket.orders.OrderDto;
import com.townbasket.orders.OrderItemDto;
import com.townbasket.orders.OrderService;
import com.townbasket.orders.OrderTimelineEntryDto;
import com.townbasket.orders.PlaceOrderRequest;
import com.townbasket.orders.TransitionRequest;
import com.townbasket.payments.PaymentMethod;
import com.townbasket.payments.PaymentResult;
import com.townbasket.payments.PaymentService;
import com.townbasket.serviceability.ServiceabilityCheckDto;
import com.townbasket.serviceability.ServiceabilityService;
import com.townbasket.serviceability.StoreDto;
import com.townbasket.shared.BusinessRuleException;
import com.townbasket.shared.PagedResponse;
import com.townbasket.shared.ResourceNotFoundException;
import com.townbasket.shared.events.OrderCancelled;
import com.townbasket.shared.events.OrderConfirmed;
import com.townbasket.shared.events.OrderDelivered;
import com.townbasket.shared.events.OrderPlaced;
import com.townbasket.shared.events.OrderStatusChanged;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module-internal implementation of {@link OrderService}: the checkout
 * orchestrator and the staff-driven state machine.
 *
 * <p>Checkout calls the PUBLIC services of {@code cart}, {@code serviceability},
 * {@code inventory} and {@code payments} synchronously (allowed dependencies),
 * then publishes domain events (in {@code shared}) that {@code inventory} and
 * {@code notifications} react to. Those modules never call orders back
 * synchronously, so there is no cycle.
 */
@Service
@Transactional
class OrderServiceImpl implements OrderService {

    private static final SecureRandom OTP_RANDOM = new SecureRandom();

    private final OrderRepository orders;
    private final CartService cartService;
    private final CatalogService catalogService;
    private final ServiceabilityService serviceabilityService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    OrderServiceImpl(OrderRepository orders,
                     CartService cartService,
                     CatalogService catalogService,
                     ServiceabilityService serviceabilityService,
                     InventoryService inventoryService,
                     PaymentService paymentService,
                     ApplicationEventPublisher events,
                     Clock clock) {
        this.orders = orders;
        this.cartService = cartService;
        this.catalogService = catalogService;
        this.serviceabilityService = serviceabilityService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public OrderDto placeOrder(PlaceOrderRequest request, String idempotencyKey, Long userId) {
        String key = resolveIdempotencyKey(request, idempotencyKey);

        // Idempotency: a retry with the same key returns the original order.
        Optional<OrderEntity> existing = orders.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return toDto(existing.get(), true);
        }

        validateRequest(request);

        CartDto cart = cartService.getCart(request.cartId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found: " + request.cartId()));
        if (cart.items().isEmpty()) {
            throw new BusinessRuleException("Cannot place an order from an empty cart");
        }
        // Guard against double-ordering the same cart (back button / network retry /
        // PWA resume): once a cart has been ordered it can never be ordered again,
        // even under a fresh idempotency key. The idempotent retry path above
        // already returned the original order for the same key.
        if (cart.checkedOut()) {
            throw new BusinessRuleException(
                    "This cart has already been ordered. Please start a new cart.");
        }

        StoreDto store = serviceabilityService.activeStore()
                .orElseThrow(() -> new IllegalStateException("No active store configured"));
        Long storeId = resolveActiveStoreId();

        requireStoreOpen(store);

        // Reject lines a store admin has marked unavailable since they were added.
        List<String> unavailable = cart.items().stream()
                .filter(i -> !i.available())
                .map(CartItemDto::productName)
                .collect(Collectors.toList());
        if (!unavailable.isEmpty()) {
            throw new BusinessRuleException(
                    "Some items are no longer available: " + String.join(", ", unavailable)
                            + ". Please remove them and try again.");
        }

        AddressDto address = request.address();
        ServiceabilityCheckDto check = serviceabilityService.check(address.lat(), address.lng());
        if (!check.serviceable()) {
            throw new BusinessRuleException("Delivery address is outside the serviceable area ("
                    + check.distanceMeters() + " m > " + check.radiusMeters() + " m)");
        }

        BigDecimal subtotal = cart.subtotal();
        if (subtotal.compareTo(store.minOrderValue()) < 0) {
            throw new BusinessRuleException("Order subtotal " + subtotal
                    + " is below the minimum order value " + store.minOrderValue());
        }
        // Never charge a total different from the one the customer confirmed.
        if (request.expectedTotal() != null && request.expectedTotal().compareTo(subtotal) != 0) {
            throw new BusinessRuleException(
                    "The total has changed (now " + subtotal + ", you saw " + request.expectedTotal()
                            + "). Please review your cart and confirm the new total.");
        }

        // Reserve stock atomically (per-line conditional UPDATE). A failure throws
        // and rolls back the whole checkout — no partial reservation, no order row.
        List<ReservationLine> reservationLines = cart.items().stream()
                .map(i -> new ReservationLine(i.variantId(), i.qty()))
                .toList();

        PaymentMethod method = request.paymentMethod();
        boolean upi = method == PaymentMethod.UPI;

        OrderEntity order = new OrderEntity(
                cart.cartId(), userId, storeId, request.customerName(), request.phone(), address.line(),
                address.lat(), address.lng(), method.name(),
                upi ? "PENDING" : "COD_PENDING",
                OrderStatus.PLACED, subtotal, subtotal, generateOtp(), key);

        // Snapshot item prices + COGS (cost price fetched separately, never exposed).
        for (CartItemDto item : cart.items()) {
            BigDecimal costPrice = catalogService.costPrice(item.variantId()).orElse(BigDecimal.ZERO);
            order.addItem(new OrderItemEntity(
                    item.variantId(), item.productName(), item.label(),
                    item.unitPrice(), costPrice, item.qty(), item.lineTotal()));
        }
        order.addEvent(new OrderEventEntity(null, OrderStatus.PLACED.name(), "Order placed"));

        // Persist first to obtain the order id, then reserve against it.
        OrderEntity saved = orders.saveAndFlush(order);
        inventoryService.reserve(storeId, saved.getId(), reservationLines);

        // Charge payment. COD -> COD_PENDING; UPI (FakeProvider) -> PAID.
        PaymentResult payment = paymentService.charge(saved.getId(), method, saved.getTotal());
        saved.setPaymentStatus(payment.status().name());

        // Both COD and a successful UPI charge confirm the order at placement.
        if (payment.confirmsOrder()) {
            confirm(saved);
        }

        OrderEntity reloaded = orders.findById(saved.getId()).orElseThrow();

        // OrderPlaced carries the reserved lines so inventory can commit/release later.
        events.publishEvent(new OrderPlaced(reloaded.getId(), storeId, reservationLines.stream()
                .map(l -> new OrderPlaced.Line(l.variantId(), l.qty()))
                .toList()));
        if (reloaded.getStatus() == OrderStatus.CONFIRMED) {
            events.publishEvent(new OrderConfirmed(reloaded.getId(), storeId));
        }

        cartService.markCheckedOut(cart.cartId());

        // Customer-facing response: carries the tracking token; the OTP stays
        // hidden until OUT_FOR_DELIVERY (so it is null here at placement).
        return toDto(reloaded, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderDto> getOrderByToken(UUID trackingToken) {
        return orders.findByPublicToken(trackingToken).map(o -> toDto(o, true));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderDto> listOrders(String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? orders.findAllByOrderByPlacedAtDescIdDesc(pageable)
                : orders.findByStatusOrderByPlacedAtDescIdDesc(parseStatus(status), pageable);
        // Admin surface: never expose the delivery OTP (staff collect it at handover).
        return PagedResponse.of(page, o -> toDto(o, false));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderDto> listUserOrders(Long userId, Pageable pageable) {
        // The caller's own order history is customer-facing: the OTP is still gated
        // to OUT_FOR_DELIVERY by toDto, and each order carries its tracking token.
        return PagedResponse.of(
                orders.findByUserIdOrderByPlacedAtDescIdDesc(userId, pageable), o -> toDto(o, true));
    }

    @Override
    public CartDto reorder(Long orderId, Long userId) {
        OrderEntity order = orders.findById(orderId)
                .filter(o -> userId.equals(o.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        CartDto cart = cartService.createUserCart(userId);
        UUID cartId = cart.cartId();
        // Add each currently catalog-available line; skip unavailable ones.
        for (OrderItemEntity item : order.getItems()) {
            boolean available = catalogService.findVariant(item.getVariantId())
                    .map(VariantView::available)
                    .orElse(false);
            if (available) {
                cart = cartService.addItem(cartId, item.getVariantId(), item.getQty());
            }
        }
        return cart;
    }

    @Override
    public OrderDto transition(Long orderId, TransitionRequest request) {
        OrderEntity order = orders.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        OrderStatus from = order.getStatus();
        OrderStatus to = parseStatus(request.to());

        if (!from.canTransitionTo(to)) {
            throw new BusinessRuleException("Illegal transition " + from + " -> " + to);
        }
        if (to == OrderStatus.DELIVERED) {
            if (request.deliveryOtp() == null || !request.deliveryOtp().equals(order.getDeliveryOtp())) {
                throw new BusinessRuleException("Delivery OTP does not match");
            }
        }

        order.setStatus(to);
        order.addEvent(new OrderEventEntity(from.name(), to.name(), request.reason()));

        if (to == OrderStatus.DELIVERED && "COD".equals(order.getPaymentMethod())) {
            order.setPaymentStatus("PAID"); // COD cash collected on delivery.
        }

        events.publishEvent(new OrderStatusChanged(order.getId(), order.getStoreId(), from.name(), to.name()));
        if (to == OrderStatus.DELIVERED) {
            events.publishEvent(new OrderDelivered(order.getId(), order.getStoreId()));
        } else if (to == OrderStatus.CANCELLED) {
            events.publishEvent(new OrderCancelled(order.getId(), order.getStoreId(), request.reason()));
        }

        // Admin surface: never expose the delivery OTP.
        return toDto(orders.findById(orderId).orElseThrow(), false);
    }

    /** Mark an order CONFIRMED and record the timeline entry (called within checkout). */
    private void confirm(OrderEntity order) {
        order.setStatus(OrderStatus.CONFIRMED);
        order.addEvent(new OrderEventEntity(
                OrderStatus.PLACED.name(), OrderStatus.CONFIRMED.name(), "Payment accepted"));
    }

    private void validateRequest(PlaceOrderRequest request) {
        if (request.cartId() == null) {
            throw new IllegalArgumentException("cartId is required");
        }
        if (isBlank(request.customerName())) {
            throw new IllegalArgumentException("customerName is required");
        }
        if (isBlank(request.phone())) {
            throw new IllegalArgumentException("phone is required");
        }
        if (request.address() == null || isBlank(request.address().line())) {
            throw new IllegalArgumentException("address.line is required");
        }
        if (request.paymentMethod() == null) {
            throw new IllegalArgumentException("paymentMethod is required");
        }
        // A reachable phone and in-range coordinates are required to deliver; the
        // frontend validates these too, but the server is authoritative (a malformed
        // client, or a tampered request, must not create an undeliverable order).
        String phone = request.phone().trim();
        if (!phone.matches("[0-9]{10}")) {
            throw new IllegalArgumentException("phone must be a 10-digit number");
        }
        double lat = request.address().lat();
        double lng = request.address().lng();
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("address coordinates are out of range");
        }
    }

    /**
     * Reject checkout when the store is closed (the address may be serviceable,
     * but nobody can fulfil the order). Hours are interpreted in the store's
     * local time via the injected {@link Clock}. Supports an overnight window
     * (closing before opening), though the MVP store opens 08:00–21:00.
     */
    private void requireStoreOpen(StoreDto store) {
        LocalTime now = LocalTime.now(clock);
        LocalTime open = store.openingTime();
        LocalTime close = store.closingTime();
        boolean isOpen = !open.isAfter(close)
                ? !now.isBefore(open) && !now.isAfter(close)        // same-day window
                : !now.isBefore(open) || !now.isAfter(close);       // crosses midnight
        if (!isOpen) {
            throw new BusinessRuleException(
                    store.name() + " is closed right now. Delivery hours are "
                            + open + "–" + close + ". Please order during open hours.");
        }
    }

    private String resolveIdempotencyKey(PlaceOrderRequest request, String headerKey) {
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey;
        }
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            return request.idempotencyKey();
        }
        throw new IllegalArgumentException("An Idempotency-Key header (or idempotencyKey field) is required");
    }

    private Long resolveActiveStoreId() {
        // serviceability exposes the active store's details by value, not id; the
        // single MVP store seeds id 1 (and inventory stock is seeded for it). When
        // identity/multi-store land, serviceability will expose the store id.
        return 1L;
    }

    private static OrderStatus parseStatus(String value) {
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown order status: " + value);
        }
    }

    private static String generateOtp() {
        return String.format("%06d", OTP_RANDOM.nextInt(1_000_000));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Map an order to its public DTO.
     *
     * @param customerFacing when {@code true} (the tracking endpoint) the delivery
     *     OTP is included <em>only</em> while the order is OUT_FOR_DELIVERY;
     *     when {@code false} (admin surface) the OTP is never included.
     */
    private OrderDto toDto(OrderEntity o, boolean customerFacing) {
        List<OrderItemDto> items = o.getItems().stream()
                // NOTE: cost price (COGS) is intentionally NOT mapped — internal only.
                .map(i -> new OrderItemDto(i.getProductName(), i.getLabel(),
                        i.getUnitPrice(), i.getQty(), i.getLineTotal()))
                .toList();
        List<OrderTimelineEntryDto> timeline = o.getEvents().stream()
                .map(e -> new OrderTimelineEntryDto(e.getToStatus(), e.getAt()))
                .toList();
        // The delivery OTP is the proof-of-delivery / COD-fraud code. It is exposed
        // to the customer ONLY while the order is OUT_FOR_DELIVERY (staff collect it
        // at handover) and is NEVER returned on the admin surface (customerFacing ==
        // false). The DELIVERED transition still verifies against the stored value on
        // the entity, not this DTO.
        String deliveryOtp = (customerFacing && o.getStatus() == OrderStatus.OUT_FOR_DELIVERY)
                ? o.getDeliveryOtp()
                : null;
        return new OrderDto(
                o.getId(),
                o.getPublicToken().toString(),
                o.getStatus().name(),
                o.getPaymentMethod(),
                o.getPaymentStatus(),
                o.getCustomerName(),
                o.getPhone(),
                new AddressDto(o.getAddressLine(), o.getLat(), o.getLng()),
                items,
                o.getSubtotal(),
                o.getTotal(),
                deliveryOtp,
                o.getPlacedAt(),
                timeline);
    }
}

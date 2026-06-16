package com.townbasket.orders.internal;

import com.townbasket.cart.CartDto;
import com.townbasket.cart.CartItemDto;
import com.townbasket.cart.CartService;
import com.townbasket.catalog.CatalogService;
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
import java.util.List;
import java.util.Optional;
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

    OrderServiceImpl(OrderRepository orders,
                     CartService cartService,
                     CatalogService catalogService,
                     ServiceabilityService serviceabilityService,
                     InventoryService inventoryService,
                     PaymentService paymentService,
                     ApplicationEventPublisher events) {
        this.orders = orders;
        this.cartService = cartService;
        this.catalogService = catalogService;
        this.serviceabilityService = serviceabilityService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.events = events;
    }

    @Override
    public OrderDto placeOrder(PlaceOrderRequest request, String idempotencyKey) {
        String key = resolveIdempotencyKey(request, idempotencyKey);

        // Idempotency: a retry with the same key returns the original order.
        Optional<OrderEntity> existing = orders.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        validateRequest(request);

        CartDto cart = cartService.getCart(request.cartId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found: " + request.cartId()));
        if (cart.items().isEmpty()) {
            throw new BusinessRuleException("Cannot place an order from an empty cart");
        }

        StoreDto store = serviceabilityService.activeStore()
                .orElseThrow(() -> new IllegalStateException("No active store configured"));
        Long storeId = resolveActiveStoreId();

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

        // Reserve stock atomically (per-line conditional UPDATE). A failure throws
        // and rolls back the whole checkout — no partial reservation, no order row.
        List<ReservationLine> reservationLines = cart.items().stream()
                .map(i -> new ReservationLine(i.variantId(), i.qty()))
                .toList();

        PaymentMethod method = request.paymentMethod();
        boolean upi = method == PaymentMethod.UPI;

        OrderEntity order = new OrderEntity(
                cart.cartId(), storeId, request.customerName(), request.phone(), address.line(),
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

        return toDto(reloaded);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderDto> getOrder(Long orderId) {
        return orders.findById(orderId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderDto> listOrders(String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? orders.findAllByOrderByPlacedAtDescIdDesc(pageable)
                : orders.findByStatusOrderByPlacedAtDescIdDesc(parseStatus(status), pageable);
        return PagedResponse.of(page, this::toDto);
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

        return toDto(orders.findById(orderId).orElseThrow());
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

    private OrderDto toDto(OrderEntity o) {
        List<OrderItemDto> items = o.getItems().stream()
                // NOTE: cost price (COGS) is intentionally NOT mapped — internal only.
                .map(i -> new OrderItemDto(i.getProductName(), i.getLabel(),
                        i.getUnitPrice(), i.getQty(), i.getLineTotal()))
                .toList();
        List<OrderTimelineEntryDto> timeline = o.getEvents().stream()
                .map(e -> new OrderTimelineEntryDto(e.getToStatus(), e.getAt()))
                .toList();
        return new OrderDto(
                o.getId(),
                o.getStatus().name(),
                o.getPaymentMethod(),
                o.getPaymentStatus(),
                o.getCustomerName(),
                o.getPhone(),
                new AddressDto(o.getAddressLine(), o.getLat(), o.getLng()),
                items,
                o.getSubtotal(),
                o.getTotal(),
                o.getDeliveryOtp(),
                o.getPlacedAt(),
                timeline);
    }
}

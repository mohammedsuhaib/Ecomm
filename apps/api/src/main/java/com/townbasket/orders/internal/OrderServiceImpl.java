package com.townbasket.orders.internal;

import com.townbasket.cart.CartDto;
import com.townbasket.cart.CartItemDto;
import com.townbasket.cart.CartService;
import com.townbasket.catalog.CatalogService;
import com.townbasket.catalog.VariantTaxView;
import com.townbasket.identity.AuthService;
import com.townbasket.inventory.InventoryService;
import com.townbasket.inventory.ReservationLine;
import com.townbasket.orders.AddressDto;
import com.townbasket.orders.AgentDaySummary;
import com.townbasket.orders.AgentDeliveryStat;
import com.townbasket.orders.OrderDto;
import com.townbasket.orders.OrderItemDto;
import com.townbasket.orders.OrderService;
import com.townbasket.orders.OrderTimelineEntryDto;
import com.townbasket.orders.PlaceOrderRequest;
import com.townbasket.orders.RiderLocationDto;
import com.townbasket.orders.TransitionRequest;
import com.townbasket.payments.PaymentMethod;
import com.townbasket.payments.PaymentResult;
import com.townbasket.payments.PaymentService;
import com.townbasket.payments.PaymentStatus;
import com.townbasket.serviceability.ServiceabilityCheckDto;
import com.townbasket.serviceability.ServiceabilityService;
import com.townbasket.serviceability.StoreDto;
import com.townbasket.shared.BusinessRuleException;
import com.townbasket.shared.PagedResponse;
import com.townbasket.shared.ResourceNotFoundException;
import com.townbasket.shared.events.OrderAssigned;
import com.townbasket.shared.events.OrderCancelled;
import com.townbasket.shared.events.OrderConfirmed;
import com.townbasket.shared.events.OrderDelivered;
import com.townbasket.shared.events.OrderPlaced;
import com.townbasket.shared.events.OrderStatusChanged;
import com.townbasket.tax.TaxBreakdown;
import com.townbasket.tax.TaxService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
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

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final SecureRandom OTP_RANDOM = new SecureRandom();

    /**
     * Customer self-service cancellation window, measured from placed_at. Keep
     * in sync with the published refund policy ("within 1 minute of placing",
     * holding-site/refund.html) — the policy is the customer-facing contract.
     */
    private static final Duration CUSTOMER_CANCEL_WINDOW = Duration.ofMinutes(1);

    /**
     * How old a rider's last fix may be and still be shown to the customer.
     *
     * <p>A phone in a pocket, a dead battery or a dropped signal all stop the
     * pings without any "stopped" message ever arriving, and the last position
     * would otherwise sit on the customer's map indefinitely, looking live. The
     * app reports every ~8 s, so three minutes is a generous allowance for a
     * bad patch of coverage and still short enough that a frozen dot reads as
     * "no location right now" rather than "the rider has stopped outside my
     * neighbour's house".
     */
    private static final Duration RIDER_LOCATION_TTL = Duration.ofMinutes(3);

    private final OrderRepository orders;
    private final InvoiceSeriesRepository invoiceSeries;
    private final AgentLocationRepository agentLocations;
    private final CartService cartService;
    private final CatalogService catalogService;
    private final ServiceabilityService serviceabilityService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final AuthService authService;
    private final TaxService taxService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    /** Prefix of the GST invoice series, e.g. "TB" in {@code TB/25-26/00001}. */
    private final String invoicePrefix;

    OrderServiceImpl(OrderRepository orders,
                     InvoiceSeriesRepository invoiceSeries,
                     AgentLocationRepository agentLocations,
                     CartService cartService,
                     CatalogService catalogService,
                     ServiceabilityService serviceabilityService,
                     InventoryService inventoryService,
                     PaymentService paymentService,
                     AuthService authService,
                     TaxService taxService,
                     ApplicationEventPublisher events,
                     Clock clock,
                     @Value("${townbasket.invoice.series-prefix:TB}") String invoicePrefix) {
        this.orders = orders;
        this.invoiceSeries = invoiceSeries;
        this.agentLocations = agentLocations;
        this.cartService = cartService;
        this.catalogService = catalogService;
        this.serviceabilityService = serviceabilityService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.authService = authService;
        this.taxService = taxService;
        this.events = events;
        this.clock = clock;
        this.invoicePrefix = invoicePrefix == null ? "TB" : invoicePrefix.trim();
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
        // Refuse a method this deployment doesn't accept before doing any work,
        // so nothing has to be rolled back.
        paymentService.requireEnabled(request.paymentMethod());

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
                // productName is null when the variant has been deleted from the
                // catalog since it was added — fall back to a neutral label.
                .map(i -> i.productName() != null ? i.productName() : "an item")
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

        // Snapshot item prices + COGS (cost price fetched separately, never
        // exposed) + the GST breakdown. Prices are tax-INCLUSIVE, so the tax is
        // extracted from each line total, never added to it — the customer pays
        // exactly the cart total either way. Snapshotting at sale time keeps
        // later catalog rate edits from mutating issued invoices.
        BigDecimal totalTax = BigDecimal.ZERO;
        for (CartItemDto item : cart.items()) {
            BigDecimal costPrice = catalogService.costPrice(item.variantId()).orElse(BigDecimal.ZERO);
            VariantTaxView tax = catalogService.taxInfo(item.variantId())
                    .orElse(new VariantTaxView(null, BigDecimal.ZERO));
            // CGST and SGST come back equal per line (each the half-rate levy),
            // so the invoice summary this sums into is balanced by construction
            // — no per-order state needed to keep it that way.
            TaxBreakdown breakdown = taxService.fromInclusiveAmount(item.lineTotal(), tax.gstRatePercent());
            totalTax = totalTax.add(breakdown.totalTax());
            order.addItem(new OrderItemEntity(
                    item.variantId(), item.productName(), item.productNameKn(), item.label(),
                    item.unitPrice(), costPrice, item.qty(), item.lineTotal(),
                    tax.hsnCode(), tax.gstRatePercent(),
                    breakdown.taxableValue(), breakdown.cgst(), breakdown.sgst()));
        }
        order.setTotalTax(totalTax);
        order.addEvent(new OrderEventEntity(null, OrderStatus.PLACED.name(), "Order placed"));

        // Persist first to obtain the order id, then reserve against it.
        OrderEntity saved = orders.saveAndFlush(order);
        inventoryService.reserve(storeId, saved.getId(), reservationLines);

        // Charge payment. COD -> COD_PENDING; UPI (FakeProvider) -> PAID.
        PaymentResult payment = paymentService.charge(saved.getId(), method, saved.getTotal());

        // A declined online payment must NOT leave a placed order holding a stock
        // reservation forever. Throwing here rolls back the whole checkout (the
        // order row AND the conditional stock reservation, both in this
        // transaction) and leaves the cart un-checked-out, so the customer can
        // retry or switch to COD on the same cart.
        if (payment.status() == PaymentStatus.FAILED) {
            throw new BusinessRuleException(
                    "Payment could not be completed. Please try again or choose a different payment method.");
        }
        saved.setPaymentStatus(payment.status().name());

        // The order stays PLACED here whatever the payment outcome: CONFIRMED is
        // a staff decision, taken from the admin queue once someone has looked
        // at the order (stock on the shelf, address, a call to the customer).
        // Payment state travels separately in paymentStatus, so a prepaid UPI
        // order and a COD one reach the queue in the same state.
        events.publishEvent(new OrderPlaced(saved.getId(), saved.getPublicCode(), storeId));

        cartService.markCheckedOut(cart.cartId());

        // The order log is the trail for "what happened to order X?", which is
        // how every support question and every reconciliation starts. Ids, the
        // customer-facing code, money and payment state only — never the
        // customer's name, phone or address, which are all a query away for
        // anyone who is actually entitled to them.
        log.info("Order {} ({}) placed: {} item(s), total {}, {} {}",
                saved.getId(), saved.getPublicCode(), saved.getItems().size(),
                saved.getTotal(), method, saved.getPaymentStatus());

        // Customer-facing response: carries the tracking token; the OTP stays
        // hidden until OUT_FOR_DELIVERY (so it is null here at placement).
        return toDto(saved, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderDto> getOrderByToken(UUID trackingToken, Long userId) {
        // Owner-scoped: a non-owner (or a legacy ownerless order) reads as "no
        // such order" — never as a 403 that would confirm the token is real.
        //
        // This is the ONE read that carries the rider's position: it is the
        // tracking page, it is owner-scoped, and it is one order — so the
        // lookup is one row and there is no N+1 as there would be on the
        // history list (which passes no location, see listUserOrders).
        return orders.findByPublicToken(trackingToken)
                .filter(o -> userId != null && userId.equals(o.getUserId()))
                .map(o -> toDto(o, true, riderLocationFor(o)));
    }

    @Override
    public OrderDto issueInvoice(UUID trackingToken, Long userId) {
        OrderEntity order = orders.findByPublicToken(trackingToken)
                .filter(o -> userId != null && userId.equals(o.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // An already-issued invoice is always served, whatever the order's
        // status: a tax invoice that has been handed out has to stay
        // retrievable. DELIVERED is terminal, so this can never serve a
        // document issued before the goods actually changed hands.
        //
        // Otherwise this is a FIRST issue: require the supply to have happened,
        // then take the next number in this financial year's series and stamp it
        // on the order. Numbering at issue time (not order time) keeps the
        // series chronological within its year by construction, and the
        // write-once stamp means a re-download reproduces this same document
        // rather than minting a second invoice for one supply.
        if (order.getInvoiceNumber() == null) {
            requireDelivered(order);
            String fy = InvoiceNumbers.financialYear(LocalDate.now(clock));
            invoiceSeries.ensureSeries(fy);
            long sequence = invoiceSeries.findAndLockByFy(fy)
                    .orElseThrow(() -> new IllegalStateException(
                            "Invoice series row missing for financial year " + fy))
                    .nextSeq();
            order.markInvoiced(
                    InvoiceNumbers.format(invoicePrefix, fy, sequence), Instant.now(clock));
        }
        return toDto(order, true);
    }

    /**
     * A tax invoice records a supply that has actually taken place, so one is
     * issued only once the order has been DELIVERED. Until handover the goods
     * are still the store's — on a shelf, in a packing crate, or in a bag on a
     * rider's bike — and a failed delivery attempt brings them back, so none of
     * those states is a supply. Numbering before handover would also put the
     * series out of order relative to when supplies occurred, which is the one
     * property the per-financial-year counter exists to guarantee.
     */
    private static void requireDelivered(OrderEntity order) {
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.DELIVERED) {
            return;
        }
        if (status == OrderStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "This order was cancelled, so there is no invoice for it.");
        }
        throw new BusinessRuleException(
                "Your invoice will be available once this order has been delivered.");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwnedBy(Long orderId, Long userId) {
        return userId != null && orders.findById(orderId)
                .map(o -> userId.equals(o.getUserId()))
                .orElse(false);
    }

    @Override
    public OrderDto cancelByToken(UUID trackingToken, Long userId) {
        OrderEntity order = orders.findByPublicToken(trackingToken)
                .filter(o -> userId != null && userId.equals(o.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        OrderStatus status = order.getStatus();
        if (status == OrderStatus.CANCELLED) {
            return toDto(order, true); // idempotent — double-tap / retry safe
        }
        // Fulfilment started (PACKING onwards): the published policy routes the
        // customer to support instead of self-service.
        if (status != OrderStatus.PLACED && status != OrderStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "This order is already being packed and can no longer be cancelled online. "
                            + "Please contact support.");
        }
        Instant deadline = order.getPlacedAt().plus(CUSTOMER_CANCEL_WINDOW);
        if (Instant.now(clock).isAfter(deadline)) {
            throw new BusinessRuleException(
                    "The " + CUSTOMER_CANCEL_WINDOW.toSeconds()
                            + "-second cancellation window has passed. Please contact support.");
        }
        // Reuse the state machine: releases reserved stock via OrderCancelled.
        // The reason is the reserved token, not a sentence: it lands on the
        // timeline the customer reads, and only the storefront knows what
        // language to say it in (see TransitionRequest.CUSTOMER_REQUEST).
        return transition(order.getId(), new TransitionRequest(
                OrderStatus.CANCELLED.name(), null, TransitionRequest.CUSTOMER_REQUEST));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderDto> listOrders(String status, Pageable pageable) {
        return listOrders(status, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderDto> listOrders(String status, String q, Pageable pageable) {
        OrderStatus st = (status == null || status.isBlank()) ? null : parseStatus(status);
        String term = q == null ? null : q.trim();
        Page<OrderEntity> page;
        if (term == null || term.isEmpty()) {
            page = st == null
                    ? orders.findAllByOrderByPlacedAtDescIdDesc(pageable)
                    : orders.findByStatusOrderByPlacedAtDescIdDesc(st, pageable);
        } else {
            // "A customer is on the phone about their order": match the order
            // code, any part of the phone, or any part of the name — in SQL,
            // so the pager describes the matches, not the page on screen.
            String like = "%" + escapeLike(term.toLowerCase()) + "%";
            // The code branch searches the normalised term, so a dictated "oh"
            // for zero or a typed-in hyphen still lands. "~" is outside the
            // Crockford alphabet, so an unusable term matches no code at all
            // rather than every one of them.
            String normalized = OrderCodes.normalize(term);
            String codeLike = normalized.isEmpty()
                    ? "~"
                    : "%" + escapeLike(normalized.toLowerCase()) + "%";
            // An all-digit term might be an order id quoted from a support note.
            // Matched by equality (not a substring of the id) so the predicate can
            // use the primary key — see OrderRepository#search for why that
            // matters to the whole query's plan, not just this branch.
            Long idExact = parseOrderId(term);
            // Native queries, so the Pageable must carry no Sort (the ORDER BY is in
            // the statement) and the status binds as its enum name.
            Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
            page = st == null
                    ? orders.search(like, codeLike, idExact, unsorted)
                    : orders.searchByStatus(st.name(), like, codeLike, idExact, unsorted);
        }
        // Admin surface: never expose the delivery OTP (staff collect it at handover).
        return PagedResponse.of(page, o -> toDto(o, false));
    }

    /** The term as an order id when it is plausibly one, else null. */
    private static Long parseOrderId(String term) {
        if (term.isEmpty() || term.length() > 18 || !term.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Long.parseLong(term);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** LIKE wildcards typed by a human are literal characters, not patterns. */
    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
        // One bulk call: addItems loads/flushes the cart once and skips lines
        // that are no longer catalog-available (per-line addItem was quadratic).
        Map<Long, Integer> lines = new LinkedHashMap<>();
        for (OrderItemEntity item : order.getItems()) {
            lines.merge(item.getVariantId(), item.getQty(), Integer::sum);
        }
        return cartService.addItems(cart.cartId(), lines);
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
            // A delivery has to belong to a rider. Not a formality: the COD
            // branch below marks the order PAID, i.e. records that cash was
            // taken, and the rider's day summary sums that cash with
            // `WHERE assigned_agent_id = ?`. An order delivered with nobody
            // assigned is therefore money booked as collected that appears in
            // no rider's total and nobody has to hand over — invisible in
            // exactly the report built to reconcile it.
            if (order.getAssignedAgentId() == null) {
                throw new BusinessRuleException(
                        "Assign a rider before marking this order delivered — the delivery, "
                                + "and any cash collected for it, are recorded against them.");
            }
            if (request.deliveryOtp() == null || !request.deliveryOtp().equals(order.getDeliveryOtp())) {
                throw new BusinessRuleException("Delivery OTP does not match");
            }
        }
        if (to == OrderStatus.DELIVERY_FAILED && isBlank(request.reason())) {
            // The reason is the whole point of the state: staff decide between a
            // re-attempt and a cancel from it, and the customer is told it.
            throw new BusinessRuleException("A reason is required when a delivery fails");
        }

        order.setStatus(to);
        order.addEvent(new OrderEventEntity(from.name(), to.name(), request.reason()));

        if (to == OrderStatus.DELIVERED && "COD".equals(order.getPaymentMethod())) {
            order.setPaymentStatus("PAID"); // COD cash collected on delivery.
        }

        events.publishEvent(new OrderStatusChanged(
                order.getId(), order.getPublicCode(), order.getStoreId(), from.name(), to.name(),
                order.getUserId(), order.getPublicToken().toString(),
                order.getAssignedAgentId(), order.getAddressLine()));
        if (to == OrderStatus.CONFIRMED) {
            events.publishEvent(new OrderConfirmed(order.getId(), order.getPublicCode(), order.getStoreId()));
        } else if (to == OrderStatus.DELIVERED) {
            events.publishEvent(new OrderDelivered(order.getId(), order.getPublicCode(), order.getStoreId()));
        } else if (to == OrderStatus.CANCELLED) {
            events.publishEvent(new OrderCancelled(
                    order.getId(), order.getPublicCode(), order.getStoreId(), request.reason()));
        }

        // One line per state change, so an order's whole history is greppable by
        // its id. DELIVERY_FAILED and CANCELLED carry the reason staff typed,
        // because that is the part nobody can reconstruct afterwards.
        if (to == OrderStatus.DELIVERY_FAILED || to == OrderStatus.CANCELLED) {
            log.info("Order {} ({}): {} -> {} ({})",
                    order.getId(), order.getPublicCode(), from, to, request.reason());
        } else {
            log.info("Order {} ({}): {} -> {}", order.getId(), order.getPublicCode(), from, to);
        }

        // Admin surface: never expose the delivery OTP.
        return toDto(orders.findById(orderId).orElseThrow(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentDeliveryStat> deliveryStatsByAgent(int days) {
        Instant since = Instant.now(clock).minus(Duration.ofDays(Math.max(days, 1)));
        return orders.countDeliveredByAgentAndDay(since).stream()
                .map(r -> new AgentDeliveryStat(r.getAgentId(), r.getDay(), r.getDeliveries(), r.getAmount()))
                .toList();
    }

    @Override
    public OrderDto assignAgent(Long orderId, Long agentId) {
        OrderEntity order = orders.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "Cannot change the delivery agent on a " + status + " order.");
        }
        // A non-null assignment must be an existing, active DELIVERY_AGENT —
        // otherwise the order would silently drop out of every agent's queue and
        // could never be confirmed via the agent path. null clears the assignment.
        //
        // The message is written for the staff member who will read it verbatim
        // in the queue: the admin dropdown lists active riders as they were when
        // the page loaded, so the way to get here is picking one deactivated
        // since. "Agent 5 is not an active delivery agent" named an internal id
        // and read as a validation trace, and the UI's fallback ("please try
        // again") was worse still — retrying never works. It stops short of
        // asserting WHY, because this guard is equally false for an id that is
        // not a rider at all and for one that does not exist; telling either of
        // those callers to reactivate an account would send them looking for one
        // that was never there.
        if (agentId != null && !authService.isActiveDeliveryAgent(agentId)) {
            throw new BusinessRuleException(
                    "That rider can no longer take orders — the account has been "
                            + "deactivated, or is not a rider account. Pick another "
                            + "rider, or check it under Riders.");
        }
        // Off duty is the rider's own switch: they keep what they hold, but a
        // NEW job must not land on someone who has gone home.
        //
        // An order that is ALREADY out is not a new job. Assigning there is
        // recording who has the goods — and it has to stay possible, because
        // DELIVERED now requires an assignment: an order that went out
        // unassigned, or whose rider went off duty at the end of the shift
        // before anyone marked it delivered, would otherwise have no way to be
        // completed at all. Cancelling a delivery that physically happened is
        // not an acceptable only-option, so the on-duty rule is relaxed once
        // the goods have left the shop.
        boolean alreadyOut = status == OrderStatus.OUT_FOR_DELIVERY
                || status == OrderStatus.DELIVERY_FAILED;
        if (agentId != null && !alreadyOut && !authService.isAvailableDeliveryAgent(agentId)) {
            throw new BusinessRuleException(
                    "That rider is off duty right now — pick another, or ask them to go on duty.");
        }
        Long previousAgentId = order.getAssignedAgentId();
        order.setAssignedAgentId(agentId); // null clears the assignment (back to pool)

        // Re-saving the same agent is a no-op, not a new job — don't buzz a
        // rider's phone again for an order they already have.
        if (!Objects.equals(previousAgentId, agentId)) {
            events.publishEvent(new OrderAssigned(
                    order.getId(), order.getPublicCode(), order.getStoreId(), agentId, previousAgentId,
                    status.name(), order.getAddressLine()));
            // Who is carrying what is the question asked when a parcel goes
            // missing, and the previous holder is half the answer.
            log.info("Order {} ({}) assigned to rider {} (was {}) while {}",
                    order.getId(), order.getPublicCode(), agentId, previousAgentId, status);
        }
        return toDto(order, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderDto> listAgentOrders(Long agentId, String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? orders.findByAssignedAgentIdOrderByPlacedAtDescIdDesc(agentId, pageable)
                : orders.findByAssignedAgentIdAndStatusOrderByPlacedAtDescIdDesc(
                        agentId, parseStatus(status), pageable);
        // Agent surface: never expose the OTP — they collect it from the customer.
        return PagedResponse.of(page, o -> toDto(o, false));
    }

    @Override
    @Transactional(readOnly = true)
    public AgentDaySummary agentDaySummary(Long agentId, LocalDate day) {
        OrderRepository.AgentDayRow row =
                orders.summarizeAgentDeliveries(agentId, startOfStoreDay(day), startOfStoreDay(day.plusDays(1)));
        return new AgentDaySummary(day, row.getDeliveries(), row.getCodOrders(), row.getCodAmount());
    }

    @Override
    @Transactional(readOnly = true)
    public AgentDaySummary storeDaySummary(LocalDate day) {
        OrderRepository.AgentDayRow row =
                orders.summarizeAllDeliveries(startOfStoreDay(day), startOfStoreDay(day.plusDays(1)));
        return new AgentDaySummary(day, row.getDeliveries(), row.getCodOrders(), row.getCodAmount());
    }

    /**
     * Midnight on {@code day} in the STORE's zone, not UTC.
     *
     * <p>The window has to be the day people lived: a shift that ends at 21:30
     * IST is one day's takings, and bucketing by the UTC date would split it —
     * IST is UTC+5:30, so every delivery between 00:00 and 05:29 IST falls on
     * the previous UTC date. Settling up would then be reconciling against a
     * figure that starts halfway through the morning.
     */
    private Instant startOfStoreDay(LocalDate day) {
        ZoneId zone = clock.getZone();
        return day.atStartOfDay(zone).toInstant();
    }

    @Override
    public OrderDto confirmDelivery(Long orderId, Long agentId, String otp) {
        requireAssignedTo(orderId, agentId);
        return transition(orderId, new TransitionRequest("DELIVERED", otp, null));
    }

    @Override
    public OrderDto failDelivery(Long orderId, Long agentId, String reason) {
        requireAssignedTo(orderId, agentId);
        // Stock stays RESERVED here on purpose: the goods are still in the
        // rider's bag. Only a later CANCELLED releases them, once staff have
        // the bag back on the shelf; a re-dispatch keeps the same reservation.
        return transition(orderId, new TransitionRequest("DELIVERY_FAILED", null, reason));
    }

    /** An agent may act only on orders assigned to them; anything else is not theirs to touch. */
    private void requireAssignedTo(Long orderId, Long agentId) {
        OrderEntity order = orders.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (order.getAssignedAgentId() == null || !order.getAssignedAgentId().equals(agentId)) {
            // A rider acting on an order that is not theirs. Usually a stale
            // queue on a phone that has been offline — but it is a refused
            // authorisation either way, and the pair of ids says which it was.
            log.warn("Rider {} refused action on order {}: assigned to {}",
                    agentId, orderId, order.getAssignedAgentId());
            throw new AccessDeniedException("This order is not assigned to you.");
        }
    }

    @Override
    public void recordAgentLocation(Long agentId, double lat, double lng, Double accuracyMeters) {
        // The phone's Geolocation API hands over doubles; anything outside the
        // globe, or NaN from a failed parse, is a client bug and is refused
        // rather than stored where the customer's map would try to draw it.
        if (!Double.isFinite(lat) || lat < -90 || lat > 90) {
            throw new IllegalArgumentException("lat must be between -90 and 90");
        }
        if (!Double.isFinite(lng) || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("lng must be between -180 and 180");
        }
        if (accuracyMeters != null && (!Double.isFinite(accuracyMeters) || accuracyMeters < 0)) {
            throw new IllegalArgumentException("accuracyMeters must be zero or more");
        }
        // Store clock, deliberately: the freshness check in riderLocationFor
        // compares against the same clock, so a phone with its time wrong can
        // neither age a live fix out nor keep a dead one alive.
        agentLocations.upsert(agentId, lat, lng, accuracyMeters, Instant.now(clock));
    }

    @Override
    public void clearAgentLocation(Long agentId) {
        agentLocations.findById(agentId).ifPresent(agentLocations::delete);
    }

    /**
     * The assigned rider's position for the customer's tracking view, or null.
     *
     * <p>Every one of these conditions is a deliberate gate, not a null check:
     * the order must be OUT_FOR_DELIVERY (before that the rider is not driving
     * to this customer, even if already assigned; after it there is nothing to
     * watch), a rider must be assigned, they must have reported at all, and the
     * report must be fresh (see {@link #RIDER_LOCATION_TTL}). Only the tracking
     * read calls this — see {@link #getOrderByToken}.
     */
    private RiderLocationDto riderLocationFor(OrderEntity o) {
        if (o.getStatus() != OrderStatus.OUT_FOR_DELIVERY || o.getAssignedAgentId() == null) {
            return null;
        }
        Instant freshAfter = Instant.now(clock).minus(RIDER_LOCATION_TTL);
        return agentLocations.findById(o.getAssignedAgentId())
                .filter(l -> !l.getRecordedAt().isBefore(freshAfter))
                .map(l -> new RiderLocationDto(l.getLat(), l.getLng(), l.getRecordedAt()))
                .orElse(null);
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
     * but nobody can fulfil the order).
     *
     * <p>The open/closed decision is NOT recomputed here — it comes from
     * {@code serviceability}, which owns store hours and evaluates them on the
     * server clock. That is what keeps the storefront's closed banner and this
     * rejection in agreement; two copies of the rule would eventually drift.
     */
    private void requireStoreOpen(StoreDto store) {
        if (!store.open()) {
            throw new BusinessRuleException(
                    store.name() + " is closed right now. Delivery hours are "
                            + store.openingTime() + "–" + store.closingTime()
                            + ". Please order during open hours.");
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

    /** Map an order to its public DTO with no rider position — every read but the tracking page. */
    private OrderDto toDto(OrderEntity o, boolean customerFacing) {
        return toDto(o, customerFacing, null);
    }

    /**
     * Map an order to its public DTO.
     *
     * @param customerFacing when {@code true} (the tracking endpoint) the delivery
     *     OTP is included <em>only</em> while the order is OUT_FOR_DELIVERY;
     *     when {@code false} (admin surface) the OTP is never included.
     * @param riderLocation the assigned rider's fresh position, or null. The
     *     caller decides — only {@link #getOrderByToken} ever passes one, having
     *     applied the gates in {@link #riderLocationFor}; this method does not
     *     look it up itself so that the list reads stay one query.
     */
    private OrderDto toDto(OrderEntity o, boolean customerFacing, RiderLocationDto riderLocation) {
        List<OrderItemDto> items = o.getItems().stream()
                // NOTE: cost price (COGS) is intentionally NOT mapped — internal only.
                .map(i -> new OrderItemDto(i.getProductName(), i.getProductNameKn(), i.getLabel(),
                        i.getUnitPrice(), i.getQty(), i.getLineTotal(),
                        i.getHsnCode(), i.getGstRate(),
                        i.getTaxableValue(), i.getCgst(), i.getSgst()))
                .toList();
        List<OrderTimelineEntryDto> timeline = o.getEvents().stream()
                .map(e -> new OrderTimelineEntryDto(e.getToStatus(), e.getAt(), e.getReason()))
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
                o.getPublicCode(),
                o.getStatus().name(),
                o.getPaymentMethod(),
                o.getPaymentStatus(),
                o.getCustomerName(),
                o.getPhone(),
                new AddressDto(o.getAddressLine(), o.getLat(), o.getLng()),
                items,
                o.getSubtotal(),
                o.getTotal(),
                o.getTotalTax(),
                deliveryOtp,
                o.getPlacedAt(),
                timeline,
                o.getAssignedAgentId(),
                o.getInvoiceNumber(),
                o.getInvoicedAt(),
                riderLocation);
    }
}

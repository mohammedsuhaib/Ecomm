-- ============================================================================
-- DEV-ONLY: seed mock orders for ONE customer account so they show up in the
-- storefront "Order History" (GET /orders/mine filters by user_id).
--
-- Targets the customer by phone (default 9632500797). Each order is linked to
-- that user_id, carries a unique public_token (trackable at /order/{token}) and
-- is tagged idempotency_key LIKE 'mock-user-%' so re-running clears & rebuilds
-- only this user's mock orders. Real orders are untouched.
--
--   docker compose -f infra/docker-compose.yml exec -T postgres \
--     psql -U townbasket -d townbasket < infra/dev/mock-user-orders.sql
-- ============================================================================

DO $$
DECLARE
    v_phone   text := '9632500797';
    v_name    text := 'Mohammed Suhaib';
    v_addr    text := '130, 11th Cross, KEB Colony, Udayagiri';
    v_lat     double precision := 12.213696740433742;
    v_lng     double precision := 76.90472611883139;
    v_user_id bigint;
    i           int;
    v_order_id  bigint;
    v_placed    timestamptz;
    v_status    text;
    v_pay       text;
    v_paystatus text;
    v_subtotal  numeric(10,2);
    n_items     int;
    v_qty       int;
    rec         record;
    statuses    text[] := ARRAY[
        'DELIVERED','DELIVERED','DELIVERED','DELIVERED','DELIVERED','DELIVERED',
        'OUT_FOR_DELIVERY','PACKING','CONFIRMED','PLACED','CANCELLED'];
BEGIN
    SELECT id INTO v_user_id FROM identity.users WHERE phone = v_phone;
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'No customer found with phone % — log in once (dev:%), then re-run.', v_phone, v_phone;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM catalog.product_variants) THEN
        RAISE EXCEPTION 'Catalogue is empty — run migrations/seed first.';
    END IF;

    -- Clear only THIS user's prior mock orders (real orders untouched).
    DELETE FROM orders.order_events
     WHERE order_id IN (SELECT id FROM orders.orders
                         WHERE idempotency_key LIKE 'mock-user-%' AND user_id = v_user_id);
    DELETE FROM orders.order_items
     WHERE order_id IN (SELECT id FROM orders.orders
                         WHERE idempotency_key LIKE 'mock-user-%' AND user_id = v_user_id);
    DELETE FROM orders.orders
     WHERE idempotency_key LIKE 'mock-user-%' AND user_id = v_user_id;

    -- 12 orders spread over the last ~6 weeks, newest a few hours ago.
    FOR i IN 1..12 LOOP
        v_placed := CURRENT_TIMESTAMP
                    - ((i - 1) * 3 || ' days')::interval
                    - (random() * interval '8 hours');
        v_status := statuses[1 + floor(random() * array_length(statuses, 1))::int];
        -- Keep the most recent order active so the live tracking page has something to show.
        IF i = 1 THEN v_status := 'OUT_FOR_DELIVERY'; END IF;
        v_pay := CASE WHEN random() < 0.5 THEN 'COD' ELSE 'UPI' END;
        v_paystatus := CASE
                          WHEN v_pay = 'UPI'          THEN 'PAID'
                          WHEN v_status = 'DELIVERED'  THEN 'PAID'
                          ELSE 'COD_PENDING'
                       END;

        INSERT INTO orders.orders (
            cart_id, store_id, user_id, customer_name, phone, address_line, lat, lng,
            payment_method, payment_status, status, subtotal, total,
            delivery_otp, public_token, idempotency_key, placed_at)
        VALUES (
            gen_random_uuid(), 1, v_user_id, v_name, v_phone, v_addr, v_lat, v_lng,
            v_pay, v_paystatus, v_status, 0, 0,
            lpad(floor(random() * 10000)::text, 4, '0'),
            gen_random_uuid(),
            'mock-user-' || gen_random_uuid(), v_placed)
        RETURNING id INTO v_order_id;

        n_items := 1 + floor(random() * 4)::int;
        v_subtotal := 0;
        FOR rec IN
            SELECT pv.id AS variant_id, p.name AS product_name, pv.label,
                   pv.selling_price, pv.cost_price
            FROM catalog.product_variants pv
            JOIN catalog.products p ON p.id = pv.product_id
            ORDER BY random()
            LIMIT n_items
        LOOP
            v_qty := 1 + floor(random() * 3)::int;
            INSERT INTO orders.order_items (
                order_id, variant_id, product_name, label,
                unit_price, cost_price, qty, line_total)
            VALUES (
                v_order_id, rec.variant_id, rec.product_name, rec.label,
                rec.selling_price, rec.cost_price, v_qty, rec.selling_price * v_qty);
            v_subtotal := v_subtotal + rec.selling_price * v_qty;
        END LOOP;
        UPDATE orders.orders SET subtotal = v_subtotal, total = v_subtotal WHERE id = v_order_id;

        -- Timeline: PLACED, then the current status (so the tracking page renders steps).
        INSERT INTO orders.order_events (order_id, from_status, to_status, at)
        VALUES (v_order_id, NULL, 'PLACED', v_placed);
        IF v_status <> 'PLACED' THEN
            INSERT INTO orders.order_events (order_id, from_status, to_status, at)
            VALUES (v_order_id, 'PLACED', v_status, v_placed + interval '40 minutes');
        END IF;
    END LOOP;

    RAISE NOTICE 'Created 12 mock orders for user_id % (phone %).', v_user_id, v_phone;
END $$;

-- Show what this user now has.
SELECT id, status, total, placed_at
FROM orders.orders
WHERE phone = '9632500797'
ORDER BY placed_at DESC;

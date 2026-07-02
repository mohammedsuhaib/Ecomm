-- ============================================================================
-- DEV-ONLY mock data for testing the admin Analytics dashboard.
--
-- Generates ~30 days of orders for store_id = 1 (the seeded MVP store) drawn
-- from the real seeded catalogue, so all four analytics panels populate:
--   • Summary KPIs  (today revenue/orders/delivered, pending queue, week-to-date)
--   • Daily chart   (revenue + gross profit per day — needs order_items.cost_price)
--   • Top products  (grouped by product_name + variant label)
--   • Low stock     (drops a few inventory.stock_levels below threshold)
--
-- RE-RUNNABLE: every order it creates is tagged idempotency_key LIKE 'mock-%',
-- so the script first deletes prior mock data and regenerates. It never touches
-- real orders. Run it against the dev DB:
--
--   docker compose -f infra/docker-compose.yml exec -T postgres \
--     psql -U townbasket -d townbasket < infra/dev/mock-analytics-data.sql
--
-- Then open the admin app (http://localhost:3001 → Analytics tab).
-- ============================================================================

DO $$
DECLARE
    d           int;
    n_orders    int;
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
    -- DELIVERED weighted heavily (real revenue), with a realistic tail of the
    -- live pipeline statuses and a few cancellations.
    statuses    text[] := ARRAY[
        'DELIVERED','DELIVERED','DELIVERED','DELIVERED','DELIVERED',
        'OUT_FOR_DELIVERY','PACKING','CONFIRMED','PLACED','CANCELLED'];
    names       text[] := ARRAY[
        'Asha R','Ravi Kumar','Priya S','Kiran M','Vijay N',
        'Lakshmi B','Suresh P','Deepa V','Manoj K','Anita G'];
BEGIN
    -- Guard: catalogue must be seeded (V3_2) or there are no variants to sell.
    IF NOT EXISTS (SELECT 1 FROM catalog.product_variants) THEN
        RAISE EXCEPTION 'No catalog.product_variants found — run migrations/seed first.';
    END IF;

    -- 1) Clear any previous mock data (real orders are untouched).
    DELETE FROM orders.order_events
     WHERE order_id IN (SELECT id FROM orders.orders WHERE idempotency_key LIKE 'mock-%');
    DELETE FROM orders.order_items
     WHERE order_id IN (SELECT id FROM orders.orders WHERE idempotency_key LIKE 'mock-%');
    DELETE FROM orders.orders WHERE idempotency_key LIKE 'mock-%';

    -- 2) Generate orders for each of the last 30 days (d=0 is today).
    FOR d IN 0..29 LOOP
        n_orders := 3 + floor(random() * 8)::int;          -- 3..10 orders/day
        FOR i IN 1..n_orders LOOP
            -- Cluster each day's orders in an 11h window ending "d days ago from
            -- now", so they reliably fall on the intended IST calendar day.
            v_placed := CURRENT_TIMESTAMP
                        - (d || ' days')::interval
                        - (random() * interval '11 hours');

            v_status := statuses[1 + floor(random() * array_length(statuses, 1))::int];
            v_pay    := CASE WHEN random() < 0.5 THEN 'COD' ELSE 'UPI' END;
            v_paystatus := CASE
                              WHEN v_pay = 'UPI'        THEN 'PAID'
                              WHEN v_status = 'DELIVERED' THEN 'PAID'
                              ELSE 'COD_PENDING'
                           END;

            INSERT INTO orders.orders (
                cart_id, store_id, customer_name, phone, address_line, lat, lng,
                payment_method, payment_status, status, subtotal, total,
                delivery_otp, public_token, idempotency_key, placed_at)
            VALUES (
                gen_random_uuid(), 1,
                names[1 + floor(random() * array_length(names, 1))::int],
                '9' || lpad(floor(random() * 1000000000)::text, 9, '0'),
                'Mock Address, Mysuru 570001', 12.2958, 76.6394,
                v_pay, v_paystatus, v_status, 0, 0,
                lpad(floor(random() * 10000)::text, 4, '0'),
                gen_random_uuid(),
                'mock-' || gen_random_uuid(), v_placed)
            RETURNING id INTO v_order_id;

            -- 1..4 distinct random variants per order.
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
                v_qty := 1 + floor(random() * 3)::int;     -- 1..3 units
                INSERT INTO orders.order_items (
                    order_id, variant_id, product_name, label,
                    unit_price, cost_price, qty, line_total)
                VALUES (
                    v_order_id, rec.variant_id, rec.product_name, rec.label,
                    rec.selling_price, rec.cost_price, v_qty,
                    rec.selling_price * v_qty);
                v_subtotal := v_subtotal + rec.selling_price * v_qty;
            END LOOP;

            UPDATE orders.orders
               SET subtotal = v_subtotal, total = v_subtotal
             WHERE id = v_order_id;

            -- Minimal timeline so the order looks real (PLACED → current status).
            INSERT INTO orders.order_events (order_id, from_status, to_status, at)
            VALUES (v_order_id, NULL, 'PLACED', v_placed);
            IF v_status <> 'PLACED' THEN
                INSERT INTO orders.order_events (order_id, from_status, to_status, at)
                VALUES (v_order_id, 'PLACED', v_status, v_placed + interval '45 minutes');
            END IF;
        END LOOP;
    END LOOP;

    -- 3) Trigger low-stock alerts (threshold is 5 in the seed): push 3 variants
    --    to "low" and 2 to "out of stock". Re-running resets nothing else; if you
    --    want stock back to full, re-apply V4_2 or set on_hand = 100.
    UPDATE inventory.stock_levels SET on_hand = 3, reserved = 0
     WHERE store_id = 1
       AND variant_id IN (SELECT id FROM catalog.product_variants ORDER BY id ASC LIMIT 3);

    UPDATE inventory.stock_levels SET on_hand = 0, reserved = 0
     WHERE store_id = 1
       AND variant_id IN (SELECT id FROM catalog.product_variants ORDER BY id DESC LIMIT 2);

    RAISE NOTICE 'Mock analytics data generated for store 1 (last 30 days).';
END $$;

-- Quick sanity peek (printed after the block runs).
SELECT status, COUNT(*) AS orders, COALESCE(SUM(total), 0) AS revenue
FROM orders.orders
WHERE idempotency_key LIKE 'mock-%'
GROUP BY status
ORDER BY orders DESC;

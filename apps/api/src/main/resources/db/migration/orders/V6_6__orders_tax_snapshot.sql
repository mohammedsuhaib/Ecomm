-- Per-line GST snapshot, taken at checkout (like the price/COGS snapshot):
-- catalog rate changes must never mutate the tax on historical orders or
-- already-issued invoices.
--
-- Prices are tax-INCLUSIVE, so these columns decompose the existing line_total
-- (taxable_value + cgst + sgst = line_total); order totals are unchanged.

ALTER TABLE orders.orders
    ADD COLUMN total_tax NUMERIC(10,2) NOT NULL DEFAULT 0;

ALTER TABLE orders.order_items
    ADD COLUMN hsn_code      TEXT,
    ADD COLUMN gst_rate      NUMERIC(4,2)  NOT NULL DEFAULT 0,
    ADD COLUMN taxable_value NUMERIC(10,2),
    ADD COLUMN cgst          NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN sgst          NUMERIC(10,2) NOT NULL DEFAULT 0;

-- Pre-GST orders: no rate was known at sale time, so the whole line is its
-- taxable value and the extracted tax is zero.
UPDATE orders.order_items SET taxable_value = line_total WHERE taxable_value IS NULL;
ALTER TABLE orders.order_items ALTER COLUMN taxable_value SET NOT NULL;

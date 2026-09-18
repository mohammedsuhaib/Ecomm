-- The supplier GSTIN as it stood when this order's invoice was issued.
--
-- The invoice PDF is re-rendered on every download, but an issued tax invoice
-- is a fixed document: invoice_number and invoiced_at are already write-once
-- for exactly that reason, and the item prices and GST breakdown are
-- snapshotted per line in order_items. The GSTIN was the one thing on the page
-- still read live from configuration, which did not matter while it could only
-- change by redeploy — but now that staff can edit it (V8_6), re-downloading an
-- old invoice would silently reprint it under a different registration number.
--
-- So it is snapshotted here alongside the number it was issued with. Null for
-- orders invoiced before this column existed, and for orders with no invoice at
-- all; the renderer omits the line in both cases, which is what those invoices
-- were issued with.

ALTER TABLE orders.orders
    ADD COLUMN IF NOT EXISTS invoice_gstin TEXT;

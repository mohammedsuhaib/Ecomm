-- The store's GSTIN, editable by staff from the admin store card.
--
-- A tax invoice must carry the supplier's GSTIN, and until now the only way to
-- set one was the TOWNBASKET_INVOICE_GSTIN environment variable — so the number
-- that makes an invoice legally valid needed a redeploy, and was blank in every
-- environment. It belongs beside the store's other registration details (name,
-- address, support phone) where whoever completes the GST registration can
-- enter it themselves.
--
-- Nullable on purpose: a store that is not registered yet must still be able to
-- save the rest of the card, and the invoice simply omits the line while it is
-- blank (as it always has) rather than printing an empty label.
--
-- TEXT, not CHAR(15): the length rule belongs with the validation in Gstin.java,
-- where a bad value can be refused with an explanation, rather than with the
-- storage, where it would surface as a driver error.

ALTER TABLE serviceability.stores
    ADD COLUMN IF NOT EXISTS gstin TEXT;

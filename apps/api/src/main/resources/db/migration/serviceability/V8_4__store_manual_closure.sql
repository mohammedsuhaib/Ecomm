-- Manual closure ("closed for today"): a festival, a power cut, a staff
-- emergency. Until now the only lever was editing opening/closing_time in SQL.
--
-- closed_until is an instant, not a flag: it expires on its own, so a store
-- that forgets to reopen is back in business next morning instead of silently
-- losing a day of orders. NULL or a past instant means "trading hours apply".
ALTER TABLE serviceability.stores
    ADD COLUMN closed_until  TIMESTAMPTZ,
    ADD COLUMN closed_reason TEXT;

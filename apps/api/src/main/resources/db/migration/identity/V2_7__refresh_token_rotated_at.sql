-- When a refresh token was spent by ROTATION (as opposed to logout or a family
-- revoke, which leave this NULL).
--
-- A phone resuming from sleep, or two tabs, can present the same refresh token
-- twice within moments: the first call rotates it, the second arrives holding a
-- token that is already spent. That used to be indistinguishable from a stolen
-- token being replayed, and the response — revoke every session the person has —
-- signed them out everywhere. Knowing WHEN the token was rotated lets the
-- server treat a replay inside a short grace window as the benign race it is,
-- while a replay long after rotation is still treated as theft.

ALTER TABLE identity.refresh_tokens ADD COLUMN rotated_at TIMESTAMPTZ;

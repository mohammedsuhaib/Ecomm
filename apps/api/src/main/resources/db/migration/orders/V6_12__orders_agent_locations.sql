-- Where each rider is right now, for the customer's live "rider on the way" view.
--
-- One row per agent, overwritten on every ping (a rider's phone reports every
-- ~8 s while they have deliveries in hand), so this is a table of CURRENT
-- positions, not a track log — there is no history to keep, and keeping one
-- would be surveillance of staff rather than reassurance for a customer.
--
-- Lives in `orders` because dispatch already does: `assigned_agent_id` is an
-- orders column, and the ONLY way a position is ever read back out is through
-- an order the agent is delivering (see OrderServiceImpl#riderLocationFor).
-- `agent_id` is an identity.users id — a plain cross-module id with no
-- cross-schema FK, consistent with `assigned_agent_id` on orders.orders.
--
-- `accuracy_m` is the phone's own radius estimate in metres, kept so a later UI
-- can draw it; the customer view currently ignores it. `recorded_at` is the
-- store clock at the moment the ping was accepted, not the phone's timestamp,
-- so a device with a wrong clock cannot make a stale fix look fresh.
CREATE TABLE orders.agent_locations (
    agent_id    BIGINT           PRIMARY KEY,
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    accuracy_m  DOUBLE PRECISION,
    recorded_at TIMESTAMPTZ      NOT NULL
);

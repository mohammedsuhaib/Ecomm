-- Assign an order to a delivery agent (identity.users.id of a DELIVERY_AGENT).
-- A plain cross-module id with no cross-schema FK — consistent with user_id /
-- store_id elsewhere on this table. Nullable: orders are unassigned until a
-- manager dispatches them to a rider. Indexed for the agent's "my deliveries"
-- queue (assigned_agent_id + status).
ALTER TABLE orders.orders ADD COLUMN assigned_agent_id BIGINT;

CREATE INDEX idx_orders_assigned_agent ON orders.orders (assigned_agent_id);

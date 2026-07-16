package com.townbasket.orders;

/**
 * Admin dispatch request: the delivery agent to assign to an order.
 * A {@code null} {@code agentId} clears the assignment (un-assign / return to pool).
 */
public record AssignAgentRequest(Long agentId) {
}

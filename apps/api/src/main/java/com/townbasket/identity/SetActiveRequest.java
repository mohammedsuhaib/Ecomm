package com.townbasket.identity;

/** Admin request to activate ({@code true}) or deactivate ({@code false}) an account. */
public record SetActiveRequest(boolean active) {
}

package com.townbasket.inventory;

/** Physical-count correction: sets on_hand to an absolute value for a variant. */
public record StockCorrectionRequest(int newOnHand, String reason) {}

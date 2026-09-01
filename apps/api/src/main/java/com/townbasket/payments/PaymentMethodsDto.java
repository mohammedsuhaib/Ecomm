package com.townbasket.payments;

import java.util.List;

/**
 * What the storefront may offer at checkout. The UI renders exactly these, so a
 * method that is off never appears as a choice a customer can make and then be
 * refused for.
 */
public record PaymentMethodsDto(List<PaymentMethod> methods) {
}

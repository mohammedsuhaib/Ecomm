/**
 * {@code cart} module — server-side cart keyed to the user (survives devices and
 * reinstalls) with anonymous-cart merge on login. Validates price and stock at
 * read time.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Cart")
package com.townbasket.cart;

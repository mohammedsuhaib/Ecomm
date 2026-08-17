/**
 * {@code tax} module — GST calculation for tax-inclusive (MRP) retail pricing.
 *
 * <p>Indian groceries are sold at a tax-inclusive shelf price, so the engine
 * <em>extracts</em> GST from a gross amount rather than adding it on top: the
 * customer-facing price never changes, but every order line carries a taxable
 * value + CGST/SGST split for the invoice. A single-town store always delivers
 * intra-state, so IGST never applies and the tax always splits half CGST, half
 * SGST.
 *
 * <p>Stateless by design: rates are data owned by {@code catalog} (per product)
 * and snapshotted by {@code orders} at checkout; this module owns the legal
 * slab list and the arithmetic. No schema, no entities.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Tax")
package com.townbasket.tax;

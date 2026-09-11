import type { Product, ProductVariant } from './types';

// One definition of which variant a product TILE is about, shared by the price
// shown on the card and the variant the quick-add button puts in the cart.
// These two lived apart once — the card priced the cheapest variant of ALL of
// them (even out-of-stock ones) while quick-add added the first BUYABLE one in
// list order — so a tile could show ₹25 and silently add the ₹110 pack.

/** Sellable right now: switched on by the store AND has live stock. */
export function isBuyable(v: ProductVariant): boolean {
  return v.available && v.availableStock > 0;
}

function cheapest(variants: ProductVariant[]): ProductVariant | null {
  return variants.reduce<ProductVariant | null>(
    (min, v) => (min == null || v.sellingPrice < min.sellingPrice ? v : min),
    null,
  );
}

/**
 * The variant quick-add adds: the CHEAPEST buyable one — never "first in array
 * order", which depends on insertion order and matches nothing the customer
 * sees. Null when the product is off or nothing is sellable.
 */
export function cheapestBuyableVariant(product: Product): ProductVariant | null {
  if (product.available === false) return null;
  return cheapest((product.variants ?? []).filter(isBuyable));
}

/**
 * The variant whose price the card shows. Prefers the buyable pick above so the
 * shown price is the price of what "+" adds; only when nothing is sellable does
 * it fall back to the cheapest overall for the "from" price — and in that state
 * the card renders its out-of-stock tag and no quick-add exists.
 */
export function displayVariant(product: Product): ProductVariant | null {
  return cheapestBuyableVariant(product) ?? cheapest(product.variants ?? []);
}

import type { Locale } from '@/i18n/config';
import type { Product } from './types';

// Product names are catalogue data. The backend stores a Kannada transliteration
// in `nameKn` (catalog.products.name_kn); when browsing in Kannada we show it,
// falling back to the English `name` whenever it is missing.
export function productDisplayName(
  product: Pick<Product, 'name' | 'nameKn'>,
  locale: Locale,
): string {
  if (locale === 'kn' && product.nameKn) return product.nameKn;
  return product.name;
}

/**
 * The same choice for a cart line, an order line, or a price-change entry.
 *
 * These are not products: they carry the two names under `productName` /
 * `productNameKn` because a line is a *reference* to a product (the cart) or a
 * *snapshot* of one (an order). A cart that says "Amul Butter" for the tile the
 * customer just tapped in Kannada reads as a different item, so every surface
 * that names a line goes through here.
 */
export function lineDisplayName(
  line: { productName: string; productNameKn?: string | null },
  locale: Locale,
): string {
  if (locale === 'kn' && line.productNameKn) return line.productNameKn;
  return line.productName;
}

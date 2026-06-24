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

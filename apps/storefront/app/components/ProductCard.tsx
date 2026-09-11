import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import type { Product } from '@/app/lib/types';
import { productDisplayName } from '@/app/lib/productName';
import { displayVariant, isBuyable } from '@/app/lib/variants';
import VegMarker from './VegMarker';
import PriceTag from './PriceTag';
import QuickAddButton from './QuickAddButton';
import ProductThumb from './ProductThumb';

/**
 * Compact product tile for grids. Links to the detail page and previews the
 * price of the SAME variant the quick-add button adds (see lib/variants.ts) —
 * a price from one variant with a "+" that adds a different one reads as the
 * cart changing the customer's choice.
 */
export default function ProductCard({ product }: { product: Product }) {
  const t = useTranslations('product');
  const locale = useLocale();
  const displayName = productDisplayName(product, locale);
  const variants = product.variants ?? [];
  // Priced variant = the one quick-add would put in the cart (cheapest buyable),
  // falling back to cheapest overall only when nothing is sellable.
  const priced = displayVariant(product);
  // Out of stock = product is on, has variants, but none are sellable right now.
  const outOfStock =
    product.available && variants.length > 0 && !variants.some(isBuyable);

  return (
    <Link
      href={`/product/${product.slug}`}
      className="product-card"
      aria-label={displayName}
    >
      <div className="thumb">
        <ProductThumb product={product} />
        {/* Inline quick add — overlays the thumb; stops navigation on tap. */}
        <div className="quick-add-slot">
          <QuickAddButton product={product} />
        </div>
      </div>
      <div className="body">
        <span style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
          <VegMarker veg={product.vegMarker} />
          <span className="name">{displayName}</span>
        </span>
        {priced ? (
          <PriceTag sellingPrice={priced.sellingPrice} mrp={priced.mrp} />
        ) : null}
        {!product.available ? (
          <span className="unavailable-tag">{t('currentlyUnavailable')}</span>
        ) : outOfStock ? (
          <span className="unavailable-tag">{t('outOfStock')}</span>
        ) : null}
      </div>
    </Link>
  );
}

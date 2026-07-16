import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import type { Product } from '@/app/lib/types';
import { productDisplayName } from '@/app/lib/productName';
import VegMarker from './VegMarker';
import PriceTag from './PriceTag';
import QuickAddButton from './QuickAddButton';
import ProductThumb from './ProductThumb';

/**
 * Compact product tile for grids. Links to the detail page and previews the
 * cheapest available variant's price. Cart actions live on the detail page.
 */
export default function ProductCard({ product }: { product: Product }) {
  const t = useTranslations('product');
  const locale = useLocale();
  const displayName = productDisplayName(product, locale);
  // Show the lowest-priced variant as the "from" price on the card.
  const variants = product.variants ?? [];
  const cheapest = variants.reduce<(typeof variants)[number] | null>(
    (min, v) => (min == null || v.sellingPrice < min.sellingPrice ? v : min),
    null,
  );
  // Out of stock = product is on, has variants, but none are sellable right now.
  const outOfStock =
    product.available &&
    variants.length > 0 &&
    !variants.some((v) => v.available && v.availableStock > 0);

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
        {cheapest ? (
          <PriceTag
            sellingPrice={cheapest.sellingPrice}
            mrp={cheapest.mrp}
          />
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

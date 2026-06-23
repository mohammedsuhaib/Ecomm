import Link from 'next/link';
import { useTranslations } from 'next-intl';
import type { Product } from '@/app/lib/types';
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
  // Show the lowest-priced variant as the "from" price on the card.
  const variants = product.variants ?? [];
  const cheapest = variants.reduce<(typeof variants)[number] | null>(
    (min, v) => (min == null || v.sellingPrice < min.sellingPrice ? v : min),
    null,
  );

  return (
    <Link
      href={`/product/${product.slug}`}
      className="product-card"
      aria-label={product.name}
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
          <span className="name">{product.name}</span>
        </span>
        {cheapest ? (
          <PriceTag
            sellingPrice={cheapest.sellingPrice}
            mrp={cheapest.mrp}
          />
        ) : null}
        {!product.available && (
          <span className="unavailable-tag">{t('currentlyUnavailable')}</span>
        )}
      </div>
    </Link>
  );
}

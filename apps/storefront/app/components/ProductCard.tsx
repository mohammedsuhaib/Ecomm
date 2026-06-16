import Link from 'next/link';
import type { Product } from '@/app/lib/types';
import VegMarker from './VegMarker';
import PriceTag from './PriceTag';

/**
 * Compact product tile for grids. Links to the detail page and previews the
 * cheapest available variant's price. Cart actions live on the detail page.
 */
export default function ProductCard({ product }: { product: Product }) {
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
        {product.imageUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={product.imageUrl} alt={product.name} loading="lazy" />
        ) : (
          <span aria-hidden>🛒</span>
        )}
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
          <span className="unavailable-tag">Currently unavailable</span>
        )}
      </div>
    </Link>
  );
}

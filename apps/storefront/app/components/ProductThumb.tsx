import type { Product } from '@/app/lib/types';

/**
 * Product image for grids + the detail page. When a product has a real
 * {@code imageUrl} (e.g. set later via admin/CDN) it's shown. Otherwise — and
 * for the demo, where the seed sets none — we render a recognizable food emoji
 * on a soft, per-category-tinted tile, so every product reads as a distinct
 * image with no external hosts, no binary assets, and no CSP/network needs.
 */

// First keyword match wins; falls back to a basket. Ordered so specific terms
// (ghee/butter) are checked before broader ones.
const KEYWORD_EMOJI: ReadonlyArray<readonly [RegExp, string]> = [
  [/atta|flour|maida|besan|wheat/i, '🌾'],
  [/basmati|rice|sona|poha/i, '🍚'],
  [/dal|dhal|arhar|toor|moong|chana|urad|lentil|rajma|chickpea/i, '🫘'],
  [/ghee/i, '🧈'],
  [/butter/i, '🧈'],
  [/paneer|cheese/i, '🧀'],
  [/curd|yogurt|yoghurt|dahi|lassi/i, '🥛'],
  [/milk/i, '🥛'],
  [/oil|sunflower|mustard|olive/i, '🫒'],
  [/egg/i, '🥚'],
  [/tea|chai/i, '🍵'],
  [/coffee/i, '☕'],
  [/juice|cola|soda|soft drink|beverage|water/i, '🧃'],
  [/biscuit|cookie|parle|rusk/i, '🍪'],
  [/chips|namkeen|snack|wafer|kurkure/i, '🍿'],
  [/chocolate|candy/i, '🍫'],
  [/honey/i, '🍯'],
  [/bread/i, '🍞'],
  [/salt/i, '🧂'],
  [/sugar|jaggery/i, '🍬'],
  [/masala|spice|turmeric|chilli|chili|jeera|cumin|garam|pepper|coriander/i, '🌶️'],
  [/detergent|soap|cleaner|cleaning|dishwash|phenyl|floor|handwash/i, '🧴'],
];

function emojiFor(name: string): string {
  for (const [re, emoji] of KEYWORD_EMOJI) {
    if (re.test(name)) return emoji;
  }
  return '🛒';
}

/** Stable hue (0–359) from a seed, so each category tints consistently. */
function hueFor(seed: string): number {
  let h = 0;
  for (let i = 0; i < seed.length; i += 1) {
    h = (h * 31 + seed.charCodeAt(i)) % 360;
  }
  return h;
}

export default function ProductThumb({ product }: { product: Product }) {
  if (product.imageUrl) {
    // eslint-disable-next-line @next/next/no-img-element
    return <img src={product.imageUrl} alt={product.name} loading="lazy" />;
  }
  const hue = hueFor(product.categoryId || product.name);
  return (
    <span
      className="thumb-emoji"
      role="img"
      aria-label={product.name}
      style={{
        background: `linear-gradient(135deg, hsl(${hue} 70% 92%), hsl(${(hue + 45) % 360} 65% 84%))`,
      }}
    >
      {emojiFor(product.name)}
    </span>
  );
}

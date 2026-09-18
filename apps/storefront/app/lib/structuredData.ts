/**
 * schema.org blocks for the public pages.
 *
 * <p>Why bother: a single supermarket delivering inside five kilometres wins
 * or loses on local intent ("grocery delivery near me"), and the two things
 * that decide how a result is displayed are the shop's own entity — where it
 * is, when it is open, how far it delivers — and, on a product page, the price
 * and whether the item is actually in stock. Both of those we know exactly, so
 * they are worth stating in a form a crawler can read without guessing.
 *
 * <p><strong>Only ever state what the API actually says.</strong> Structured
 * data that disagrees with the page is worse than none: it earns a manual
 * action. So there are no ratings here (the shop has none), no invented
 * payment methods, and availability comes from the same stock figure the Add
 * to cart button uses.
 *
 * <p>Server-side only — these build plain objects for {@code JsonLd}.
 */
import { absoluteUrl } from './site';
import { productDisplayName } from './productName';
import type { Product, Store } from './types';

/** "08:00:00" (a Java LocalTime) → "08:00", which is what schema.org wants. */
function hhmm(time: string | null | undefined): string | null {
  if (!time) return null;
  const match = /^(\d{2}):(\d{2})/.exec(time);
  return match ? `${match[1]}:${match[2]}` : null;
}

/**
 * The shop itself: a GroceryStore with its address, position, hours and the
 * circle it delivers into. This is the block that feeds local results, so the
 * figures must match the ones staff set in the admin app — they do, because
 * they come from the same `/store` payload the storefront renders.
 */
export function groceryStoreJsonLd(store: Store): Record<string, unknown> {
  const opens = hhmm(store.openingTime);
  const closes = hhmm(store.closingTime);
  return {
    '@context': 'https://schema.org',
    '@type': 'GroceryStore',
    '@id': `${absoluteUrl('/')}#store`,
    name: store.name,
    url: absoluteUrl('/'),
    image: absoluteUrl('/icons/icon-512.png'),
    ...(store.address ? { address: { '@type': 'PostalAddress', streetAddress: store.address } } : {}),
    ...(store.supportPhone ? { telephone: store.supportPhone } : {}),
    geo: { '@type': 'GeoCoordinates', latitude: store.lat, longitude: store.lng },
    ...(opens && closes
      ? {
          openingHoursSpecification: [
            {
              '@type': 'OpeningHoursSpecification',
              // One window, every day: the store keeps a single pair of hours
              // rather than a per-weekday schedule, so saying anything more
              // specific here would be inventing it.
              dayOfWeek: [
                'Monday', 'Tuesday', 'Wednesday', 'Thursday',
                'Friday', 'Saturday', 'Sunday',
              ],
              opens,
              closes,
            },
          ],
        }
      : {}),
    // The delivery radius, as the geometry it actually is.
    areaServed: {
      '@type': 'GeoCircle',
      geoMidpoint: { '@type': 'GeoCoordinates', latitude: store.lat, longitude: store.lng },
      geoRadius: store.deliveryRadiusMeters,
    },
    currenciesAccepted: 'INR',
  };
}

/**
 * The site, plus how to search it. The SearchAction is what lets a result for
 * the shop carry its own search box; the URL is the real one the header form
 * submits to, so it cannot drift.
 */
export function webSiteJsonLd(siteName: string): Record<string, unknown> {
  return {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: siteName,
    url: absoluteUrl('/'),
    potentialAction: {
      '@type': 'SearchAction',
      target: {
        '@type': 'EntryPoint',
        urlTemplate: `${absoluteUrl('/search')}?q={search_term_string}`,
      },
      'query-input': 'required name=search_term_string',
    },
  };
}

/**
 * A product, with one Offer per variant.
 *
 * <p>An offer per variant rather than a single price range, because that is
 * what the shop sells: "5 kg" and "25 kg" are different offers at different
 * prices with their own stock. Availability is read from the same live figure
 * the page uses to enable or disable Add to cart, so the rich result cannot
 * promise something the basket then refuses.
 */
export function productJsonLd(product: Product, locale: string): Record<string, unknown> {
  const url = absoluteUrl(`/product/${product.slug}`);
  const name = productDisplayName(product, locale as 'en' | 'kn');
  const sellable = (v: Product['variants'][number]) =>
    product.available && v.available && v.availableStock > 0;

  return {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name,
    ...(product.description ? { description: product.description } : {}),
    ...(product.imageUrl ? { image: [absoluteUrl(product.imageUrl)] } : {}),
    url,
    sku: product.slug,
    offers: product.variants.map((variant) => ({
      '@type': 'Offer',
      name: variant.label,
      price: variant.sellingPrice,
      priceCurrency: 'INR',
      availability: sellable(variant)
        ? 'https://schema.org/InStock'
        : 'https://schema.org/OutOfStock',
      itemCondition: 'https://schema.org/NewCondition',
      url,
      seller: { '@id': `${absoluteUrl('/')}#store` },
    })),
  };
}

/** The trail the page already shows, in the form a result snippet can use. */
export function breadcrumbJsonLd(
  trail: { name: string; path: string }[],
): Record<string, unknown> {
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: trail.map((step, index) => ({
      '@type': 'ListItem',
      position: index + 1,
      name: step.name,
      item: absoluteUrl(step.path),
    })),
  };
}

import type { MetadataRoute } from 'next';
import { getCategories, getProducts } from './lib/api';
import { absoluteUrl, isNoIndex } from './lib/site';
import type { Category, Product } from './lib/types';

/**
 * /sitemap.xml — the catalogue, as a list a crawler can walk.
 *
 * <p>Built from the live API rather than a hand-kept file, because the shop's
 * catalogue changes when staff change it and nobody is going to remember to
 * edit an XML file. Dynamic for the same reason robots.txt is: the absolute
 * URLs depend on the runtime hostname.
 *
 * <p>The underlying catalogue reads are cached for an hour, so a crawler
 * hammering this route costs one pair of API calls per hour, not one per hit.
 */
export const dynamic = 'force-dynamic';

/** Page size for walking the catalogue. Large, because this is one crawl. */
const PAGE_SIZE = 200;

/**
 * Stop after this many pages. A single supermarket has thousands of lines at
 * most, and an unbounded loop against a paginated API is how a sitemap route
 * turns into an outage when something upstream starts lying about totals.
 */
const MAX_PAGES = 25;

/** One hour: fresh enough for a new product, cheap enough for repeat crawls. */
const CACHE_SECONDS = 3600;

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  // Nothing to offer a crawler on a deployment that should not be indexed.
  if (isNoIndex()) return [];

  const now = new Date();
  const entries: MetadataRoute.Sitemap = [
    { url: absoluteUrl('/'), lastModified: now, changeFrequency: 'daily', priority: 1 },
  ];

  const categories: Category[] = await getCategories({ revalidate: CACHE_SECONDS }).catch(() => []);
  for (const category of categories) {
    entries.push({
      url: absoluteUrl(`/category/${category.slug}`),
      lastModified: now,
      changeFrequency: 'daily',
      priority: 0.8,
    });
  }

  // Walk the catalogue. A failed page ends the walk rather than the request:
  // a partial sitemap is still useful, an error page is not.
  const seen = new Set<string>();
  for (let page = 0; page < MAX_PAGES; page++) {
    const result = await getProducts(undefined, page, PAGE_SIZE, {}, { revalidate: CACHE_SECONDS })
      .catch(() => null);
    const items: Product[] = result?.content ?? [];
    for (const product of items) {
      if (!product.slug || seen.has(product.slug)) continue;
      seen.add(product.slug);
      entries.push({
        url: absoluteUrl(`/product/${product.slug}`),
        lastModified: now,
        changeFrequency: 'weekly',
        priority: 0.6,
      });
    }
    if (!result || items.length < PAGE_SIZE) break;
  }

  return entries;
}

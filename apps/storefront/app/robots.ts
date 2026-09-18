import type { MetadataRoute } from 'next';
import { isNoIndex, siteUrl } from './lib/site';

/**
 * /robots.txt
 *
 * <p>Dynamic, not built into the image: it names the canonical host, which is
 * a runtime fact (see lib/site.ts). A statically generated file would carry
 * whatever the CI machine happened to have in its environment.
 */
export const dynamic = 'force-dynamic';

/**
 * What a crawler may read.
 *
 * <p>The catalogue is the whole point — home, categories and product pages are
 * server-rendered and open. Everything that belongs to one person is closed:
 * the account area, the basket, checkout and order tracking are all either
 * signed-in or keyed by an unguessable token, so indexing them would be
 * pointless at best. Search result pages are excluded because they are an
 * infinite space of near-duplicates of the pages above.
 */
export default function robots(): MetadataRoute.Robots {
  const base = siteUrl();

  if (isNoIndex()) {
    // A non-production deployment. Say no to everything, and advertise no
    // sitemap — there is nothing here anyone should find in a search result.
    return { rules: [{ userAgent: '*', disallow: '/' }] };
  }

  return {
    rules: [
      {
        userAgent: '*',
        allow: '/',
        disallow: ['/account', '/cart', '/checkout', '/order/', '/search'],
      },
    ],
    sitemap: `${base}/sitemap.xml`,
    host: base,
  };
}

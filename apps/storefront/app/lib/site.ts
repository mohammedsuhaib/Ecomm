/**
 * Where this deployment lives on the public internet, and whether it belongs
 * in a search index at all.
 *
 * <p><strong>Server-side only.</strong> These read plain runtime environment
 * variables, deliberately NOT `NEXT_PUBLIC_*`: Next inlines those when the
 * image is built, and the public hostname is a property of the deployment, not
 * of the build — the same image serves QA and production. Everything that needs
 * them (canonical URLs, the sitemap, robots.txt, structured data) is rendered
 * on the server, so nothing here has to reach the browser.
 *
 * <p>They are functions rather than constants on purpose: module evaluation can
 * happen during the build, and a constant would freeze the build machine's
 * environment into the image. Reading per call keeps the answer the one the
 * running container was given.
 *
 * <ul>
 *   <li>{@code SITE_URL} — the canonical origin, e.g. https://shop.town-basket.com.
 *       Must be the ONE hostname the shop answers on; every other host should
 *       redirect to it, or search engines split the site in two.</li>
 *   <li>{@code SEO_NOINDEX} — set to "true" on any non-production deployment.
 *       QA also sends an `X-Robots-Tag: noindex` header at the proxy; this is
 *       the second lock, and it is what makes robots.txt say so too.</li>
 * </ul>
 */

/** Used when SITE_URL is unset, so a misconfigured deploy still emits sane absolute URLs. */
export const DEFAULT_SITE_URL = 'https://shop.town-basket.com';

/** The canonical origin, with any trailing slash removed. */
export function siteUrl(): string {
  const raw = process.env.SITE_URL?.trim();
  const base = raw && /^https?:\/\//i.test(raw) ? raw : DEFAULT_SITE_URL;
  return base.replace(/\/+$/, '');
}

/** True on a deployment that must stay out of search results (QA, staging). */
export function isNoIndex(): boolean {
  return (process.env.SEO_NOINDEX ?? '').trim().toLowerCase() === 'true';
}

/**
 * An absolute URL for a site-relative path. Product images arrive from the API
 * as relative paths (`/images/products/x.jpg`), and both Open Graph and
 * schema.org require absolute ones.
 */
export function absoluteUrl(path: string): string {
  if (/^https?:\/\//i.test(path)) return path;
  return `${siteUrl()}${path.startsWith('/') ? path : `/${path}`}`;
}

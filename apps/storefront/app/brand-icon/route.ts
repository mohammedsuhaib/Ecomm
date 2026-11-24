import { brand } from '@/app/lib/brand';

// Per request: which shop this is comes from the BRAND the running container
// was given, not the build machine's.
export const dynamic = 'force-dynamic';

/**
 * The shop's app icon, as a stable address.
 *
 * <p>The service worker is compiled once and cannot know the brand, yet the
 * push notifications it shows carry the shop's icon. It asks for this address,
 * and the server answers with a redirect to the right file for this deployment.
 *
 * <p>The redirect is relative on purpose: an absolute one would be built from
 * the request's host, which behind the reverse proxy may be an internal name.
 */
export function GET(): Response {
  return new Response(null, {
    status: 302,
    headers: { Location: brand().assets.icon192 },
  });
}

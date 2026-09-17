import type { MetadataRoute } from 'next';

// Web app manifest (served by Next at /manifest.webmanifest). Makes the store
// dashboard installable on the shop's laptop or tablet, so staff open it from
// the home screen or taskbar and run it without browser chrome — the toolbar
// and tab strip are pure overhead on a screen that shows a live order queue
// all day.
//
// The icon is the brand shopfront tile (brand/icon-shopfront.svg): green like
// the rest of Town Basket, but flat and architectural, so it cannot be
// confused with the storefront's illustrated basket or the rider app's blue
// pin. All three can end up on one device — see public/icons/README.md.
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Town Basket — Store Admin',
    // Short enough not to be truncated under a home-screen icon.
    short_name: 'TB Admin',
    description:
      'Order queue, catalogue, inventory and store configuration for Town Basket staff.',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    // No orientation lock, unlike the phone-shaped storefront and rider apps:
    // this one is used on a landscape laptop as often as a portrait tablet.
    background_color: '#f4f7f5',
    theme_color: '#2e7d32',
    categories: ['business', 'productivity'],
    icons: [
      {
        src: '/icons/icon-192.png',
        sizes: '192x192',
        type: 'image/png',
        purpose: 'any',
      },
      {
        src: '/icons/icon-512.png',
        sizes: '512x512',
        type: 'image/png',
        purpose: 'any',
      },
      {
        src: '/icons/icon-maskable-192.png',
        sizes: '192x192',
        type: 'image/png',
        purpose: 'maskable',
      },
      {
        src: '/icons/icon-maskable-512.png',
        sizes: '512x512',
        type: 'image/png',
        purpose: 'maskable',
      },
    ],
  };
}

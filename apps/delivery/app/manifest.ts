import type { MetadataRoute } from 'next';

// Web app manifest (served by Next at /manifest.webmanifest). Makes the rider
// app installable on a delivery agent's phone — which is also what lets iOS
// deliver push at all (Safari only allows it for an installed PWA).
//
// Blue, not the storefront's green: the two apps sit side by side on a rider's
// home screen and must never be mistaken for one another.
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Town Basket Delivery',
    short_name: 'TB Delivery',
    description:
      'Delivery agent app for Town Basket — your assigned orders and handover confirmation.',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    orientation: 'portrait',
    background_color: '#ffffff',
    theme_color: '#1a56db',
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

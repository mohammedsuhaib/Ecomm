import type { MetadataRoute } from 'next';

// Web app manifest (served by Next at /manifest.webmanifest). Drives the
// installable PWA: name, brand colours, standalone display, icons.
// NOTE: the icon files referenced below are placeholders — see
// public/icons/README and generate real branded PNGs before launch.
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Town Basket',
    short_name: 'Town Basket',
    description:
      'Fresh groceries from your neighbourhood supermarket, delivered within 5 km.',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    orientation: 'portrait',
    background_color: '#ffffff',
    theme_color: '#2e7d32',
    categories: ['shopping', 'food'],
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
        src: '/icons/icon-maskable-512.png',
        sizes: '512x512',
        type: 'image/png',
        purpose: 'maskable',
      },
    ],
  };
}

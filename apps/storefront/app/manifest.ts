import type { MetadataRoute } from 'next';

// Web app manifest (served by Next at /manifest.webmanifest). Drives the
// installable PWA: name, brand colours, standalone display, icons.
// Icons are the branded basket mark — see public/icons/README.md and brand/.
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
    // Matches the off-white ground of the branded icons so the install
    // splash screen blends with the icon tile.
    background_color: '#fdfcf3',
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

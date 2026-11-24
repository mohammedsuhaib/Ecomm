import type { MetadataRoute } from 'next';
import { brand } from '@/app/lib/brand';

// Per-request, never frozen at build: which shop this is comes from the BRAND
// the running container was given (app/lib/brand.ts), not the build machine's.
export const dynamic = 'force-dynamic';

// Web app manifest (served by Next at /manifest.webmanifest). Drives the
// installable PWA: name, brand colours, standalone display, icons.
// Icons are the branded basket mark — see public/icons/README.md and brand/.
export default function manifest(): MetadataRoute.Manifest {
  const b = brand();
  return {
    name: b.name,
    short_name: b.name,
    description: b.description,
    start_url: '/',
    scope: '/',
    display: 'standalone',
    orientation: 'portrait',
    // Matches the off-white ground of the branded icons so the install
    // splash screen blends with the icon tile.
    background_color: b.colors.splashBackground,
    theme_color: b.colors.primary,
    categories: ['shopping', 'food'],
    icons: [
      {
        src: b.assets.icon192,
        sizes: '192x192',
        type: 'image/png',
        purpose: 'any',
      },
      {
        src: b.assets.icon512,
        sizes: '512x512',
        type: 'image/png',
        purpose: 'any',
      },
      {
        src: b.assets.iconMaskable512,
        sizes: '512x512',
        type: 'image/png',
        purpose: 'maskable',
      },
    ],
  };
}

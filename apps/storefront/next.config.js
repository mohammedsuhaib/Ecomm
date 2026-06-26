const withSerwistInit = require('@serwist/next').default;
const createNextIntlPlugin = require('next-intl/plugin');

// next-intl: resolves the request locale + messages from i18n/request.ts.
// Cookie-based (no URL routing) — see i18n/config.ts.
const withNextIntl = createNextIntlPlugin('./i18n/request.ts');

// Serwist (Workbox) service worker: compiles app/sw.ts -> public/sw.js and
// registers it. Disabled in dev to avoid caching surprises while iterating.
const withSerwist = withSerwistInit({
  swSrc: 'app/sw.ts',
  swDest: 'public/sw.js',
  cacheOnNavigation: true,
  reloadOnOnline: true,
  disable: process.env.NODE_ENV === 'development',
});

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Standalone output keeps the production Docker image small.
  output: 'standalone',
  // The API base URL is injected per-environment (local compose / prod Caddy).
  env: {
    NEXT_PUBLIC_API_BASE_URL:
      process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1',
  },
  // Product/category images are served from DO Spaces (and may be remote in
  // dev). We use plain <img> for now, so this is informational; switch to
  // next/image + configure remotePatterns when optimising images.
};

module.exports = withNextIntl(withSerwist(nextConfig));

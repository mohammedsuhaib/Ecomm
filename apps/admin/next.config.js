const withSerwistInit = require('@serwist/next').default;

// Serwist (Workbox) service worker: compiles app/sw.ts -> public/sw.js and
// registers it. Disabled in dev to avoid caching surprises while iterating.
//
// `cacheOnNavigation` is deliberately OFF: app/sw.ts defines its own
// navigation rule so the shell's caching sits next to the comment explaining
// why caching the document is safe here (the HTML carries no live data) while
// caching an API response never is.
const withSerwist = withSerwistInit({
  swSrc: 'app/sw.ts',
  swDest: 'public/sw.js',
  reloadOnOnline: true,
  disable: process.env.NODE_ENV === 'development',
});

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: 'standalone',
  env: {
    NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1',
  },
};

module.exports = withSerwist(nextConfig);

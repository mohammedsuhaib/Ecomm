/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Standalone output keeps the production Docker image small.
  output: 'standalone',
  // The API base URL is injected per-environment (local compose / prod Caddy).
  env: {
    NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1',
  },
};

// NOTE: PWA service worker (Serwist/Workbox) is wired in M2 when the app shell
// and offline strategy land. M1 ships a valid manifest placeholder only.
module.exports = nextConfig;

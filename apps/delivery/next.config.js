/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  // The delivery app talks to the same API as admin; resolve internally in Docker.
  env: {
    NEXT_PUBLIC_API_BASE_URL:
      process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1',
  },
};

module.exports = nextConfig;

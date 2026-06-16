// Public entry point for the Town Basket API client.
//
// In CI this package is regenerated from the backend's OpenAPI spec:
//   1. springdoc-openapi emits apps/api/build/openapi/openapi.json
//   2. `pnpm --filter @town-basket/api-client generate` writes
//      src/generated/schema.ts (openapi-typescript)
//   3. a thin typed-fetch wrapper is exported from here
//
// Until M2 wires the first controllers there is no generated schema yet, so
// this placeholder keeps the package importable and type-checkable.

export const API_CLIENT_PLACEHOLDER = true;

// After generation, this file will re-export the typed client, e.g.:
//   export type { paths, components } from './generated/schema';
//   export { createClient } from './client';

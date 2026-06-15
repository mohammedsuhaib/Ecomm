# @town-basket/api-client

Typed TypeScript client for the Town Basket REST API, consumed by both
frontends (`apps/storefront`, `apps/admin`). It is the mechanical bridge that
keeps the Java backend and the TypeScript frontends in sync (ARCHITECTURE.md
§6).

## This package is generated — do not hand-edit generated sources

The API contract has a single source of truth: the Spring controllers in
`apps/api`. The client is produced from that, not maintained by hand.

Pipeline (run in CI, see `.github/workflows/ci.yml`):

1. **springdoc-openapi** generates `openapi.json` from the running/compiled
   Spring controllers → `apps/api/build/openapi/openapi.json`.
2. **openapi-typescript** turns that spec into typed definitions
   → `packages/api-client/src/generated/schema.ts`.
3. A thin typed-`fetch` wrapper is exported from `src/index.ts`.
4. CI then type-checks/builds the frontends against the freshly generated
   client. **If the contract drifted, the frontends fail to compile — drift is
   caught at build time, not in production.**

The public API is versioned under `/api/v1`; breaking changes require a new
version.

## Local regeneration

```bash
# from repo root, after the backend has produced its OpenAPI spec
pnpm --filter @town-basket/api-client generate
```

`src/generated/` is git-ignored; it is a build artifact.

## Status (M1)

Placeholder only. No controllers exist yet (they arrive from M2 onward), so
there is no spec to generate from. `src/index.ts` exports a placeholder so the
package stays importable and type-checkable. The generation wiring above is the
intended shape.

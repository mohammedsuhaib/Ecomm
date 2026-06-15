# Build context is the repo root (see infra/docker-compose.yml) so the pnpm
# workspace files are available.
FROM node:20-alpine AS base
RUN corepack enable
WORKDIR /repo

# ───── deps + build ─────
FROM base AS build
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml* ./
COPY apps/storefront/package.json apps/storefront/package.json
COPY packages ./packages
RUN pnpm install --filter @town-basket/storefront... --frozen-lockfile || \
    pnpm install --filter @town-basket/storefront...
COPY apps/storefront apps/storefront
RUN pnpm --filter @town-basket/storefront build

# ───── runtime (Next.js standalone) ─────
FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /repo/apps/storefront/.next/standalone ./
COPY --from=build /repo/apps/storefront/.next/static ./apps/storefront/.next/static
COPY --from=build /repo/apps/storefront/public ./apps/storefront/public
EXPOSE 3000
CMD ["node", "apps/storefront/server.js"]

# Build context is the repo root (see infra/docker-compose.yml).
FROM node:20-alpine AS base
RUN corepack enable
WORKDIR /repo

# ───── deps + build ─────
FROM base AS build
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml* ./
COPY apps/delivery/package.json apps/delivery/package.json
COPY packages ./packages
RUN pnpm install --filter @town-basket/delivery... --frozen-lockfile || \
    pnpm install --filter @town-basket/delivery...
COPY apps/delivery apps/delivery
# Browser-facing API base URL — inlined into the client bundle at BUILD time
# (a runtime env only affects SSR). Override per environment via --build-arg.
ARG NEXT_PUBLIC_API_BASE_URL="http://localhost:8080/api/v1"
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
RUN pnpm --filter @town-basket/delivery build

# ───── runtime (Next.js standalone) ─────
FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3002
ENV HOSTNAME=0.0.0.0
COPY --from=build /repo/apps/delivery/.next/standalone ./
COPY --from=build /repo/apps/delivery/.next/static ./apps/delivery/.next/static
EXPOSE 3002
CMD ["node", "apps/delivery/server.js"]

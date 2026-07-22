# Build context is the repo root (see infra/docker-compose.yml).
FROM node:20-alpine AS base
RUN corepack enable
WORKDIR /repo

# ───── deps + build ─────
FROM base AS build
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml* ./
COPY apps/admin/package.json apps/admin/package.json
COPY packages ./packages
RUN pnpm install --filter @town-basket/admin... --frozen-lockfile || \
    pnpm install --filter @town-basket/admin...
COPY apps/admin apps/admin
# Browser-facing API base URL — inlined into the client bundle at BUILD time
# (a runtime env only affects SSR). Override per environment via --build-arg.
ARG NEXT_PUBLIC_API_BASE_URL="http://localhost:8080/api/v1"
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
RUN pnpm --filter @town-basket/admin build

# ───── runtime (Next.js standalone) ─────
FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
# Next.js standalone server.js listens on $PORT (default 3000); the admin app
# is published on 3001, so bind it there.
ENV PORT=3001
ENV HOSTNAME=0.0.0.0
COPY --from=build /repo/apps/admin/.next/standalone ./
COPY --from=build /repo/apps/admin/.next/static ./apps/admin/.next/static
EXPOSE 3001
CMD ["node", "apps/admin/server.js"]

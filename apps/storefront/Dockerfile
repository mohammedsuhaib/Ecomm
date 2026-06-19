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

# Firebase Web config (phone-OTP login). Next inlines NEXT_PUBLIC_* at BUILD
# time, so these must be present during `next build` to enable the real flow in
# the resulting image. All six are PUBLIC values (safe to bake into the client
# bundle). Pass them with `--build-arg NEXT_PUBLIC_FIREBASE_*=...`. They default
# to empty → the build still succeeds and the image falls back to the dev/fake
# verifier (`dev:<phone>`) — see app/lib/firebase.ts `isFirebaseConfigured()`.
ARG NEXT_PUBLIC_FIREBASE_API_KEY=""
ARG NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=""
ARG NEXT_PUBLIC_FIREBASE_PROJECT_ID=""
ARG NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=""
ARG NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=""
ARG NEXT_PUBLIC_FIREBASE_APP_ID=""
ENV NEXT_PUBLIC_FIREBASE_API_KEY=$NEXT_PUBLIC_FIREBASE_API_KEY \
    NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=$NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN \
    NEXT_PUBLIC_FIREBASE_PROJECT_ID=$NEXT_PUBLIC_FIREBASE_PROJECT_ID \
    NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=$NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET \
    NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=$NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID \
    NEXT_PUBLIC_FIREBASE_APP_ID=$NEXT_PUBLIC_FIREBASE_APP_ID

# Google Maps JS key for the checkout/address LocationPicker (F7). PUBLIC value
# (a browser key restricted to the storefront's domains). Next inlines
# NEXT_PUBLIC_* at BUILD time, so it must be present during `next build` to
# render the map. Defaults to empty → the build still succeeds and the picker
# falls back to the geolocation-only flow (no map). Pass it with
# `--build-arg NEXT_PUBLIC_GOOGLE_MAPS_API_KEY=...`.
ARG NEXT_PUBLIC_GOOGLE_MAPS_API_KEY=""
ENV NEXT_PUBLIC_GOOGLE_MAPS_API_KEY=$NEXT_PUBLIC_GOOGLE_MAPS_API_KEY

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

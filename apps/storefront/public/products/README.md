# Product images (local/dev)

Static product images served from the storefront's own origin during
local/dev. A product's `catalog.products.image_url` of `/products/<file>`
resolves to a file in this directory (Next.js serves `public/` at the root).

In **production**, product images live in **DO Spaces** (see `ARCHITECTURE.md`
§7b and `next.config.js`); set `image_url` to the Spaces/CDN URL instead and
these local copies are no longer used.

## Expected files

| `image_url` in seed        | File to place here  |
| -------------------------- | ------------------- |
| `/products/amul-dahi.jpg`  | `amul-dahi.jpg`     |

Drop a square-ish JPG/PNG/WebP here with the matching name. Keep them small
(e.g. ≤ 200 KB, ~500×500) since they ship with the app and aren't optimised
by `next/image` yet.

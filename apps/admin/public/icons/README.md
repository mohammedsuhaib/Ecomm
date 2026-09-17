# App icons

PWA install icons for the store admin dashboard, rendered from
`brand/icon-shopfront.svg` (the brand's "neighbourhood store" tile — a pitched
roof over a basket-weave body). Referenced from `app/manifest.ts`.

- `icon-192.png` / `icon-512.png` — `purpose: any`, the brand tile exactly as
  drawn, rounded corners and transparent outside them.
- `icon-maskable-192.png` / `icon-maskable-512.png` — `purpose: maskable`:
  full-bleed gradient ground with the artwork scaled to 72% about the centre,
  so it survives Android cropping the tile to a circle or squircle (~80% safe
  zone). No rounded corners here on purpose — the OS supplies the shape, and
  baking our own in leaves pale slivers in the corners.

## Why this artwork and not the basket

All three Town Basket apps can end up installed on the same device, so their
icons have to be told apart at a glance on a home screen:

| App | Icon |
| --- | --- |
| Storefront | illustrated basket of groceries on brand off-white |
| Store admin (this one) | flat green tile, white shopfront |
| Delivery | flat blue tile, white map pin |

Green keeps admin in the brand and matches its header; flat-and-architectural
is what separates it from the storefront's illustrated tile.

## Regenerating

There is no raster master — the source is the SVG, so re-render rather than
scaling a PNG up. Any SVG rasteriser works; these were rendered at exact pixel
sizes with headless Chromium. Keep the two `purpose` variants in step: the
`any` files are the brand file unchanged, the maskable ones re-ground it
full-bleed at 72%.

# Brands

One JSON file per client. Which one a deployment serves is set by the runtime
environment variable `BRAND` (the file name without `.json`); it defaults to
`townbasket`. One image serves every client — the choice is made when the
container starts, like `SITE_URL`.

An unknown `BRAND` is an error (HTTP 500 on every page), never a silent fallback
to the default: one client's name and logo on another client's shop is worse
than an error page.

## Adding a client

1. Copy `townbasket.json` to `<id>.json` (lower-case letters and digits) and
   change every value. The fields are documented on `Brand` in
   `app/lib/brand.ts`.
2. Register it in the `BRANDS` map in `app/lib/brand.ts` (one import, one line).
3. Put the client's pictures under `public/` and point `assets.*` at them. Use a
   folder of their own (`public/brands/<id>/...`), not the Town Basket files:
   - `logoMark` — header logo, transparent background
   - `icon192`, `icon512`, `iconMaskable512` — install icons (see
     `public/icons/README.md` for how the maskable one is drawn)
   - `shareImage` — 1200x630, shown when a link to the shop is shared
   - `favicon` (128x128) and `appleTouchIcon` (180x180)
4. Deploy with `BRAND=<id>` and the client's own `SITE_URL`, `API` URLs and keys.

## What the brand covers

Name everywhere it is shown (header, titles, install prompt, messages, push
fallbacks), the three colours (as CSS variables `--green`, `--green-dark`,
`--green-light`), theme colour, install manifest, favicon and touch icon, the
share picture, policy-page links, canonical address and invoice file names.

## What it does not cover (by design or not yet)

- **Store facts** — name shown in the banner, phone, hours, minimum order,
  delivery radius — come from the store record in each client's own database.
- **The translation files still say "within 5 km"** in a few sentences. The
  radius is store data, so those sentences should take it as a value; until then
  a client with a different radius needs those lines edited.
- **Other apps.** The admin and rider apps have their own hard-coded names and
  colours; they have not been moved onto brands yet.
- **Other colours.** Tints and hover shades that are not the three variables
  (the category tile pastels, a few hover greens) are fixed in `globals.css`.
- **`app/global-error.tsx`** is the last-resort screen and renders outside the
  app's providers; its colours are fixed, and it reads the shop's name from the
  page's `application-name` tag.
- **The policy pages themselves** (`holding-site/`) and the Caddy proxy config
  are per-client files and live outside this app.

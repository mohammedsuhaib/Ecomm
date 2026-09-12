# App icons

Branded PNGs derived from the master logo in `brand/logo-full.png` (the basket
mark is `brand/logo-mark.png`). Referenced from `app/manifest.ts`; the service
worker (`app/sw.ts`) also uses `icon-192.png` for push notifications.

- `icon-192.png` — 192×192, `purpose: any` (basket mark on brand off-white)
- `icon-512.png` — 512×512, `purpose: any`
- `icon-maskable-512.png` — 512×512, `purpose: maskable`
  (mark scaled to ~62% so it stays inside the ~80% safe zone when Android
  masks the icon to a circle/squircle)

The favicon is `app/icon.png` and the iOS home-screen icon is
`app/apple-icon.png` — both picked up automatically by the Next.js App Router
file conventions. The header logo is `public/images/logo-mark.png`
(transparent background).

To regenerate at other sizes, scale down from `brand/` — never scale up.

# App icons — PLACEHOLDERS

These PNGs are flat brand-colour placeholders generated so the web app manifest
resolves and the PWA is installable during development.

**Before launch, replace with real branded icons:**

- `icon-192.png` — 192×192, `purpose: any`
- `icon-512.png` — 512×512, `purpose: any`
- `icon-maskable-512.png` — 512×512, `purpose: maskable`
  (keep the 🧺 logo inside the ~80% safe zone so Android masking doesn't crop it)

Brand colour is Marigold `#f9a825` with deep-amber `#b45309` (see `app/globals.css`).
A favicon (`app/favicon.ico`) and an `apple-touch-icon` are also worth adding.

Referenced from `app/manifest.ts`.

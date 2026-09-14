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

## Keep them quantised

These are saved as PNGs with a 256-colour adaptive palette and
Floyd–Steinberg dithering, which halves them (`icon-512.png` 147 KB → 69 KB,
`icon-192.png` 31 KB → 14 KB, `icon-maskable-512.png` 89 KB → 42 KB) at
38–42 dB PSNR — indistinguishable side by side, because the artwork is flat
colour with a couple of soft gradients. A truecolour re-export from `brand/`
will quietly double them again, so finish with the quantise step:

```python
from PIL import Image
im = Image.open(path).convert("RGB")   # convert("RGBA") if the source has alpha
im.quantize(colors=256, method=Image.MEDIANCUT,
            dither=Image.FLOYDSTEINBERG).save(path, optimize=True)
```

`app/apple-icon.png` is quantised the same way. The masters in `brand/` are
deliberately left untouched — they are the source to scale down from.

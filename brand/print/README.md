# Town Basket carry bag — print artwork

Size M (330 × 405 mm, 80 mm gussets). Release 3. Send `tb-bag-M-flat.svg` to the
printer; the individual panels are there for editing and for the other sizes.

| File | Trim | Runs |
|---|---|---|
| `tb-bag-M-flat.svg` | 820 × 485 mm | **the artwork to plate** — all panels in print order |
| `tb-bag-M-front.svg` | 330 × 405 mm | CMYK + 1 spot |
| `tb-bag-M-back.svg` | 330 × 405 mm | CMYK + 1 spot |
| `tb-bag-M-gusset-left.svg` | 80 × 405 mm | 1 spot only |
| `tb-bag-M-gusset-right.svg` | 80 × 405 mm | 1 spot only |
| `tb-bag-M-base.svg` | 330 × 80 mm | 1 spot only |

Every file is trim **+ 5 mm bleed** all round. 1 SVG user unit = 1 mm at 100 %.

Handle is a **U-cut**, 140 × 70 mm, centred, cut from the top edge.

> The base strip declares **150 MICRON**. That is a declaration of the film you
> actually run — if you run anything else, change the strip to match before
> plating. Read the thickness note below first.


## Before you output

1. **Delete the `<g id="dieline">` layer.** It is trim, bleed, safe area, fold
   and the U-cut in magenta. None of it prints.
2. **Nothing draws the substrate.** Every filled shape is ink. Reversed-out type
   and the QR plates are *holes* (`fill-rule="evenodd"`) — unprinted film, not
   white ink.
3. **Type is already outlined.** Nothing to activate, nothing to substitute. This
   matters most for the Kannada: a RIP without the font silently breaks the
   conjunct clusters in ಬಾಸ್ಕೆಟ್, and the result looks like a spelling mistake
   rather than a font error.
4. **No strokes anywhere.** Rules, keylines and the resin triangle are filled
   shapes, so nothing changes weight if the file is scaled.

## Inks

`#023723` is the **spot** (TB-GREEN, closest Pantone 627 C). It carries the
address band, all type, the gussets and the base strip. Pull a draw-down on the
actual film and sign it — it shifts warm on natural LDPE.

The logo adds twelve more, all flat, all process:

```
#1C6334  #6AAB38  #488C2E  #8CC252  #FCD91B  #FACD5B
#E67D12  #F09F2F  #E93A17  #189FED  #D9EFF7  #FFFFFF
```

The list is repeated in a comment at the top of each logo file in `../`. There
are no gradients in this artwork, so nothing can band.

## Non-negotiables

- **120 micron is the legal floor**, not the recommendation here. Plastic carry
  bags below 120 µm cannot legally be sold or used in India (Plastic Waste
  Management Rules, from 31 Dec 2022) — anything quoted at 50 or 75 µm is off
  the table however cheap. But a U-cut removes the continuous top edge, so the
  entire load goes through the two straps beside the cut instead of across the
  full width. **Quote M and L at 150 µm.** 120 µm that would have been
  comfortable on a D-cut is marginal on a U-cut at 10 kg.
- **Registration ±1.0 mm assumed.** Nothing butt-registers; the spot ink and the
  logo never touch.
- **Handle is a U-cut**, 140 × 70 mm, centred, cut from the top edge. The bottom
  is a **true constant-radius semicircle** — depth is exactly half the width.
  Do not square it off with small corner radii: that corner is a stress raiser
  and it is where the bag tears.
- **82 mm clear at the top** — the whole handle zone carries no artwork. 25 mm
  top reinforcement lip above that.
- **Gusset type sits 8 mm clear of the pleat fold** or the name creases down its
  own middle on the first shop trip.
- **Logo minimum sizes:** full lockup 60 mm wide, trolley alone 35 mm. Below that
  the skyline windows, the bottle cap and the tomato highlight fill in. For
  anything smaller or single-ink, use `../logo-mark-1c.svg`.
- **QR:** both are error-correction H with a 4-module quiet zone, printed in the
  spot on unprinted film. Test-scan the draw-down in poor light and on a crumpled
  sample — not the PDF.

## Fill these in before plating

- Printer legal name, address and PWM registration number — base strip.
- Store WhatsApp number — right gusset.

Get the printer's PWM registration certificate on file, and have a native Kannada
reader sign off every Kannada string. Neither is recoverable after 50,000 bags.

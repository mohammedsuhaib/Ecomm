# Town Basket carry bag — print artwork

Size M (330 × 405 mm, 80 mm gussets). Release 6. **One ink, one plate, every
panel.** Send `tb-bag-M-flat.svg` to the printer; the individual panels are
there for editing and for deriving the other sizes.

| File | Trim |
|---|---|
| `tb-bag-M-flat.svg` | 820 × 485 mm — **the artwork to plate**, all panels in print order |
| `tb-bag-M-front.svg` | 330 × 405 mm |
| `tb-bag-M-back.svg` | 330 × 405 mm |
| `tb-bag-M-gusset-left.svg` | 80 × 405 mm |
| `tb-bag-M-gusset-right.svg` | 80 × 405 mm |
| `tb-bag-M-base.svg` | 330 × 80 mm |

Every file is trim **+ 5 mm bleed** all round. 1 SVG user unit = 1 mm at 100 %.

Handle is a **U-cut**, 140 × 70 mm, centred, cut from the top edge.

> The base strip declares **150 MICRON**. That is a declaration of the film you
> actually run — if you run anything else, change the strip to match before
> plating. Read the thickness note below first.

## The ink

**`#023723` — TB-GREEN. One colour. Closest Pantone 627 C.**

That is the entire specification. There is no second ink, no process build, no
tint, no gradient and no halftone anywhere in this artwork: every shape is
100 % ink or bare film. Nothing can mis-register, nothing can band, and no
solid can print mottled. Pull a draw-down on the actual film and sign it — it
shifts warm on natural LDPE.

The logo on this bag is the **stencil reduction**, not the colour logo
flattened. Each region of the illustration was decided ink-or-substrate by its
luminance, and a gap opened wherever two inked regions touch so they still read
as separate shapes. The four lightest areas — the sun, the banana, the bottle
glass and the white highlights — drop out to bare film. The wordmark is
untouched; it was already solid.

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

## Non-negotiables

- **120 micron is the legal floor**, not the recommendation here. Plastic carry
  bags below 120 µm cannot legally be sold or used in India (Plastic Waste
  Management Rules, from 31 Dec 2022) — anything quoted at 50 or 75 µm is off
  the table however cheap. But a U-cut removes the continuous top edge, so the
  entire load goes through the two straps beside the cut instead of across the
  full width. **Quote M and L at 150 µm.** 120 µm that would have been
  comfortable on a D-cut is marginal on a U-cut at 10 kg.
- **Handle is a U-cut**, 140 × 70 mm, centred, cut from the top edge. The bottom
  is a **true constant-radius semicircle** — depth is exactly half the width.
  Do not square it off with small corner radii: that corner is a stress raiser
  and it is where the bag tears.
- **82 mm clear at the top** — the whole handle zone carries no artwork. 25 mm
  top reinforcement lip above that.
- **Gusset type sits 8 mm clear of the pleat fold** or the name creases down its
  own middle on the first shop trip.
- **Logo minimum sizes, one-ink versions:** lockup **70 mm** wide, trolley alone
  **40 mm**. Larger than the colour versions, because the separating gaps
  between shapes scale with the artwork and close up below that.
- **Film colour is your choice.** With no yellows or pale blues left in the
  artwork, nothing needs a white ground — natural is fine, so take whichever is
  cheaper.
- **QR:** both are error-correction H with a 4-module quiet zone, printed in the
  ink on unprinted film. Test-scan the draw-down in poor light and on a crumpled
  sample — not the PDF.

## What's on the back

The whole back panel is a kitchen card — sixteen groceries sorted fridge /
not-fridge, plus three storage facts — and nothing else. There is no offer, no
price and no code on this bag, by design: print is permanent and margins are
not. Rotate the card's copy between runs (seasonal produce, monsoon storage,
cooking times) and keep the plate geometry; a household that has two different
cards keeps both bags.

## Fill these in before plating

- Printer legal name, address and PWM registration number — base strip.
- Store WhatsApp number — right gusset.

Get the printer's PWM registration certificate on file, and have a native Kannada
reader sign off every Kannada string. Neither is recoverable after 50,000 bags.

## Also in `../`

`logo-lockup-1c.svg` and `logo-mark-1c.svg` are the one-ink versions used here.
`logo-lockup.svg` and `logo-mark.svg` are the full-colour vectors — they are not
used on this bag, and they are what you want for the shopfront, the app and the
non-woven tote.

# Town Basket carry bag — print artwork

Size M (330 × 405 mm, 80 mm gussets). Release 7. **Certified compostable film,
one ink, one plate, every panel.** Send `tb-bag-M-flat.svg` to the printer; the
individual panels are there for editing and for deriving the other sizes.

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

## The ink

**`#023723` — TB-GREEN. One colour. Closest Pantone 627 C.**

No second ink, no process build, no tint, no gradient, no halftone, nothing
reversed out: every shape is 100 % ink or bare film. Nothing can mis-register,
nothing can band, no solid can print mottled.

**Coverage is 6.6 % of the bag's area** — front 8.0 %, back 5.5 %, gussets 8 %,
base 1.3 %. That is deliberate and it is not only about cost. On a certified
compostable article the ink is part of the article: its mass counts toward the
additive fraction the certificate has to cover. **Ask your certifier what they
allow and keep the answer in writing.** The solid band that used to sit at the
foot of the front panel is gone for this reason, and the logo is the outline
reduction rather than the solid one — about a third of the ink.

Pull a draw-down on the actual film and sign it. Compostable films take ink
differently from LDPE; the green will sit warmer and slightly duller than it
does on polythene, and adhesion is the converter's to prove on their own film.

## Compostable: what changes on the strip

The base strip carries **no resin code** and **no thickness declaration**.

- Compostable bioplastic is **not LDPE**. A recycling triangle on it would send
  the bag into a plastic stream it contaminates.
- Compostable carry bags are **exempt from India's 120-micron rule**, so there
  is no thickness to declare.
- In their place: **IS/ISO 17088** and a **CPCB certificate number**.

That certificate is the converter's, not yours. Get the number **and a copy of
the certificate**, and check it is current, before the plate is cut. Printing a
compostability claim you cannot evidence is worse than a typo.

## Before you output

1. **Delete the `<g id="dieline">` layer.** It is trim, bleed, safe area, fold
   and the U-cut in magenta. None of it prints. The dashed rectangle on the back
   panel is **not** dieline — it is printed, and it is the cut line for the card.
2. **Nothing draws the substrate.** Every filled shape is ink.
3. **Type is already outlined.** Nothing to activate, nothing to substitute. This
   matters most for the Kannada: a RIP without the font silently breaks the
   conjunct clusters in ಬಾಸ್ಕೆಟ್, and the result looks like a spelling mistake
   rather than a font error.
4. **No strokes anywhere.** Rules, dashes and the cut line are filled shapes, so
   nothing changes weight if the file is scaled.

## Non-negotiables

- **Gauge is set by a drop test, not by the quote.** Compostable film is weaker
  than LDPE at the same gauge, and the thickness exemption means a converter can
  legally offer 40 or 50 µm. A U-cut already puts the whole load on the two
  straps beside the cut. **Ask for a loaded drop test at the weight you actually
  pack**, and be ready to go thicker than quoted, or to put a D-cut back on L.
- **Handle is a U-cut**, 140 × 70 mm, centred, cut from the top edge. The bottom
  is a **true constant-radius semicircle** — depth is exactly half the width.
  Do not square it off with small corner radii: that corner is a stress raiser
  and it is where the bag tears.
- **82 mm clear at the top** — the whole handle zone carries no artwork.
- **Gusset type sits 8 mm clear of the pleat fold** or the name creases down its
  own middle on the first shop trip.
- **Logo minimum sizes, outline versions:** lockup **80 mm** wide, trolley alone
  **50 mm**. Below that the linework drops under 0.5 mm and fills in.
- **Shelf life.** Compostable film ages — it loses strength in heat and humidity
  and has a limited usable life from manufacture. **Do not print a year's
  supply.** Order quarterly, store cool and dry, stock-rotate.
- **QR:** both are error-correction H with a 4-module quiet zone, printed in the
  ink on unprinted film. Test-scan the draw-down in poor light and on a crumpled
  sample — not the PDF.

## What's on the back

The whole back panel is a kitchen card — sixteen groceries sorted fridge /
not-fridge, plus three storage facts — drawn **inside a dashed cut line**. A
compostable bag will not last the way polythene does, so the useful part is
made to come off it and stay on the wall. No offer, no price and no code on this
bag, by design: print is permanent and margins are not. Rotate the card's copy
between runs (seasonal produce, monsoon storage, cooking times) and keep the
plate geometry.

## Fill these in before plating

- Printer legal name and address — base strip.
- **CPCB certificate number** — base strip.
- Store WhatsApp number — right gusset.

Have a native Kannada reader sign off every Kannada string. Not recoverable
after the run.

## Also in `../`

`logo-lockup-outline.svg` and `logo-mark-outline.svg` are the low-coverage
versions used here. `logo-lockup-1c.svg` and `logo-mark-1c.svg` are the solid
one-ink versions, for stamps and stationery. `logo-lockup.svg` and
`logo-mark.svg` are the full-colour vectors — for the shopfront, the app and the
non-woven tote.

# Town Basket carry bag — print artwork

For **EcoPact**, size **13 × 16 in (330 × 406 mm)**, certified compostable film.
Release 10. **Vest cut, two sides, one colour, one plate.**

| File | Trim |
|---|---|
| `tb-bag-13x16-vest-front.svg` | 330 × 406 mm — logo, address, statutory footer |
| `tb-bag-13x16-vest-back.svg` | 330 × 406 mm — the brand face, logo at 190 mm |

Both are trim **+ 5 mm bleed** all round. 1 SVG user unit = 1 mm at 100 %.
**Handle: vest / W-cut.** Scoop **210 mm wide × 110 mm deep**, straps **60 mm**
each. The scoop is one elliptical sweep tangent to the inside of each strap —
deliberately **no corner at the shoulder**, because that is where a vest tears.
Nominal dimensions: replace with EcoPact's die drawing.

The straps take the top 110 mm, so the **printable body is 296 mm, not 406**.
Everything on both faces is fitted to that.

This is EcoPact's **2 Side 1 Colour** line: ₹280/kg Natural, 150 pcs/kg,
**₹1.86 a bag**. The whole back face costs ₹67 per 1,000 bags over a one-sided
bag, which is the cheapest thing on this job.

## The ink

**`#023723` — TB-GREEN. One colour. Closest Pantone 627 C.**

No second ink, no tint, no gradient, no halftone, nothing reversed out: every
shape is 100 % ink or bare film. Nothing can mis-register and nothing can band.

**Coverage is 6.7 % of the bag's area** — front 6.7 %, back 6.7 % — and that is
deliberate. On a certified compostable article the ink is part of the article:
its mass counts toward the additive fraction the certificate has to cover.
**Ask what your certifier allows and keep the answer in writing.**

Pull a draw-down on the actual film and sign it. Compostable film takes ink
differently from LDPE; the green will sit warmer and duller, and adhesion is
EcoPact's to prove on their own film.

## Before you output

1. **Delete the `<g id="dieline">` layer.** Trim, bleed, safe area, the shoulder
   line and the vest scoop, in magenta. None of it prints; everything outside
   that layer does.
2. **Nothing draws the substrate.** Every filled shape is ink.
3. **Type is already outlined.** A RIP without Noto Sans Kannada silently breaks
   the conjunct clusters in ಬಾಸ್ಕೆಟ್, and it reads as a spelling mistake rather
   than a font error.
4. **No strokes anywhere.** Rules and dashes are filled shapes, so nothing
   changes weight if the file is scaled.

## Four things to settle with EcoPact first

1. **Does the bag have side gussets?** Their sizes are width × height only and
   they price by side, not panel — so everything is on two faces and the
   statutory block sits on the foot of the front. If it is gusseted and the
   gussets print, the earlier five-panel set is in the repository history.
2. **Die dimensions.** This is drawn as a vest — their shape — but to nominal
   numbers. Send the real die drawing and it gets matched exactly. Ask
   specifically: **does the quoted 16 in height include the straps, or is it
   body only?** The whole layout hangs off that one answer — if the 16 in is
   body-only, the bag is 110 mm taller than drawn and everything can breathe.
3. **Micron and load rating per grade, plus a loaded drop test** at the weight
   you actually pack. Their sheet gives grams, not microns and not strength:
   150 pcs/kg is 6.7 g a bag, 90 pcs/kg is 11.1 g — roughly 25 and 41 g/m² over
   two faces. Both are thin for 8–10 kg through two vest straps. **This is the question
   that decides whether the bag works.**
4. **Do they apply their own compliance strip?** Their sample bags carry a small
   ECOPACT mark at the foot. If the CPCB marking comes from them, delete ours —
   two compliance blocks on one bag is worse than one.

Also get the **CPCB certificate number and a copy of the certificate**. "Govt.
Approved" on a price sheet is not a certificate number, and the number is what
goes on the bag.

## Non-negotiables

- **No corner at the shoulder.** The scoop meets each strap tangentially. A
  squared or small-radius shoulder is a stress raiser exactly where a vest bag
  fails under load.
- **122 mm clear at the top** — 110 mm of handle plus 12. The straps carry no
  artwork: they fold, they crease, and they are the part under load.
- **Logo minimum sizes, outline versions:** lockup **80 mm** wide, trolley alone
  **50 mm**. Below that the linework drops under 0.5 mm and fills in.
- **Shelf life.** Compostable film ages — it loses strength in heat and humidity.
  **Do not print a year's supply.** Order quarterly, store cool and dry.
- **QR:** both error-correction H, 4-module quiet zone, 1.02 mm modules, printed
  in the ink on unprinted film. Test-scan the draw-down in poor light and on a
  crumpled sample — not the PDF.

## What's on each face

Both faces carry the logo. A vest bag shows whichever side it was picked up by,
so the identity goes on both and the small print on one.

**Front** — outline lockup at 172 mm, the Kannada lockup, `town-basket.com`
with the QR, and the statutory footer at the bottom (4 mm type, already at the
floor — nothing more can be added to it).

**Back** — the brand face. Lockup at 190 mm, centred, with the Kannada lockup
and the address beneath and nothing else. Larger than the front because it has
no statutory footer to carry. No QR: a second one would pull a centred
composition off its axis for a convenience the front already provides, and the
typed address works from either side.

No kitchen card on this bag any more. That is the right call for compostable
film — a bag headed for the wet waste in a week or two is not where you put
something meant to be pinned to a wall. If the card is worth having, it belongs
on the non-woven tote, which lasts a year.

## Fill in before plating

- EcoPact's legal name and address — front footer.
- **CPCB certificate number** — front footer.
- Store WhatsApp number — not on the bag at present; say if you want it back.

Have a native Kannada reader sign off every Kannada string.

## Also in `../`

`logo-lockup-outline.svg`, `logo-mark-outline.svg` — the low-coverage versions
used here. `logo-lockup-1c.svg`, `logo-mark-1c.svg` — solid one-ink, for stamps
and stationery. `logo-lockup.svg`, `logo-mark.svg` — full colour, for the
shopfront, the app and a non-woven tote.

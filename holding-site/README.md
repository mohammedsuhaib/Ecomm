# Town Basket — Holding Site

A small, temporary **static holding website** for Town Basket
(grocery quick-commerce, [town-basket.com](https://town-basket.com)).

## Purpose

This site exists to:

1. Show a clean **"Coming Soon"** landing page.
2. Host the **legal / policy pages** that the payment provider
   (**Paytm Payment Gateway**) requires to be live before it will approve the
   merchant's KYC.

This unblocks payment onboarding. It will be **replaced by the real Next.js
storefront** once that is ready.

## Contents

| File           | Page                                |
| -------------- | ----------------------------------- |
| `index.html`   | Coming Soon landing page            |
| `terms.html`   | Terms & Conditions                  |
| `privacy.html` | Privacy Policy (DPDP Act, 2023)     |
| `refund.html`  | Refund & Cancellation Policy        |
| `shipping.html`| Shipping & Delivery Policy          |
| `contact.html` | Contact Us                          |
| `styles.css`   | Shared, mobile-first stylesheet     |

Pure static HTML + CSS. No framework, no build step, no JavaScript dependencies.

## Serve locally

From inside this folder:

```bash
python3 -m http.server 8000
```

Then open <http://localhost:8000> in your browser.

Any static file server works (e.g. `npx serve`). In production the folder sits
behind **Caddy** on a DigitalOcean droplet.

## Before going live — fill in the placeholders

The policy and contact pages contain clearly marked placeholders in
`[square brackets]` that the client must replace before launch, including:

- `[Legal business name]`, `[GSTIN, if applicable]`
- `[Store / registered address]`, `[City, State]` (jurisdiction)
- `[Support email]`, `[Support phone]`, `[Support hours]`
- `[Grievance Officer name]`, `[Grievance email]`
- `[Delivery hours]`, `[Delivery promise]`, delivery charges / free-delivery threshold
- `[Cancellation window]`, `[Reporting window]`, `[Refund initiation window]`, `[Refund timeline]`
- `Last updated: [date]` on every policy page

Search the files for `[` to find them all, or look for the highlighted
`placeholder` styling in the rendered pages.

## Disclaimer

These policy pages are **reasonable templates only**. The client should have
them **reviewed by a qualified legal/compliance professional** for their
specific business before relying on them or submitting them for KYC.

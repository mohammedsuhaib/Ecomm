# Town Basket — QA Functional Test Cases

Black-box test cases for manual QA. Every case is executed through the apps
(storefront, admin, delivery) or a REST client — no code or unit tests here.

**Coverage:** every module of the platform — identity, catalogue, cart,
serviceability, checkout, orders, payments, tax/GST, inventory, riders,
notifications, analytics, plus i18n, PWA and security.

---

## 1. Before you start

### 1.1 Environments

| Environment | Storefront | Admin | Delivery | Notes |
|---|---|---|---|---|
| Local | http://localhost:3000 | :3001 | :3002 | `docker compose -f infra/docker-compose.yml up --build` |
| QA | https://qa.town-basket.com | qa-admin. | qa-delivery. | No HTTP gate; reachable only from the tailnet — see `infra/qa/README.md` |

QA and local both run the **dev conveniences**: fake phone OTP, fake UPI
gateway, seeded accounts. That is deliberate — it is what makes end-to-end
testing possible without real money or SMS.

### 1.2 Test accounts

| Role | Credentials | Where |
|---|---|---|
| Customer | any 10-digit phone, OTP token `dev:<phone>` (offline verifier — the default) | Storefront |
| Admin | `admin@townbasket.local` / `Admin@12345` | Admin |
| Store staff | `staff@townbasket.local` / `Staff@12345` | Admin |
| Delivery rider | `delivery@townbasket.local` / `Delivery@12345` | Delivery |

> These are seeded dev credentials. If they work in production, that is a
> **P1 defect** — see TC-SEC-011.

### 1.3 Store rules in force (seeded)

| Rule | Value | Where it is enforced |
|---|---|---|
| Minimum order value | ₹299 | Checkout |
| Delivery radius | 5 km from store | Address / checkout |
| Store hours | 08:00–21:00 IST | Checkout |
| Customer self-cancel window | 1 minute from placing | Order page |
| GST slabs allowed | 0%, 5%, 18%, 40% | Product form, CSV import |
| Auth rate limit | 10 requests / 60 s per IP | Login + OTP endpoints |
| Low stock threshold | 5 (per variant, editable) | Inventory, analytics |

### 1.4 Conventions

- **Priority** — P1 blocks release, P2 important, P3 cosmetic/nice-to-have.
- **Type** — Positive (happy path), Negative (rejection expected), Edge.
- A case marked **[auto]** also has automated coverage in the API test suite;
  it is still worth executing once manually per release.
- "Fresh cart" means a browser with cleared site data (a cart cannot be
  ordered twice — see TC-CHK-012).

---

## 2. Identity & authentication (`identity`)

### 2.1 Customer phone-OTP login — storefront

> **Two verifier modes.** TC-AUTH-001..008 below assume the OFFLINE verifier
> (`dev:<phone>` tokens), which is the default in local dev and QA. A deployment
> with a Firebase projectId set runs the REAL SMS path instead — see
> TC-AUTH-001r..003r, and note that `dev:` tokens must then be rejected.

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-AUTH-001 | Positive | First-time login | Account → Login → enter `9876500001` → request OTP → submit token `dev:9876500001` | Logged in; name/phone shown on Account; header shows account indicator | P1 |
| TC-AUTH-002 | Negative | Wrong OTP token | Request OTP for `9876500001`, submit `dev:9999999999` | Rejected with a clear message; not logged in | P1 |
| TC-AUTH-003 | Negative | Malformed phone | Enter `98765` (5 digits) | Client blocks submit or server rejects; no OTP sent | P2 |
| TC-AUTH-004 | Positive | Session survives reload | Log in, hard-refresh the page | Still logged in | P1 |
| TC-AUTH-005 | Positive | Logout | Account → Logout | Logged out; protected pages (Account) return to login | P1 |
| TC-AUTH-006 | Edge | Token refresh | Log in, leave idle >15 min, then browse/act | Access token silently refreshes; no forced re-login (refresh valid 30 days) | P2 |
| TC-AUTH-007 | Negative | Rate limit | Request OTP 12 times in under a minute from one browser | After ~10, further attempts are rejected until the window resets | P2 |
| TC-AUTH-008 | Positive | Guest cart merges on login | Add 2 items as guest → log in | Cart still holds both items; cart id may change but nothing is lost | P1 |
| TC-AUTH-009 | Positive | Resend code | Request an OTP, wait for the countdown to finish, tap **Resend code** | Button reads "Resend code in 30s" and is disabled after each send; once at 0 it re-sends, shows "A new code has been sent.", clears the code field and restarts the countdown. In real-OTP mode a second SMS arrives and the NEW code works | P1 |
| TC-AUTH-009a | Negative | No rapid re-sends | Tap Resend repeatedly during the countdown | Nothing is sent; the button stays disabled until the countdown ends (protects SMS quota and the 10/60s auth rate limit) | P2 |
| TC-AUTH-009b | Edge | Resend after changing number | Request a code, tap Change number, enter a different number, send | The code is sent to the NEW number; verifying uses the new session — the old code is rejected | P2 |

**Real-OTP mode only** (run these after switching QA to Firebase phone auth per
`infra/qa/README.md` — use a Firebase *test* phone number so no SMS is billed):

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-AUTH-001r | Positive | Real OTP login | Log in with a Firebase test number and its fixed code | Logged in; same post-login state as TC-AUTH-001 | P1 |
| TC-AUTH-002r | Negative | Dev token is dead | Submit `dev:9876500001` against the real-mode deployment | Rejected; a hand-typed token can never stand in for an SMS | P1 |
| TC-AUTH-003r | Negative | Wrong code | Request the OTP, then submit a wrong 6-digit code | Rejected with a clear message; not logged in | P1 |
| TC-AUTH-004r | Edge | Half-switched deployment | Set the API projectId but rebuild the storefront WITHOUT the Firebase build args (or the reverse) | Login fails; this is config drift, not a defect — both halves must move together | P2 |
| TC-AUTH-006r | Positive | One account across both modes | Log in as `9632500797` under the offline verifier, switch the deployment to real OTP, log in again with the same number | Same account and same order history — NOT a second customer; Account shows `+91 9632500797`, not a doubled prefix | P1 |
| TC-AUTH-007r | Positive | Checkout after a real-OTP login | Log in via real OTP, go to checkout | Phone prefills as 10 digits and Place Order is enabled — an E.164 prefill would silently fail the 10-digit check | P1 |
| TC-AUTH-005r | Edge | Blank projectId | Deploy with the projectId env var present but empty | API refuses to boot with a message naming `townbasket.identity.firebase.project-id` — never a silent fall back to the dev verifier | P1 |

### 2.2 Staff / rider login

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-AUTH-010 | Positive | Admin login | Admin app → `admin@townbasket.local` / `Admin@12345` | Dashboard loads with all five tabs (Orders, Analytics, Inventory, Catalogue, Riders) | P1 |
| TC-AUTH-011 | Positive | Staff login | Log in as `staff@townbasket.local` | Dashboard loads; staff can work the order queue | P1 |
| TC-AUTH-012 | Negative | Wrong password | Admin login with `Admin@wrong` | Single generic "invalid email or password" — must NOT reveal which field was wrong | P1 |
| TC-AUTH-013 | Negative | Customer cannot use admin | Try a customer phone/OTP on the admin login | Rejected — admin is email+password only | P1 |
| TC-AUTH-014 | Positive | Rider login | Delivery app → `delivery@townbasket.local` / `Delivery@12345` | Rider queue loads | P1 |
| TC-AUTH-015 | Negative | Deactivated rider | Admin → Riders → deactivate the rider, then attempt rider login | Login refused; if already logged in, their queue stops working | P1 |
| TC-AUTH-016 | Positive | Change password | Admin → Change password button → current + new password | Success message; log out and log in with the NEW password | P1 |
| TC-AUTH-017 | Negative | Change password, wrong current | Enter a wrong current password | "Current password is incorrect"; password unchanged | P1 |
| TC-AUTH-018 | Edge | Change password closes safely | Open Change password, type a new value, press Escape | Confirms before discarding; focus returns to the trigger button | P3 |
| TC-AUTH-019 | Positive | Admin resets a rider's password | Admin → Riders → **Reset password** on a rider → enter a new password (≥8) | Success message; rider logs in to the delivery app with the NEW password; the OLD password is refused; a delivery-app session opened before the reset is signed out on its next refresh | P1 |
| TC-AUTH-020 | Positive | Admin resets a staff password | Admin (ADMIN role) → Staff tab → Reset password on the store-staff account | Staff logs in with the new password; the Staff tab is NOT visible when logged in as store staff | P1 |
| TC-AUTH-021 | Negative | Store staff cannot reset staff/admin | Log in as `staff@` and call `POST /api/v1/admin/users/{adminId}/password` | 403; admin's password unchanged. Store staff CAN reset a rider (TC-AUTH-019 as staff) | P1 |
| TC-AUTH-022 | Negative | No self-reset, no customer reset, no short password | As admin, try to reset your own account, a customer's account, and a rider with a 5-character password | Each refused with a clear message; nothing changes | P2 |

---

## 3. Catalogue browsing (`catalog`)

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-CAT-001 | Positive | Home loads | Open the storefront | Categories and featured products render; no console errors | P1 |
| TC-CAT-002 | Positive | Category listing | Tap a category | Only that category's products listed, paginated | P1 |
| TC-CAT-003 | Positive | Product detail | Open a product | Name, image, veg/non-veg marker, description and every variant with price shown | P1 |
| TC-CAT-004 | Positive | Discount display | Open a product whose MRP > selling price | Selling price prominent, MRP struck through, discount shown | P2 |
| TC-CAT-005 | Edge | No discount | Open a product where MRP = selling price | No strikethrough, no 0% discount badge | P3 |
| TC-CAT-006 | Positive | Search exact | Search "atta" | Matching products returned, most relevant first | P1 |
| TC-CAT-007 | Positive | Search typo-tolerant | Search "ata" / "hrlicks" | Still returns the intended products (trigram similarity) | P2 |
| TC-CAT-008 | Negative | Search no results | Search "zzzzqq" | Friendly empty state, not an error | P2 |
| TC-CAT-009 | Positive | Sort by price | Apply sort Price ascending, then descending | Order reverses correctly; sorting spans variants | P2 |
| TC-CAT-010 | Positive | Sort by name | Apply sort Name | Alphabetical | P3 |
| TC-CAT-010a | Positive | Sort by discount | Apply sort Discount | Biggest MRP-vs-selling saving first; items with no discount last | P2 |
| TC-CAT-010b | Edge | Sort survives a reload | Apply a sort, copy the URL, open it in a new tab | Same sort still applied (carried in `?sort=`); an invalid value falls back to the default order | P3 |
| TC-CAT-010c | Positive | Sort keeps scroll position | Scroll down to the product grid on home/category/search, change the sort | The list reorders in place — the page does NOT jump back to the top | P2 |
| TC-CAT-014 | Positive | Tile price = what "+" adds | Find a multi-variant product whose CHEAPEST variant is out of stock; note the price on the tile, tap the quick-add "+", open the cart | The cart line's variant and unit price match exactly what the tile showed — never a different pack than the price implied | P1 |
| TC-CAT-014a | Edge | No buyable variant | Product switched on but every variant out of stock | Tile shows a "from" price + Out of stock tag and NO quick-add "+"; detail page still opens | P2 |
| TC-CAT-011 | Positive | Out of stock visible in catalogue | Set a variant's stock to 0 in Admin → Inventory, reload the catalogue | Variant shows out-of-stock **in the listing and on the product page**, not only in the cart | P1 |
| TC-CAT-012 | Negative | Unavailable product hidden | Admin → toggle a product unavailable | It disappears from storefront listing and search | P1 |
| TC-CAT-013 | Edge | Stock cap on stepper | Variant with 3 units in stock; try to add 5 from the grid and the product page | Stepper stops at 3 in both places | P1 |

---

## 4. Cart (`cart`)

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-CART-001 | Positive | Add item | Add a product variant to cart | Cart badge increments; item listed with correct price | P1 |
| TC-CART-002 | Positive | Quantity change | Increase then decrease quantity | Line total and subtotal recompute correctly | P1 |
| TC-CART-003 | Positive | Remove item | Remove a line | Line disappears; subtotal recomputes | P1 |
| TC-CART-004 | Positive | Cart persists | Add items, close and reopen the browser | Cart still populated | P1 |
| TC-CART-005 | Edge | Empty cart | Remove all items | Empty state with a route back to browsing; checkout not reachable | P2 |
| TC-CART-006 | Edge | Stock cap in cart | Cart holds 3 of a variant that has 3 in stock; try to increase | Blocked at 3 with a clear reason | P1 |
| TC-CART-007 | Edge | Item goes unavailable | Add an item, then mark it unavailable in Admin, return to cart | Cart flags the line as unavailable and blocks checkout until removed | P1 |
| TC-CART-008 | Edge | Price changes under the customer | Add an item, change its selling price in Admin, then check out | Checkout is rejected with a "total has changed" message; the customer re-confirms | P1 |
| TC-CART-009 | Positive | Cart follows the account across devices | Log in on device A, add items, log out; log in with the same number on device B (fresh browser) | The same basket appears on device B — carts belong to the account, not the browser | P1 |
| TC-CART-010 | Positive | Logout clears the basket from a shared browser | Log in, add items, log out; without logging in, open the cart on the same browser | Cart shows empty for the next (guest) user; logging back in restores the basket | P1 |

---

## 5. Serviceability & location (`serviceability`)

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-SRV-001 | Positive | In-radius address | Set a delivery location within 5 km of the store | Marked serviceable; checkout allowed | P1 |
| TC-SRV-002 | Negative | Out-of-radius address | Set a location ~10 km away | "Outside the serviceable area" with the distance shown; checkout blocked | P1 |
| TC-SRV-003 | Edge | Just inside boundary | ~4.9 km | Serviceable | P2 |
| TC-SRV-004 | Edge | Just outside boundary | ~5.1 km | Not serviceable | P2 |
| TC-SRV-005 | Positive | Location gate | Open the storefront in a fresh browser | Location prompt appears; after choosing, the header shows the chosen area | P2 |
| TC-SRV-006 | Positive | Pin-drop picker | Use the map picker to move the pin | Coordinates update; serviceability re-checks | P2 |
| TC-SRV-007 | Edge | Maps key absent | Deployment without `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY` | Pin-drop degrades gracefully; manual address entry still works | P2 |
| TC-SRV-008 | Negative | Store closed blocks ordering | With the store closed, try to check out | Place Order is disabled with the closed reason; no order created | P1 |
| TC-SRV-009 | Positive | Closed banner is sitewide | With the store closed, visit home, a category, a product, cart and checkout | Amber "We're closed right now" banner under the header on EVERY page, naming the next opening time | P1 |
| TC-SRV-010 | Positive | Tomorrow vs today wording | Check the banner after closing time, then before opening time | After closing: "opens again tomorrow at 8 AM". Before opening: "opens today at 8 AM" | P2 |
| TC-SRV-011 | Negative | No banner while open | Visit any page during trading hours | No closed banner anywhere | P1 |
| TC-SRV-012 | Edge | Device clock is irrelevant | Set the phone's clock/timezone hours off, then load the storefront | Banner reflects the STORE's real state — the device clock must not change it | P1 |
| TC-SRV-013 | Edge | Banner appears without a reload | Keep a page open across closing time (or close the store in the DB) | Banner appears within ~5 minutes without the customer reloading | P2 |
| TC-SRV-014 | Edge | Browsing still works when closed | With the banner showing, browse and add items to the cart | Browsing and cart edits work; only order placement is blocked | P2 |
| TC-SRV-015 | Edge | Closure mid-checkout | Begin checkout while open, close the store, then submit | Rejected with the specific closed message — not the generic "couldn't place this order" | P1 |
| TC-SRV-016 | Edge | API unreachable | Block the API, then load a page | No banner shown (a false "closed" would cost orders); page still renders | P2 |
| TC-SRV-017 | Edge | Frontend newer than the API | Run the storefront against an API build that predates the open/closed fields | No banner at all — a missing `open` must read as unknown, never as closed (it would otherwise show all day and always say "opens today") | P1 |
| TC-SRV-018 | Positive | Store settings editable in Admin | Admin → Store → change closing time to 5 minutes from now → Save | Saved without SQL; storefront home shows the new hours within ~60 s; at the new closing time the closed banner appears and checkout blocks | P1 |
| TC-SRV-019 | Positive | Close for today | Admin → Store → **Close for today…** with reason "Power cut" (during trading hours) | Status card turns amber "Closed for today"; storefront banner reads "We're closed today. Power cut. We open again tomorrow at 8:00 am"; Place Order is disabled; API `GET /store` has `open:false, manuallyClosed:true` | P1 |
| TC-SRV-020 | Positive | Reopen now | With the store closed for today, tap **Reopen now** | Banner disappears within ~5 min (immediately on reload); checkout works; normal hours apply | P1 |
| TC-SRV-021 | Edge | Closure lapses by itself | Close for today, then check the next morning without touching Admin | Store is open at the normal opening time — a forgotten reopen costs at most the intended day | P2 |
| TC-SRV-022 | Negative | Invalid settings rejected | Try radius 100 m, or opening time equal to closing time, or a blank name | Save refused with a message; nothing changes on the storefront | P2 |
| TC-SRV-023 | Negative | Customers cannot touch settings | Call `PUT /api/v1/admin/store` or `POST /api/v1/admin/store/close-today` with a customer token or none | 401/403; store unchanged | P1 |

---

## 6. Checkout (`orders` + `payments`)

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-CHK-001 | Positive | Pay-on-delivery order | Cart ≥ ₹299, in-radius address, store open → choose Pay on Delivery | Order created; status PLACED (awaiting staff confirmation); payment COD_PENDING; confirmation page shows the tracking link | P1 |
| TC-CHK-002 | Positive | UPI order (fake gateway) | Same cart, choose UPI | Order PLACED; payment PAID (fake provider auto-succeeds) — payment alone does not confirm the order | P1 |
| TC-CHK-003 | Negative | Below minimum | Cart of ₹150 → checkout | Rejected naming the ₹299 minimum; no order created | P1 |
| TC-CHK-004 | Negative | Login required | As a guest, try to place an order | Redirected to login — there is no guest checkout | P1 |
| TC-CHK-005 | Negative | Empty cart | Force checkout with an empty cart | "Cannot place an order from an empty cart" | P2 |
| TC-CHK-006 | Negative | Missing name | Leave customer name blank | Blocked with a field-level message | P2 |
| TC-CHK-007 | Negative | Bad phone | Enter `12345` as phone | Rejected — must be 10 digits (server-enforced, not just the UI) | P1 |
| TC-CHK-008 | Negative | Blank address line | Leave the address line empty | Rejected | P2 |
| TC-CHK-009 | Positive | Saved address reuse | Log in with a saved address, check out | Address prefilled; order uses it | P2 |
| TC-CHK-010 | Edge | Stock reserved at checkout | Note a variant's stock, place an order for 2 units, check Admin → Inventory | Available stock dropped by exactly 2 (reserved) | P1 |
| TC-CHK-011 | Edge | Insufficient stock at the moment of ordering | Cart holds 5; reduce stock to 1 in Admin; place the order | Order refused; stock unchanged; nothing half-created | P1 |
| TC-CHK-012 | Edge | Same cart cannot be ordered twice | Place an order, press browser Back, try to submit again | Rejected — "cart has already been ordered, start a new cart" | P1 |
| TC-CHK-013 | Edge | Double-submit / retry | Double-click Place Order, or resubmit on a flaky network | Exactly ONE order is created (idempotency key) — verify in Admin queue | P1 |
| TC-CHK-014 | Edge | Payment declined leaves nothing behind | Force a UPI failure if the fake provider allows it | No order row, no stock reservation held, cart still usable | P1 |

---

## 7. Order tracking, cancellation & invoice (`orders`)

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-ORD-001 | Positive | Track by link | Open the tracking link from the confirmation page | Status, items, totals, address and timeline shown | P1 |
| TC-ORD-002 | Positive | Live status updates | Keep the tracking page open; move the order to PACKING in Admin | Status updates within seconds without a manual refresh (no connection badge is shown — judge it by the status and the "updated HH:MM" time) | P1 |
| TC-ORD-003 | Edge | Live falls back to polling | Block SSE (throttle/offline briefly) then restore | Page keeps updating via polling — the status and the "updated HH:MM" time keep moving; no connection badge is shown either way | P2 |
| TC-ORD-004 | Positive | Delivery code appears at the right time | Watch the page while staff mark the order READY_FOR_DELIVERY, then while the rider taps **Picked up** | OTP is hidden at PLACED/CONFIRMED/PACKING/READY_FOR_DELIVERY and appears ONLY at OUT_FOR_DELIVERY — being packed is not being handed over | P1 |
| TC-ORD-005 | Positive | Order history | Account → Orders | The FIVE most recent orders, newest first, each with its live status stamp, View and Reorder; the address book below stays one short scroll away | P2 |
| TC-ORD-021 | Positive | Show more orders | As a customer with more than 5 orders, open Account | Exactly 5 rows plus a full-width "Show more (N older)" button naming the exact remainder; each tap appends the next 5 (no duplicates, no page jump) and the count falls; when everything is shown the button is replaced by "That's all N orders." (en + kn) | P2 |
| TC-ORD-022 | Edge | Five or fewer orders | As a customer with 1–5 orders, open Account | All of them, no Show more button and no "That's all" line — nothing to expand, nothing to say | P3 |
| TC-ORD-023 | Edge | Show more fails | Go offline after the first 5 load, tap Show more | The five rows stay; an error notice says older orders could not load; tapping again once online works | P3 |
| TC-ORD-024 | Edge | New order between taps | With 7 orders showing 5, place a new order in another tab, then tap Show more | The 2 older orders appear once each — no row duplicated by the shifted paging | P3 |
| TC-ORD-006 | Positive | Reorder / buy again | Account → an old order → Reorder | New cart populated with the still-available lines | P2 |
| TC-ORD-007 | Edge | Reorder skips dead items | Make one line's product unavailable, then reorder | Available lines added, unavailable ones silently skipped | P2 |
| TC-ORD-008 | Positive | Self-cancel inside the window | Place an order and cancel within 1 minute | Order CANCELLED; countdown was visible on the button; reserved stock released (check Inventory) | P1 |
| TC-ORD-009 | Negative | Self-cancel after the window | Wait >1 minute, then try to cancel | Refused, routed to support; order unchanged | P1 |
| TC-ORD-010 | Negative | Self-cancel after packing starts | Move the order to PACKING, then try to cancel | Refused — "already being prepared" | P1 |
| TC-ORD-011 | Edge | Cancel is idempotent | Cancel, then tap cancel again / reload and retry | Still CANCELLED; no error, no double release of stock | P2 |
| TC-ORD-012 | Positive | Invoice PDF | Tracking page → Download invoice | PDF opens: store details, bill-to, itemised lines, totals, payment line | P1 |
| TC-ORD-013 | Security | Tracking token is the only key | Open a tracking link, then alter the token in the URL | No other customer's order is reachable; 404 | P1 |
| TC-ORD-014 | Security | Order ids are not enumerable | Try `/order/1`, `/order/2` | Sequential ids do not expose orders — only the UUID token works | P1 |
| TC-ORD-015 | Positive | Customer sees the failed attempt | Track an order after the rider reports Can't deliver | Headline "We couldn't deliver your order", amber notice with the rider's reason and "the store will call you"; timeline stays lit to Out for delivery; the push/in-app update says the same (en + kn) | P1 |
| TC-ORD-016 | Positive | Live rider location **[auto]** | Rider app is sharing (TC-DLV-022); move the order to OUT_FOR_DELIVERY; open the tracking page | "Your rider is on the way" card under the delivery code: minutes first, distance second ("About 5 minutes away · 1.2 km"; "Almost at your door" under 100 m) and "Updated N s ago" counting up between polls — the minutes are an estimate (crow-flies × 1.3 road factor at 18 km/h, rounded up, never below 1), so a rider 850 m away reads as 4 minutes; with a Maps key, a map with 🛵 and 🏠 pins that re-frames as the rider moves (en + kn) | P1 |
| TC-ORD-017 | Negative | Rider hidden outside Out for delivery **[auto]** | Same order while PACKING and again while READY_FOR_DELIVERY (assigned, not yet collected), then after DELIVERED | No rider card before the pick-up or after handover, even though the rider app is still sharing — a bag on the counter must not show a rider who is not coming yet | P1 |
| TC-ORD-018 | Edge | Stale fix is hidden **[auto]** | Rider locks their phone or closes the app mid-delivery | Within ~3 minutes the card disappears rather than showing a frozen dot; it returns on the next report | P2 |
| TC-ORD-019 | Security | Position never leaves the tracking read **[auto]** | Inspect the admin queue, the rider's queue and the customer's order HISTORY responses while sharing | `riderLocation` is null on every one of them; only `GET /orders/track/{token}` carries it | P1 |
| TC-ORD-025 | Positive | Packed-and-ready step | Keep the tracking page open while staff mark the order **Ready for delivery** | Timeline gains a "Packed and ready" step between "Being packed" and "Out for delivery", lit with its own timestamp; headline and 🛍️ emoji change; no delivery code yet; Kannada equivalent ("ಪ್ಯಾಕ್ ಆಗಿ ಸಿದ್ಧವಾಗಿದೆ") | P1 |
| TC-ORD-026 | Positive | On-the-way arrives from the rider | With the page open, have the rider tap **Picked up** in their app | Within seconds: headline "Out for delivery", the delivery code appears, and the rider card starts (TC-ORD-016) — all three driven by the rider's tap, not by a staff click | P1 |
| TC-ORD-020 | Negative | Cancelled order is not offered in the cart | Place an order, cancel it (self-cancel, or have staff cancel it while the tracking page is closed), then open the cart | Plain "Your cart is empty" — NOT "Your order is placed — track this order"; a brief "Loading…" is acceptable while the status is checked, "empty" must not flash first | P1 |

---

## 8. Admin order queue & fulfilment (`orders` admin)

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-ADM-001 | Positive | New order appears live | Keep Admin → Orders open; place an order from the storefront | Order appears within seconds without a refresh | P1 |
| TC-ADM-002 | Positive | Status filter tabs | Click through the status tabs | Only orders in that status listed; counts make sense | P1 |
| TC-ADM-003 | Positive | Full happy path | New order arrives PLACED → staff press Confirm order → Mark Packing → Mark Ready for delivery → the assigned rider taps **Picked up** → DELIVERED (with OTP) | Each transition accepted; timeline records all six steps; the customer's page shows "Order confirmed!" only after the staff step | P1 |
| TC-ADM-004 | Negative | Illegal transition | Try PLACED → PACKING, PLACED → DELIVERED, CONFIRMED → OUT_FOR_DELIVERY, or PACKING → OUT_FOR_DELIVERY (via API) | Each rejected as an illegal transition — a PLACED order must be confirmed first, and a packed order must be marked ready before it can go out | P1 |
| TC-ADM-005 | Negative | Deliver without the OTP | Try to mark DELIVERED with a blank or wrong OTP | Refused — "Delivery OTP does not match" | P1 |
| TC-ADM-006 | Negative | Terminal states are final | Try to transition a DELIVERED or CANCELLED order | No transitions offered/accepted | P1 |
| TC-ADM-007 | Positive | Staff cancellation | Cancel a CONFIRMED order from Admin | CANCELLED; reserved stock released; customer's page reflects it live | P1 |
| TC-ADM-008 | Positive | Assign a rider | Assign the seeded rider to a PACKING order | Assignment saved and shown on the card | P1 |
| TC-ADM-009 | Negative | Assign an inactive rider | Deactivate the rider, then try to assign them | Refused — "not an active delivery agent" | P1 |
| TC-ADM-010 | Positive | Reassign / unassign | Change the rider, then clear the assignment | Order returns to the unassigned pool | P2 |
| TC-ADM-011 | Negative | Assign on a closed order | Try to assign a rider to a DELIVERED order | Refused | P2 |
| TC-ADM-012 | Positive | COD marked paid on delivery | Complete a COD order through DELIVERED | Payment status flips to PAID at delivery | P1 |
| TC-ADM-013 | Security | Cost price never leaks | Inspect the order payload the admin/storefront receives | No `costPrice` on any order line in either app | P1 |
| TC-ADM-014 | Positive | Failed delivery lands with staff | After TC-DLV-010, open Admin → Orders | Card shows **Delivery failed** (amber) with the rider's reason; a "Delivery failed" status tab lists it; the dashboard alert fires | P1 |
| TC-ADM-015 | Positive | Re-dispatch | On a Delivery-failed card tap **Re-dispatch** | Order returns to OUT_FOR_DELIVERY with the SAME rider and the same customer OTP; it reappears in that rider's queue | P1 |
| TC-ADM-016 | Positive | Cancel after failed attempt | On a Delivery-failed card tap Cancel with a reason | CANCELLED; reserved stock is released ONLY now (check Inventory before/after) | P1 |
| TC-ADM-017 | Negative | Failed order cannot skip to Delivered | Try to mark a Delivery-failed order DELIVERED | Refused — it must go back out for delivery first | P1 |
| TC-ADM-023 | Positive | Mark ready for delivery | On a Packing card tap **Mark Ready for delivery** | Card turns teal with a "Ready for delivery" badge, the "Ready for the rider to collect" note appears, and a "Ready for delivery" status tab lists it; the card stays in the queue (the shop still holds the goods) | P1 |
| TC-ADM-024 | Positive | Ready-but-unassigned says what to do | Mark an order with no rider ready | The note reads "Assign a rider above — they pick it up from their own app"; assigning one changes it to the "they mark it picked up" wording | P2 |
| TC-ADM-025 | Positive | Staff can record the hand-over | On a Ready-for-delivery card tap **Handed to rider** (the rider's phone is flat) | Order moves to OUT_FOR_DELIVERY exactly as the rider's own tap would: customer's code and live map go live; timeline shows the step | P2 |
| TC-ADM-026 | Negative | Delivery still needs the rider and the code | On a Ready-for-delivery card, try to reach DELIVERED without the pick-up | Not offered, and refused via the API — a bag on the counter cannot have been handed over | P2 |
| TC-ADM-018 | Positive | Order search | Admin → Orders → type an order number, then part of a phone, then part of a customer name | Each finds the order(s) server-side; works across pages, not just the visible list; the count line says how many matched | P1 |
| TC-ADM-019 | Positive | Search within a status tab | Type a term, then switch status tabs | Results are the intersection of term and status; clearing the box restores the plain tab list | P2 |
| TC-ADM-020 | Edge | Search wildcards are literal | Search `%` or `_` | Only orders whose name/phone contain that character match — never everything | P2 |
| TC-ADM-021 | Edge | Live refresh keeps the search | Leave a search active; place a new order from the storefront that does not match | The list does not jump to unfiltered results on the live/poll refresh | P2 |
| TC-ADM-022 | Negative | Cannot assign an off-duty rider | Rider taps **Off duty**; in Admin open an order's rider dropdown and try that rider | Shown "(off duty)" and not selectable; a direct `POST …/assign` is refused with an "off duty" message; the Riders roster shows an Off-duty badge | P1 |

---

## 9. Inventory (`inventory`)

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-INV-001 | Positive | Inventory list | Admin → Inventory | Variants listed with on-hand, reserved, available and threshold | P1 |
| TC-INV-002 | Positive | Low stock sorts first | View the list with a mix of stock levels | Low/out-of-stock rows appear at the top, visually flagged | P2 |
| TC-INV-003 | Positive | Stock correction | Correct a variant to a new on-hand count with a reason | Value saved; storefront availability updates | P1 |
| TC-INV-004 | Negative | Correction below reserved | With 2 units reserved by a live order, try to set on-hand to 1 | Refused — cannot drop below reserved | P1 |
| TC-INV-005 | Negative | Negative stock | Try to set on-hand to −5 | Refused — "newOnHand must be >= 0" | P1 |
| TC-INV-006 | Edge | Reservation released on cancel | Note available stock, place an order, cancel it | Available returns to the original number | P1 |
| TC-INV-007 | Edge | Reservation committed on delivery | Complete an order to DELIVERED | On-hand permanently reduced; reserved returns to 0 | P1 |
| TC-INV-008 | Edge | New variant opens at zero | Create a product with a variant in Admin → Catalogue | It appears in Inventory with 0 on-hand (out of stock, not missing) | P2 |
| TC-INV-009 | Positive | Low-stock threshold edit | Change a threshold, then reduce stock just below it | Row flags as low; appears in the Analytics low-stock panel | P2 |
| TC-INV-010 | Positive | Search spans the whole store | With >100 variants, search for a product you know is NOT on page 1 | It is found — search is not limited to the page you are viewing | P1 |
| TC-INV-011 | Positive | Pager describes the search results | Search a term with few matches | Header count and "Page x of y" describe the MATCHES, not the full variant list | P1 |
| TC-INV-012 | Positive | Search resets to page 1 | Go to page 3, then type a search | Jumps back to page 1 of the results rather than filtering page 3 | P1 |
| TC-INV-013 | Positive | Search by variant label | Search `500 g` | Matches on the variant label, not just the product name | P2 |
| TC-INV-014 | Edge | Wildcards are literal | Search `%` | Treated as a typed character — does NOT match everything | P2 |
| TC-INV-015 | Edge | Paging through matches | Search a term with >2 pages of matches and walk every page | No row appears twice and none is skipped | P2 |
| TC-INV-016 | Edge | Fast typing | Type a term quickly, then delete a few characters | Final list matches the final search box contents (no stale result overwriting it) | P2 |
| TC-INV-017 | Edge | Correction keeps the search | Search, correct a stock count, save | List reloads still filtered by the same term, on the same page | P2 |
| TC-INV-018 | Edge | No matches | Search `zzzzqq` | "No stock matches …" naming the term; pager hidden | P3 |
| TC-INV-019 | Edge | Failed attempt keeps the reservation | Note available stock, take an order to OUT_FOR_DELIVERY, report Can't deliver | Available stock UNCHANGED (goods are still with the rider); it returns only if staff cancel (TC-ADM-016) | P1 |

---

## 10. Catalogue administration (`catalog` admin)

### 10.1 Categories & products

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-ACAT-001 | Positive | Create category | Admin → Catalogue → add a category | Appears in the admin list and on the storefront | P1 |
| TC-ACAT-002 | Positive | Slug auto-generates | Create "Test Snacks & Treats" | Slug becomes `test-snacks-treats` | P2 |
| TC-ACAT-003 | Edge | Duplicate name | Create the same category name twice | Second gets a suffixed slug (`-2`); no crash | P2 |
| TC-ACAT-004 | Negative | Delete a category with products | Try to delete a category that still has products | Refused with a clear reason | P1 |
| TC-ACAT-005 | Negative | Blank name | Save a category with a blank name | Refused | P2 |
| TC-ACAT-006 | Positive | Create product with variants | Add a product with two variants (label, selling, cost, MRP) | Created; both variants visible on the storefront | P1 |
| TC-ACAT-007 | Negative | MRP below selling price | Set MRP ₹40, selling ₹50 | Refused — that would render a negative discount | P1 |
| TC-ACAT-008 | Negative | Negative price | Set selling price −1 | Refused | P1 |
| TC-ACAT-009 | Positive | Edit product | Rename, change category, edit description | Saved; slug stays unchanged (immutable) | P2 |
| TC-ACAT-010 | Positive | Availability toggle | Toggle a product unavailable then available | Disappears from / returns to the storefront | P1 |
| TC-ACAT-011 | Positive | Variant CRUD | Add, edit, then delete a variant | Each change reflected on the storefront | P1 |
| TC-ACAT-012 | Edge | Retry-safe save | Interrupt a product edit mid-save (kill network), then save again | No duplicate variants created and nothing double-deleted | P2 |
| TC-ACAT-013 | Positive | Veg / non-veg marker | Create one veg and one non-veg product | Storefront shows the green dot vs the non-veg triangle correctly | P2 |
| TC-ACAT-014 | Positive | Upload a product photo | Admin → product form → choose a JPEG straight from a phone | Uploads, preview appears, URL fills in; after save the storefront tile shows it; the stored file is a resized JPEG, not the multi-MB original | P1 |
| TC-ACAT-015 | Positive | Photo on a NEW product | Upload a photo while creating a product that does not exist yet | Works — upload is independent of the product id — and the photo is attached when the product saves | P1 |
| TC-ACAT-016 | Security | Disguised file refused | Rename an SVG (or a PDF/zip) to `.jpg` and upload it | Refused naming JPEG/PNG; nothing is stored. The check is on the file's bytes, so renaming never gets past it | P1 |
| TC-ACAT-017 | Negative | Oversized photo | Upload an image larger than 6 MB | Refused with a message naming the size limit, not a container error page | P2 |
| TC-ACAT-018 | Positive | Replacing a photo cleans up | Upload photo A, save, upload photo B, save; check the bucket | Product shows B; A's object is gone — the bucket does not accumulate orphans | P2 |
| TC-ACAT-019 | Positive | Deleting a product deletes its photo | Delete a product that has an uploaded photo; check the bucket | The object is gone with the product | P1 |
| TC-ACAT-020 | Edge | Seeded images are left alone | Delete a seeded product whose image is a bundled `/images/products/…` path | Product deletes cleanly; no attempt to delete anything from the bucket, no error | P1 |
| TC-ACAT-021 | Edge | Upload unconfigured | With no storage configured, open the product form and try to upload | API startup logged "Product image upload disabled"; the attempt is refused with a message pointing at pasting a URL, and pasting one still works | P2 |
| TC-ACAT-022 | Edge | Transparent PNG | Upload a packshot cut out on a transparent background | Transparent areas render white on the storefront, never black | P2 |

### 10.2 GST & HSN

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-ACAT-020 | Positive | Set a GST slab | Set a product to 5% | Saved; appears on the invoice breakdown for new orders | P1 |
| TC-ACAT-021 | Negative | Illegal slab | Attempt 12% (retired in Sept 2025) via API/CSV | Refused, listing the legal slabs (0/5/18/40) | P1 |
| TC-ACAT-022 | Positive | HSN entry | Enter HSN `09023010` | Saved and printed on the invoice | P2 |
| TC-ACAT-023 | Positive | HSN-based rate prefill | Type a known HSN in the product form | Candidate GST rate(s) suggested; still overridable by the user | P2 |
| TC-ACAT-024 | Edge | Blank HSN | Leave HSN empty | Allowed; invoice shows a dash rather than a blank column | P3 |

### 10.3 CSV bulk import

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-CSV-001 | Positive | Valid import | Import a CSV with the required header (`name, category, variant_label, selling_price, cost_price`) | Reports the number created; products appear in the catalogue | P1 |
| TC-CSV-002 | Positive | Multi-variant grouping | Rows sharing one product name with different `variant_label` | ONE product created with several variants | P1 |
| TC-CSV-003 | Positive | Dry run | Import with dry-run enabled | Counts reported, nothing persisted | P2 |
| TC-CSV-004 | Positive | Re-run is safe | Import the same file twice | Second run skips existing products rather than duplicating | P1 |
| TC-CSV-005 | Negative | Missing required column | Drop `cost_price` from the header | Rejected, naming the missing column(s) | P1 |
| TC-CSV-006 | Negative | Unknown category | Use a category that does not exist | That product is reported as an error; other rows still import | P1 |
| TC-CSV-007 | Negative | Non-numeric price | Put `abc` in `selling_price` | Row-level error naming the column | P2 |
| TC-CSV-008 | Negative | Ragged row | A row with fewer columns than the header | Row-level error with the line number | P2 |
| TC-CSV-009 | Negative | Empty file | Upload an empty file | "The uploaded file is empty." | P2 |
| TC-CSV-010 | Edge | Row cap | Import >2000 data rows | Rejected, naming the limit | P2 |
| TC-CSV-011 | Edge | Quoted fields | A product name containing a comma, wrapped in quotes | Imported intact | P2 |
| TC-CSV-012 | Edge | Bad `veg` value | Put `maybe` in the veg column | Rejected — must be Y or N | P3 |
| TC-CSV-013 | Positive | Template link | Open the CSV template link before picking a file | Template is reachable and its header matches what the importer expects | P3 |
| TC-CSV-014 | Edge | Imported items start out of stock | Import, then check the storefront | Products visible but out of stock until stock is loaded | P2 |

---

## 11. Riders administration

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-RID-001 | Positive | Onboard a rider | Admin → Riders → add name, email, password | Rider created and listed as active | P1 |
| TC-RID-002 | Positive | New rider can log in | Log into the delivery app with the new credentials | Queue loads | P1 |
| TC-RID-003 | Negative | Duplicate email | Add a rider with an email already in use | Refused — "That email is already in use" | P1 |
| TC-RID-004 | Negative | Weak / blank password | Submit a blank password | Refused with a field message | P2 |
| TC-RID-005 | Positive | Deactivate | Deactivate a rider | Marked inactive; drops out of the assignment dropdown | P1 |
| TC-RID-006 | Positive | Reactivate | Reactivate them | Assignable and able to log in again | P2 |
| TC-RID-007 | Positive | Date-wise delivery counts | Complete 2 deliveries with one rider, expand their stats | Counts shown per date and match reality; each date row also shows that day's ₹ order value | P2 |
| TC-RID-008 | Edge | Rider with no deliveries | Expand a brand-new rider's stats | Empty state, not an error; Order value column shows "—" | P3 |
| TC-RID-009 | Positive | Order value column | Deliver 2 orders of known totals with one rider, check the Order value column | Shows the ₹ sum of the delivered orders' totals (tax-inclusive), matching the two order totals exactly; per-date breakdown amounts add up to it | P2 |
| TC-RID-010 | Edge | Order value counts each order once | Deliver one order, then re-check stats after any repeated/duplicate confirm attempts | The order's value appears exactly once — deliveries and ₹ never double | P2 |
| TC-RID-011 | Negative | Deactivated rider cannot log in | Deactivate a rider, then sign in to the delivery app with their CORRECT password | Refused with "Your account has been deactivated. Please contact the store manager." — not "Incorrect email or password" | P1 |
| TC-RID-012 | Security | Deactivation reveals nothing to a guesser | With the rider deactivated, sign in with a WRONG password | Plain "Incorrect email or password" (401) — the deactivated message appears only for the right password | P1 |
| TC-RID-013 | Positive | Deactivation ends the live session | Have the rider signed in on a phone, deactivate them in Admin, wait for the access token to expire (≤ 15 min) or trigger a refresh | The app returns to the login screen; signing in again shows the deactivated message | P1 |
| TC-RID-014 | Positive | Reactivation restores login | Reactivate the rider | They can sign in again and the queue loads | P2 |

---

## 12. Delivery (rider) app

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-DLV-001 | Positive | Queue shows assigned work | Assign an OUT_FOR_DELIVERY order to the rider, open the app | Order listed with customer, phone, address, items and payment method | P1 |
| TC-DLV-002 | Negative | Only their own orders | Assign an order to a DIFFERENT rider | It does NOT appear in this rider's queue | P1 |
| TC-DLV-003 | Positive | Confirm delivery with OTP | Get the OTP from the customer's tracking page, enter it | Order DELIVERED; disappears from the queue; customer page updates live | P1 |
| TC-DLV-004 | Negative | Wrong OTP | Enter `000000` | Refused; order stays OUT_FOR_DELIVERY | P1 |
| TC-DLV-005 | Negative | Confirm someone else's order | Attempt to deliver an order assigned to another rider (via API) | Refused (403) | P1 |
| TC-DLV-006 | Positive | OTP input on mobile | Focus the OTP field on a phone | Numeric keypad opens; only digits accepted; capped at 6 | P2 |
| TC-DLV-007 | Positive | Refresh | Tap Refresh | Queue reloads; "updated HH:MM" timestamp changes | P2 |
| TC-DLV-008 | Edge | Offline banner | Turn on airplane mode with the app open | Explicit offline banner; auto-recovers when back online | P1 |
| TC-DLV-009 | Edge | Auto-poll | Leave the app open while an order is assigned | Queue picks it up within ~30 s without interaction | P2 |
| TC-DLV-010 | Positive | Logout | Tap Logout | Returns to login; queue no longer reachable | P2 |
| TC-DLV-011 | Positive | Pinch-zoom allowed | Pinch-zoom the queue on a phone | Zoom works (accessibility requirement) | P3 |
| TC-DLV-010 | Positive | Can't deliver | On an out-for-delivery card tap **Can't deliver**, pick a reason, Report | Order leaves the rider's queue; a "Reported — bring it back" confirmation shows briefly | P1 |
| TC-DLV-011 | Negative | Reason is mandatory | Open Can't deliver and tap Report with no reason selected | Report is disabled; nothing is sent | P2 |
| TC-DLV-012 | Negative | Not my order | Call `POST /delivery/orders/{id}/fail` for an order assigned to another rider | 403; order unchanged | P1 |
| TC-DLV-013 | Positive | Rider goes off duty | Tap **On duty** in the delivery-app header → it flips to Off duty; an amber banner explains | Orders already in the queue stay and can still be delivered/reported; admin cannot assign new ones (TC-ADM-022) | P1 |
| TC-DLV-014 | Positive | Rider goes back on duty | Tap **Off duty** → On duty | Admin can assign again immediately; the toggle survives a reload (read from the server, not the device) | P1 |
| TC-DLV-015 | Negative | Only riders have a duty switch | Call `PUT /api/v1/me/duty` with a customer or admin token | 403; nothing changes | P2 |
| TC-DLV-016 | Positive | Completed tab lists my deliveries | Deliver two orders, tap **Completed** | Both orders listed newest first, each showing "Delivered HH:MM", customer, address, items and total; the Deliveries tab still shows only pending work | P1 |
| TC-DLV-017 | Positive | Today's cash tally | Deliver one COD order of ₹X and one UPI order, open Completed | "Today" shows 2 delivered and ₹X cash collected · 1 order — the UPI order is counted as a delivery but not as cash | P1 |
| TC-DLV-018 | Negative | Only my completed orders | Have ANOTHER rider deliver an order the same day | It appears in neither this rider's Completed list nor their tally | P1 |
| TC-DLV-019 | Edge | Store day, not phone day | Deliver an order after 18:30 IST (already tomorrow in UTC) | It counts in TODAY's tally; at midnight IST the tally resets to zero while the list keeps every order | P2 |
| TC-DLV-020 | Edge | Nothing delivered yet | Open Completed as a brand-new rider | "0 delivered", "₹0 cash collected" and an empty state — no error | P3 |
| TC-DLV-021 | Positive | Older deliveries | With more than 50 completed orders, open Completed | First 50 shown with a "Show more (N older)" button that appends the rest without duplicates | P3 |
| TC-DLV-022 | Positive | Sharing starts when carrying | Sign in as a rider who has an OUT_FOR_DELIVERY order | Browser asks for location once; a green "Sharing your location with customers · Stop" pill appears; the customer's tracking page shows the rider within ~10 s (TC-ORD-016) | P1 |
| TC-DLV-023 | Positive | Stop is remembered | Tap **Stop**, then reload the app | Pill reads "Location off — customers can't see you on the way · Turn on"; no reports are sent; the customer's card disappears within ~3 min; **Turn on** resumes and survives a reload too | P1 |
| TC-DLV-024 | Edge | Nothing carried stops sharing | Deliver (or report) the last order you are carrying | Pill disappears; `DELETE /delivery/location` is sent so nothing lingers; sharing resumes on its own at the next pick-up. A bag still in "Ready to collect" does NOT restart it — nobody is watching a map for an order that has not left | P2 |
| TC-DLV-025 | Negative | Admin cannot report a position **[auto]** | Call `PUT /delivery/location` with an ADMIN token (dispatcher view) | 403 — an admin is never assigned an order, so their laptop must not appear on a customer's map | P2 |
| TC-DLV-026 | Negative | Location permission refused | Block location for the site, reload with an order in the queue | Amber banner explaining how to allow it in browser settings; nothing is sent; the rest of the app works normally | P2 |
| TC-DLV-027 | Edge | Sign-out forgets the fix | Tap Logout while sharing | `DELETE /delivery/location` is sent BEFORE the token is dropped; the customer's card disappears | P2 |
| TC-DLV-028 | Edge | Phone in pocket (platform limit) | On iOS Safari, lock the screen mid-delivery for 5 minutes | Reports pause while locked (a web-app constraint); the customer's card hides after ~3 min and returns once the phone is unlocked — no stale position is ever shown | P3 |
| TC-DLV-029 | Positive | Ready to collect appears first | Have staff mark an assigned order **Ready for delivery**, open the app | A "📦 Ready to collect" section above "🛵 Out for delivery", each with its own count; the header count and "N pending" cover both; the hint explains that tapping Picked up starts the customer's map | P1 |
| TC-DLV-030 | Positive | Picked up moves the card | On a Ready-to-collect card tap **Picked up** | Card moves to Out for delivery immediately (no waiting for the 30 s poll) with Confirm Delivery / Can't deliver now offered; the customer's page goes "Out for delivery" within seconds | P1 |
| TC-DLV-031 | Negative | A bag on the counter cannot be delivered or failed | Look at a Ready-to-collect card; then call the deliver and fail endpoints for it | The card offers **Picked up** only — no OTP box, no "Can't deliver"; both API calls are refused (422) | P1 |
| TC-DLV-032 | Edge | Double-tap Picked up | Tap **Picked up**, kill the connection before the reply, tap again once back | No error: the rider keeps the order and it stays OUT_FOR_DELIVERY with exactly ONE hand-over on the timeline | P2 |
| TC-DLV-033 | Negative | Not my bag | Call `POST /delivery/orders/{id}/pick-up` for an order assigned to another rider | 403; the order stays READY_FOR_DELIVERY and in the other rider's list | P1 |
| TC-DLV-034 | Edge | Staff sent it out first | Open a Ready-to-collect card, have staff tap **Handed to rider**, then tap **Picked up** | No error — it is already yours and already out; the card settles in Out for delivery (the app says "this order has moved on" at worst, and the next poll agrees) | P3 |

---

## 13. Tax / GST (`tax`)

Prices are **tax-inclusive (MRP-style)**: GST is extracted from the price, never
added on top. The customer-facing total must never change because of a GST edit.

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-TAX-001 | Positive | Total unchanged by GST | Note a cart total, then change the product's GST slab in Admin, re-add to a fresh cart | Customer total is identical — GST only changes the invoice breakdown | P1 |
| TC-TAX-002 | Positive | Breakdown re-adds exactly | Place an order and open its invoice | Taxable value + CGST + SGST = the line total, to the paisa, on every line | P1 |
| TC-TAX-003 | Positive | 5% extraction | Order a ₹105 line at 5% | Taxable ₹100.00, CGST ₹2.50, SGST ₹2.50 | P1 |
| TC-TAX-004 | Positive | Zero-rated item | Order an item at 0% (e.g. fresh dairy) | No tax extracted; taxable value equals the line total | P1 |
| TC-TAX-005 | Positive | Mixed-rate order | One 5% and one 18% item in a single order | Per-line rates correct; order-level GST is the sum of the lines | P1 |
| TC-TAX-006 | Edge | Odd-paisa rounding | Order a line with an awkward price (e.g. ₹33.33) | CGST and SGST differ by at most 1 paisa and still re-add exactly | P2 |
| TC-TAX-007 | Positive | Historical invoices are frozen | Place an order, then change the product's GST rate, then reopen the OLD invoice | Old invoice still shows the ORIGINAL rate — snapshots must not move | P1 |
| TC-TAX-008 | Positive | Invoice is a GST invoice | Open any invoice | HSN and GST% columns per line; taxable value, CGST, SGST and "Total (incl. GST)" in the summary; "prices inclusive of GST" note | P1 |
| TC-TAX-009 | Positive | GSTIN shown when set | Admin → Store → enter a GSTIN, save, then issue an invoice | GSTIN printed under the store details, no restart needed | P2 |
| TC-TAX-010 | Edge | GSTIN absent | Leave it blank | Invoice renders with no empty GSTIN line | P2 |
| TC-TAX-011 | Edge | GSTIN typed as printed | Enter `29 aapfu 0939 f1zv` (spaced, lower case) | Saved and shown as `29AAPFU0939F1ZV` | P2 |
| TC-TAX-012 | Negative | Malformed GSTIN | Enter 14 characters, or a state code like `00` | Refused with a message naming the problem; nothing saved | P2 |
| TC-TAX-013 | Edge | Issued invoices don't change **[auto]** | Issue an invoice, change the GSTIN in admin, re-download that invoice | Still shows the GSTIN it was issued under; a NEW invoice shows the new one | P1 |
| TC-TAX-011 | Positive | Storefront GST line | Open an order with tax | "Includes GST of ₹X" shown under the total, matching the invoice | P2 |
| TC-TAX-012 | Edge | Legacy zero-tax orders | Open an order placed before GST was added | Shows as zero tax; no crash, no blank invoice | P2 |

---

## 14. Payments (`payments`)

> **Live prepayment is NOT implemented.** Only Pay on Delivery (cash or UPI at
> the door, settled off-platform) and a fake online UPI
> provider that always succeeds exist. Every UPI case below tests the fake
> gateway; real Razorpay acceptance testing is out of scope until integrated.

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-PAY-001 | Positive | Pay-on-delivery lifecycle | Place a COD order, complete to DELIVERED | COD_PENDING at placement → PAID at delivery | P1 |
| TC-PAY-002 | Positive | Only pay-on-delivery is offered | Open checkout on a default deployment | "Pay on Delivery" is the only method, its hint says cash or UPI at the door, and a note explains paying online in advance is coming soon | P1 |
| TC-PAY-002a | Negative | UPI cannot be forced | POST an order with `paymentMethod: "UPI"` directly to the API | Refused ("choose Pay on Delivery"); no order created, cart still usable for COD | P1 |
| TC-PAY-002b | Positive | Switching UPI on | Set `UPI_ENABLED=true`, restart, reload checkout | UPI appears as a choice and a UPI order completes (fake gateway) | P2 |
| TC-PAY-003 | Positive | Method shown consistently | Compare method/status on the tracking page, admin card, rider card and invoice | Consistent everywhere and never says "cash only": tracking + invoice read "Pay on Delivery", admin reads "Pay on delivery", the rider card reads "Collect ₹<total>" | P2 |
| TC-PAY-004 | Edge | Cancelled UPI order | Cancel a PAID UPI order inside the window | Cancellation succeeds; refund handling matches the published refund policy | P1 |

---

## 15. Notifications (`notifications`)

Requires a VAPID key pair for push (see `NOTIFICATIONS.md`). With keys unset,
the opt-in must simply not appear — that itself is TC-NOTIF-001.

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-NOTIF-001 | Edge | Push unconfigured | With no VAPID keys, open an in-flight order page | No notification opt-in shown anywhere; app otherwise normal | P1 |
| TC-NOTIF-002 | Positive | Customer opt-in | With keys set, signed in, open an in-flight order → tap "Notify me" → Allow | Button flips to "Notifications on" | P1 |
| TC-NOTIF-003 | Negative | No prompt without a tap | Load the order page and just wait | Browser NEVER asks for permission unprompted | P1 |
| TC-NOTIF-004 | Positive | Status push arrives with app closed | Subscribe, close the app/lock the phone, move the order to PACKING in Admin | Notification arrives; tapping it opens that order's tracking page | P1 |
| TC-NOTIF-005 | Positive | One notification per transition | Move an order to DELIVERED | Exactly ONE notification (not two, despite two internal events) | P1 |
| TC-NOTIF-006 | Positive | Opt-out | Tap "Notifications on" to turn it off, then transition the order | No further notifications | P2 |
| TC-NOTIF-007 | Negative | Blocked permission | Deny notification permission, reload | Explanatory hint shown instead of a dead button | P2 |
| TC-NOTIF-008 | Edge | Not offered when signed out | Sign out and open a tracking link | No opt-in (a subscription needs an account) | P2 |
| TC-NOTIF-009 | Positive | Rider gets a job alert | Rider subscribes; assign them an order **already READY_FOR_DELIVERY** (or already out) | Rider's phone alerts with the delivery address | P1 |
| TC-NOTIF-010 | Positive | Rider alerted when the bag is ready | Assign a PACKING order to the rider, then mark it READY_FOR_DELIVERY, then have the rider tap **Picked up** | Silent on assignment; alerts once the bag is ready ("Order ready to collect" + address); NO push for their own pick-up — the app must not buzz them about what they just did | P1 |
| TC-NOTIF-011 | Positive | Rider told when a job is taken away | Reassign the order to a different rider | Original rider is told it is no longer theirs; new rider alerted | P1 |
| TC-NOTIF-012 | Positive | Rider told about cancellation | Cancel an order the rider is carrying | Rider notified "delivery cancelled" | P1 |
| TC-NOTIF-013 | Security | Rider data stays with the rider | Watch a customer's tracking page while a rider alert fires | Customer's live stream NEVER shows rider messages or another customer's address | P1 |
| TC-NOTIF-014 | Positive | Admin new-order alert | Admin → Orders → enable 🔔 Alerts → place an order from the storefront | Chime plays and a desktop notification appears, even with the tab in the background | P1 |
| TC-NOTIF-015 | Positive | Alert preference remembered | Enable alerts, reload the admin app | Still enabled | P2 |
| TC-NOTIF-016 | Negative | Alerts silent by default | Fresh browser, place an order | No sound until the toggle is switched on | P2 |
| TC-NOTIF-017 | Negative | Transitions do not spam staff | Move several orders through CONFIRMED → PACKING → OUT_FOR_DELIVERY with alerts on | No chime for staff's own mid-flow steps. Only three things alert: a new order, a cancellation and a delivery — and each fires EXACTLY ONCE per order (a cancellation must not double-chime) | P2 |
| TC-NOTIF-018 | Edge | Dead subscription cleanup | Subscribe, clear site data / uninstall, then trigger a notification | Server prunes the dead subscription; no repeated failures in the API log | P3 |

---

## 16. Analytics (`analytics`)

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-ANL-001 | Positive | KPI tiles | Admin → Analytics | Today's revenue, orders, delivered, pending, week revenue/orders and gross profit all render | P1 |
| TC-ANL-002 | Positive | Revenue is not double-counted | Place one order with 3 different line items, check today's revenue | Revenue increases by the order total ONCE, not once per line | P1 |
| TC-ANL-003 | Positive | Order counts | Place 2 orders, deliver 1 | todayOrders +2, todayDelivered +1, pending reflects the rest | P1 |
| TC-ANL-004 | Positive | Gross profit | Compare the Gross Profit tile (scoped to the selected 7/30-day period) against revenue − cost for that period | Matches, and the margin % shown alongside is consistent; cost comes from the per-line snapshot | P1 |
| TC-ANL-005 | Positive | Daily chart | View the 7/30-day chart | Bars per day with revenue and gross profit; dates align to IST calendar days | P2 |
| TC-ANL-006 | Positive | Top products | View top products | Grouped by product + variant, ordered by quantity/revenue | P2 |
| TC-ANL-007 | Positive | Low stock panel | Drop a variant below its threshold | It appears in the low-stock list with available vs threshold | P2 |
| TC-ANL-008 | Edge | Empty period | View analytics on a day with no orders | Zeros and an empty state, not errors or blank tiles | P2 |
| TC-ANL-009 | Positive | Cancelled orders excluded | Cancel an order and re-check revenue | Cancelled revenue is not counted | P1 |
| TC-ANL-010 | Accessibility | Chart is readable without sight | Tab through the chart; inspect with a screen reader | Each bar is focusable with a meaningful label; a text data table is available | P2 |
| TC-ANL-011 | Security | Cost data stays internal | Check what the storefront receives anywhere | Cost price / gross profit appear ONLY in the admin app | P1 |

---

## 17. Localisation (English / Kannada)

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-I18N-001 | Positive | Language switch | Storefront → switch to ಕನ್ನಡ | UI text switches; choice persists across pages and reloads | P1 |
| TC-I18N-002 | Positive | No missing keys | Walk home → category → product → cart → checkout → order page in Kannada | No raw keys (e.g. `order.notifyEnable`) and no English fallbacks leaking | P1 |
| TC-I18N-003 | Positive | Interpolated strings | View the cancel countdown and the "Includes GST of ₹X" line in Kannada | Numbers/amounts substituted correctly inside the translated sentence | P2 |
| TC-I18N-004 | Positive | Kannada product names | Browse in Kannada with transliteration enabled | Kannada product names shown; falls back to English where blank | P2 |
| TC-I18N-005 | Edge | Switch back | Return to English | Everything reverts; no mixed-language screen | P2 |
| TC-I18N-006 | Edge | Currency format | Check any price | Indian digit grouping (₹1,00,000.00 style) in both languages | P3 |

---

## 18. PWA & offline behaviour

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-PWA-001 | Positive | Storefront installable | Open the storefront on Android Chrome | Install prompt available; installs with the correct name and icon | P1 |
| TC-PWA-002 | Positive | Runs standalone | Launch the installed storefront | Opens without browser chrome; green theme colour applied | P2 |
| TC-PWA-003 | Positive | Offline fallback | Install, go offline, navigate to an uncached page | The offline page is shown, not a browser error | P1 |
| TC-PWA-004 | Positive | Cached browsing | Browse a few categories, go offline, revisit them | Previously seen catalogue still renders | P2 |
| TC-PWA-005 | Edge | Stale service worker | Deploy a change, reload the installed app | New version picked up (may need one reload); no permanently stale UI | P2 |
| TC-PWA-006 | Positive | Rider app installable | Open the delivery app on a phone | Installable; **blue** icon, clearly distinct from the green storefront | P1 |
| TC-PWA-007 | Negative | Rider app never serves stale data | Install the rider app, go offline, reopen | No cached queue or cached delivery OTP is shown — offline state is explicit | P1 |
| TC-PWA-008 | Edge | iOS push needs install | On iPhone Safari, open the storefront without installing | Push opt-in absent; after Add to Home Screen it becomes available | P2 |
| TC-PWA-009 | Positive | Assets resolve in Docker | Run the containerised stack and open the rider app | Manifest, icons and service worker all load (no 404s) | P1 |

---

## 18a. Search visibility (storefront)

Everything here is checked on a **production-like** deployment, i.e. one with
`SITE_URL` set and `SEO_NOINDEX` unset. TC-SEO-009 is the opposite case.

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-SEO-001 | Positive | robots.txt | `GET /robots.txt` | Allows the catalogue; disallows /account, /cart, /checkout, /order/ and /search; names the canonical host and links the sitemap — all on the deployment's own hostname | P1 |
| TC-SEO-002 | Positive | Sitemap lists the live catalogue | `GET /sitemap.xml` | Home, every category and every product, as absolute URLs on the canonical host; a product added in Admin appears within the hour | P1 |
| TC-SEO-003 | Positive | Product canonical is the slug | Open a product by its numeric id and by its slug | Both pages carry the SAME canonical, pointing at the slug URL | P1 |
| TC-SEO-004 | Positive | Category canonical ignores sort | Open a category, then the same with `?sort=price_asc`, then `?page=1` | Sorted views canonicalise to the unsorted page; page 1 keeps `?page=1` — it is a different set of products | P2 |
| TC-SEO-005 | Positive | Product structured data matches the page | View source on a product page | One `Product` block with an Offer per variant: the price shown on the page, INR, and InStock only where the page lets you add to cart | P1 |
| TC-SEO-006 | Positive | The shop's own entity | View source on the home page | A `GroceryStore` block whose address, phone, coordinates, hours and delivery radius match the Store panel in Admin, plus a `WebSite` block with the search action | P1 |
| TC-SEO-007 | Positive | Share preview | Paste the home page URL into WhatsApp | A wide card with the Town Basket mark, name and tagline — not a bare link | P2 |
| TC-SEO-008 | Negative | Private pages stay out | View source on /cart, /checkout, /account and an order tracking URL | Each carries `noindex, nofollow`; /search carries `noindex, follow` | P1 |
| TC-SEO-009 | Negative | A non-production deployment hides | On QA: `GET /robots.txt`, `GET /sitemap.xml`, and view source on any page | "Disallow: /", an empty sitemap, and a noindex tag on the page — plus the `X-Robots-Tag: noindex` header from the proxy | P1 |
| TC-SEO-010 | Edge | Structured data validates | Run a product page and the home page through Google's Rich Results Test | No errors; Product and the local-business block are both recognised | P2 |

---

## 19. Security & authorisation

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-SEC-001 | Negative | Admin API needs staff role | Call an `/api/v1/admin/**` endpoint with a CUSTOMER token | 403 | P1 |
| TC-SEC-002 | Negative | Admin API needs a token | Call the same with no token | 401 | P1 |
| TC-SEC-003 | Negative | Delivery API is rider-only | Call `/api/v1/delivery/**` with a customer token | 403 | P1 |
| TC-SEC-004 | Negative | Rider cannot reach admin | With a rider token, call an admin endpoint | 403 | P1 |
| TC-SEC-005 | Negative | Customer data is per-account | With customer A's token, request customer B's orders | Only A's own orders returned | P1 |
| TC-SEC-006 | Negative | Expired/garbage token | Send a tampered or expired JWT | 401; no partial access | P1 |
| TC-SEC-007 | Positive | Refresh token rotates | Use a refresh token twice | The first use rotates it; replaying the old one fails | P1 |
| TC-SEC-008 | Negative | Push subscribe needs login | POST a push subscription with no token | 401 | P2 |
| TC-SEC-009 | Positive | Unsubscribe works signed out | Unsubscribe using only the endpoint URL after logging out | Succeeds (capability-based, by design) | P3 |
| TC-SEC-010 | Negative | CORS | Call the API from an origin not in the allow-list | Blocked by CORS | P2 |
| TC-SEC-011 | Security | Seeded credentials rotated | On any public/production deployment, try the seeded admin/staff/rider logins | **All must fail.** If any succeeds, stop the release | P1 |
| TC-SEC-012 | Security | No secrets in responses | Inspect API responses and page source | No password hashes, JWT secret, VAPID private key or cost prices anywhere | P1 |
| TC-SEC-013 | Negative | OTP not exposed early | Read the tracking payload before OUT_FOR_DELIVERY, including while READY_FOR_DELIVERY | `deliveryOtp` is null; it is never present on admin/rider payloads | P1 |
| TC-SEC-014 | Negative | QA env not public | From a device NOT on the tailnet, `curl --max-time 10 https://qa.town-basket.com/` | Times out or is refused. **Any HTTP response — 200 included — means QA is publicly reachable, and its fake OTP verifier lets anyone log in as any customer.** From a tailnet device the same URL serves the storefront and carries `X-Robots-Tag: noindex` | P1 |
| TC-SEC-015 | Security | Backup objects are private | In the DO Spaces console, open the backups bucket; try an unauthenticated GET on a `db/<timestamp>.sql.gz` URL | Bucket listing is OFF and the object returns 403 — database dumps sit at timestamp-predictable keys, so a public bucket means every customer's data is enumerable by date | P1 |

---

## 20. Cross-cutting robustness

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-NFR-001 | Edge | Concurrent last unit | Two browsers each order the LAST unit of a variant at the same time | Exactly one succeeds; the other is refused; stock never goes negative | P1 |
| TC-NFR-002 | Edge | API restart mid-session | Restart the API while a tracking page is open | Page reconnects/falls back to polling and recovers | P2 |
| TC-NFR-003 | Edge | Database restart | Restart Postgres, then use the app | API reconnects without a manual restart | P2 |
| TC-NFR-004 | Edge | Slow network | Throttle to 3G and complete a checkout | Buttons show busy state; no duplicate orders; no silent failure | P2 |
| TC-NFR-005 | Edge | Long product names | Create a product with a very long name and open it everywhere | No layout overflow in storefront, admin, rider card or invoice | P3 |
| TC-NFR-006 | Edge | Large catalogue | Import ~700 products (see the client import CSV) | Listing, search and sort stay responsive; admin lists paginate | P2 |
| TC-NFR-007 | Accessibility | Keyboard only | Complete browse → cart → checkout using only the keyboard | Every control reachable; visible focus ring throughout | P2 |
| TC-NFR-008 | Accessibility | Touch targets | Use the storefront and rider app one-handed on a phone | Tap targets ≥44 px; nothing requires precision tapping | P2 |
| TC-NFR-009 | Edge | Timezone correctness | Place an order near midnight IST | It lands on the correct IST calendar day in analytics and on the invoice | P2 |
| TC-NFR-010 | Positive | Tap feedback on touch | On a PHONE (not a desktop with a mouse), tap buttons, product tiles, category tiles and the +/− steppers | Every tap gives an immediate press-in response, before any network round-trip; with Slow 3G throttled, a tap never feels dead while the request runs | P2 |
| TC-NFR-011 | Positive | Loading states reserve space | Throttle to Slow 3G and open Account, Order tracking and Cart while data loads | Shimmer skeletons hold the layout — no bare "Loading…" line and no content jump when data lands | P3 |
| TC-NFR-010 | Edge | Money formatting | Check a large total (>₹1,00,000) across all surfaces | Consistent Indian grouping; no rounding drift between cart, order and invoice | P2 |

---

## 21. Deployment / environment checks

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-ENV-001 | Positive | Local stack | `docker compose -f infra/docker-compose.yml up --build` | All four services healthy; storefront, admin and rider apps reachable with seed data | P1 |
| TC-ENV-002 | Positive | Migrations apply cleanly | Bring up an EMPTY database | Every module's schema is created and seeded with no Flyway error | P1 |
| TC-ENV-003 | Positive | Migrations are re-runnable | Restart against an existing database | No checksum/version errors | P1 |
| TC-ENV-004 | Positive | QA env | Follow `infra/qa/README.md` end to end | All four QA hosts serve over HTTPS from a tailnet device, with no credential prompt; the storefront and admin are installable as PWAs there | P1 |
| TC-ENV-005 | Negative | Browser API URL is baked correctly | Open any deployed app and watch the network tab | Requests go to the environment's real API host — never `localhost` | P1 |
| TC-ENV-006 | Positive | Smoke test after any deploy | Log in → browse → add to cart → COD order → admin sees it → assign rider → rider confirms with OTP | Full loop passes | P1 |

---

## 22. Known gaps — do not raise as defects

These are unimplemented by design as of this document. Confirm the *stated*
behaviour rather than expecting the feature.

| Area | Current state | What to verify instead |
|---|---|---|
| Live payments | No real gateway. UPI is switched OFF by default (`UPI_ENABLED`) because the only provider is a fake that auto-succeeds | TC-PAY-002 / 002a — COD only, UPI refused |
| WhatsApp / SMS notifications | Not built — needs a provider account and DLT registration | Nothing; SSE + Web Push are the shipped channels |
| Product image upload | Admin accepts pasted URLs only | TC-ACAT-009 with a URL |
| Storefront app icons | Placeholder artwork | TC-PWA-001 checks installability, not artwork quality |
| Monitoring / alerting | Not implemented | — |
| Multiple API instances | Live tracking and rate limiting are per-instance | Test single-instance only |
| Seeded GST rates | Category-level approximations; HSN blank | TC-ACAT-020/022 after staff set real values |

---

## 23. Release sign-off checklist

Minimum set to run before any production release:

1. **TC-ENV-002, TC-ENV-005, TC-ENV-006** — clean deploy, correct API URL, full smoke loop.
2. **TC-SEC-011, TC-SEC-012, TC-SEC-013** — seeded credentials rotated, no secret or cost-price leakage, OTP gated.
3. **TC-CHK-001/002/003/012/013** — checkout, minimum order, no duplicate orders.
4. **TC-ADM-003/004/005** — order state machine and OTP-gated delivery.
5. **TC-INV-006/007** — stock released on cancel, committed on delivery.
6. **TC-TAX-002/007/008** — GST arithmetic, frozen invoices, statutory format.
7. **TC-DLV-003/004** — rider handover with OTP.
8. **TC-ORD-008/009** — cancellation window honoured.
9. **TC-NOTIF-001** (keys unset) or **TC-NOTIF-004/009** (keys set).
10. **TC-I18N-002** — no missing translations.

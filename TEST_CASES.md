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
| QA | https://qa.town-basket.com | qa-admin. | qa-delivery. | Behind HTTP basic auth — see `infra/qa/README.md` |

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

---

## 6. Checkout (`orders` + `payments`)

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-CHK-001 | Positive | Pay-on-delivery order | Cart ≥ ₹299, in-radius address, store open → choose Pay on Delivery | Order created; status CONFIRMED; payment COD_PENDING; confirmation page shows the tracking link | P1 |
| TC-CHK-002 | Positive | UPI order (fake gateway) | Same cart, choose UPI | Order CONFIRMED; payment PAID (fake provider auto-succeeds) | P1 |
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
| TC-ORD-002 | Positive | Live status updates | Keep the tracking page open; move the order to PACKING in Admin | Status updates within seconds without a manual refresh; "live" indicator lit | P1 |
| TC-ORD-003 | Edge | Live falls back to polling | Block SSE (throttle/offline briefly) then restore | Page keeps updating via polling; indicator shows "updating" | P2 |
| TC-ORD-004 | Positive | Delivery code appears at the right time | Watch the page as the order goes to OUT_FOR_DELIVERY | OTP is hidden at PLACED/CONFIRMED/PACKING and appears ONLY at OUT_FOR_DELIVERY | P1 |
| TC-ORD-005 | Positive | Order history | Account → Orders | Past orders newest-first with their statuses | P2 |
| TC-ORD-006 | Positive | Reorder / buy again | Account → an old order → Reorder | New cart populated with the still-available lines | P2 |
| TC-ORD-007 | Edge | Reorder skips dead items | Make one line's product unavailable, then reorder | Available lines added, unavailable ones silently skipped | P2 |
| TC-ORD-008 | Positive | Self-cancel inside the window | Place an order and cancel within 1 minute | Order CANCELLED; countdown was visible on the button; reserved stock released (check Inventory) | P1 |
| TC-ORD-009 | Negative | Self-cancel after the window | Wait >1 minute, then try to cancel | Refused, routed to support; order unchanged | P1 |
| TC-ORD-010 | Negative | Self-cancel after packing starts | Move the order to PACKING, then try to cancel | Refused — "already being prepared" | P1 |
| TC-ORD-011 | Edge | Cancel is idempotent | Cancel, then tap cancel again / reload and retry | Still CANCELLED; no error, no double release of stock | P2 |
| TC-ORD-012 | Positive | Invoice PDF | Tracking page → Download invoice | PDF opens: store details, bill-to, itemised lines, totals, payment line | P1 |
| TC-ORD-013 | Security | Tracking token is the only key | Open a tracking link, then alter the token in the URL | No other customer's order is reachable; 404 | P1 |
| TC-ORD-014 | Security | Order ids are not enumerable | Try `/order/1`, `/order/2` | Sequential ids do not expose orders — only the UUID token works | P1 |

---

## 8. Admin order queue & fulfilment (`orders` admin)

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-ADM-001 | Positive | New order appears live | Keep Admin → Orders open; place an order from the storefront | Order appears within seconds without a refresh | P1 |
| TC-ADM-002 | Positive | Status filter tabs | Click through the status tabs | Only orders in that status listed; counts make sense | P1 |
| TC-ADM-003 | Positive | Full happy path | CONFIRMED → PACKING → OUT_FOR_DELIVERY → DELIVERED (with OTP) | Each transition accepted; timeline records each step | P1 |
| TC-ADM-004 | Negative | Illegal transition | Try PLACED → DELIVERED, or CONFIRMED → OUT_FOR_DELIVERY | Rejected as an illegal transition | P1 |
| TC-ADM-005 | Negative | Deliver without the OTP | Try to mark DELIVERED with a blank or wrong OTP | Refused — "Delivery OTP does not match" | P1 |
| TC-ADM-006 | Negative | Terminal states are final | Try to transition a DELIVERED or CANCELLED order | No transitions offered/accepted | P1 |
| TC-ADM-007 | Positive | Staff cancellation | Cancel a CONFIRMED order from Admin | CANCELLED; reserved stock released; customer's page reflects it live | P1 |
| TC-ADM-008 | Positive | Assign a rider | Assign the seeded rider to a PACKING order | Assignment saved and shown on the card | P1 |
| TC-ADM-009 | Negative | Assign an inactive rider | Deactivate the rider, then try to assign them | Refused — "not an active delivery agent" | P1 |
| TC-ADM-010 | Positive | Reassign / unassign | Change the rider, then clear the assignment | Order returns to the unassigned pool | P2 |
| TC-ADM-011 | Negative | Assign on a closed order | Try to assign a rider to a DELIVERED order | Refused | P2 |
| TC-ADM-012 | Positive | COD marked paid on delivery | Complete a COD order through DELIVERED | Payment status flips to PAID at delivery | P1 |
| TC-ADM-013 | Security | Cost price never leaks | Inspect the order payload the admin/storefront receives | No `costPrice` on any order line in either app | P1 |

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
| TC-RID-007 | Positive | Date-wise delivery counts | Complete 2 deliveries with one rider, expand their stats | Counts shown per date and match reality | P2 |
| TC-RID-008 | Edge | Rider with no deliveries | Expand a brand-new rider's stats | Empty state, not an error | P3 |

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
| TC-TAX-009 | Positive | GSTIN shown when configured | Set `townbasket.invoice.gstin`, restart, reopen an invoice | GSTIN printed under the store details | P2 |
| TC-TAX-010 | Edge | GSTIN absent | Leave it unset | Invoice renders with no empty GSTIN line | P2 |
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
| TC-NOTIF-009 | Positive | Rider gets a job alert | Rider subscribes; assign them an order **already OUT_FOR_DELIVERY** | Rider's phone alerts with the delivery address | P1 |
| TC-NOTIF-010 | Positive | Rider alerted at dispatch | Assign a PACKING order to the rider, then move it to OUT_FOR_DELIVERY | Silent on assignment; alerts on dispatch ("ready to collect") | P1 |
| TC-NOTIF-011 | Positive | Rider told when a job is taken away | Reassign the order to a different rider | Original rider is told it is no longer theirs; new rider alerted | P1 |
| TC-NOTIF-012 | Positive | Rider told about cancellation | Cancel an order the rider is carrying | Rider notified "delivery cancelled" | P1 |
| TC-NOTIF-013 | Security | Rider data stays with the rider | Watch a customer's tracking page while a rider alert fires | Customer's live stream NEVER shows rider messages or another customer's address | P1 |
| TC-NOTIF-014 | Positive | Admin new-order alert | Admin → Orders → enable 🔔 Alerts → place an order from the storefront | Chime plays and a desktop notification appears, even with the tab in the background | P1 |
| TC-NOTIF-015 | Positive | Alert preference remembered | Enable alerts, reload the admin app | Still enabled | P2 |
| TC-NOTIF-016 | Negative | Alerts silent by default | Fresh browser, place an order | No sound until the toggle is switched on | P2 |
| TC-NOTIF-017 | Negative | Transitions do not spam staff | Move several orders through statuses with alerts on | Chime fires only for genuinely NEW orders | P2 |
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
| TC-SEC-013 | Negative | OTP not exposed early | Read the tracking payload before OUT_FOR_DELIVERY | `deliveryOtp` is null; it is never present on admin/rider payloads | P1 |
| TC-SEC-014 | Negative | QA env not public | Open a `qa.*` host in a fresh browser | HTTP basic auth challenge; page carries `X-Robots-Tag: noindex` | P2 |

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
| TC-NFR-010 | Edge | Money formatting | Check a large total (>₹1,00,000) across all surfaces | Consistent Indian grouping; no rounding drift between cart, order and invoice | P2 |

---

## 21. Deployment / environment checks

| ID | Type | Scenario | Steps | Expected result | Pri |
|---|---|---|---|---|---|
| TC-ENV-001 | Positive | Local stack | `docker compose -f infra/docker-compose.yml up --build` | All four services healthy; storefront, admin and rider apps reachable with seed data | P1 |
| TC-ENV-002 | Positive | Migrations apply cleanly | Bring up an EMPTY database | Every module's schema is created and seeded with no Flyway error | P1 |
| TC-ENV-003 | Positive | Migrations are re-runnable | Restart against an existing database | No checksum/version errors | P1 |
| TC-ENV-004 | Positive | QA env | Follow `infra/qa/README.md` end to end | All four QA hosts serve over HTTPS behind basic auth | P1 |
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

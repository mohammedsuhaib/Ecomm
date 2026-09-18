# Notifications

How Town Basket tells people something happened, and what you have to configure
to turn each channel on.

## The shape of it

The `notifications` module listens to order domain events and fans each one out
to every enabled **channel** (`NotificationChannel`). Adding a channel means
adding one class — nothing about the order flow changes. Delivery is
best-effort by construction: a channel that fails is logged and skipped, and can
never block an order transition or another channel.

| Channel | Reaches | Needs configuring | Status |
|---|---|---|---|
| `SSE` | The tracking page and admin queue **while open** | Nothing | Always on |
| `WEB_PUSH` | A customer's or rider's phone **even with the app closed** | A VAPID key pair | On when keys are set |

Every delivery is recorded in `notifications.notification_log` (order, channel,
type), so you can answer "was the customer actually told?" after the fact.

Each message carries an **audience** — customer, staff, or rider — and channels
use it to decide what is theirs to deliver. That is what keeps a rider's job
(including the customer's address) out of the public order-tracking stream.

Staff get a third thing that isn't a server channel at all: the admin queue
plays a chime and raises a desktop notification when a new order arrives. It
runs in the open dashboard tab, so it needs no keys and no setup — just press
**🔔 Alerts** once per browser (the browser asks for notification permission and
unlocks audio on that click, and the choice is remembered).

## Turning on Web Push

One-time, then it works for every customer **and rider** who opts in — the same
key pair serves both apps.

1. **Generate a VAPID key pair** (any machine with Node):
   ```bash
   npx web-push generate-vapid-keys
   ```
   You get a public and a private key. The pair identifies this server to the
   browser push services; generate it **once** and keep it — regenerating
   invalidates every existing subscription and customers must opt in again.

2. **Give them to the API** as environment variables:
   ```
   WEB_PUSH_PUBLIC_KEY=<public key>
   WEB_PUSH_PRIVATE_KEY=<private key>
   WEB_PUSH_SUBJECT=mailto:you@yourdomain.com
   ```
   The private key is a **secret** — env var or your compose `.env` (which is
   gitignored), never committed. The public key is not secret; browsers need it.

3. **Restart the API.** It logs `Web Push channel enabled.` on startup. With no
   keys set it logs that the channel is disabled and everything else behaves
   exactly as before — that's the local-dev default, so nothing to do there.

The storefront picks it up on its own: it asks the API whether push is
available, and only then shows "🔔 Notify me about this order" on the order
page. No keys, no button.

### What the customer sees

They must be signed in (the subscription is stored against their account) and
tap the button — the browser then asks permission. From that point, every status
change on an in-flight order sends one notification: packed, out for delivery,
delivered, cancelled. Tapping it opens their tracking page.

### What the rider sees

The delivery app has its own installable PWA (blue, so it is never confused with
the green storefront on a home screen) and its own **🔕 Turn on new-delivery
alerts** button above the queue. Riders should install it and switch alerts on
during onboarding — it is the only way they learn about a job without staring at
the phone.

They are notified at the three moments that change what they do:

| When | They see |
|---|---|
| An order they hold goes **out for delivery** | "Delivery ready to collect · Deliver to <address>" |
| An order **already out for delivery** is assigned to them | "New delivery assigned · Deliver to <address>" |
| An order they hold is **cancelled** or handed to someone else | "Delivery cancelled" / "Delivery reassigned" |

Deliberately **not** notified: an order assigned to them while it is still being
packed. Their queue lists out-for-delivery orders only, so buzzing then would
point at a job they cannot see or collect — the out-for-delivery message covers
it a moment later. New-job alerts vibrate and stay on screen until tapped;
cancellations do not vibrate, so they don't startle someone mid-ride.

Notes worth knowing:

- **Nothing is pushed to anyone who is signed out.** A browser subscription
  belongs to the browser, not to the session — it survives a logout, and a
  signed-out phone may never run our code again to say so. So the recipient's
  session is checked at send time (`AuthService#hasActiveSession`), and a person
  who has signed out, whose session has expired, or whose sessions an admin has
  revoked is skipped. This is what stops a rider phone handed to the next rider
  from announcing the previous rider's deliveries — their customers' addresses
  included. The subscription is left alone and goes quiet until they sign in
  again, so turning alerts on is a one-time thing per phone, not per shift.
- **Android/Chrome** works with the site simply open. **iPhone** only allows
  push once the customer has *installed* the PWA to their home screen (Safari
  16.4+) — until then the button won't appear for them.
- Permission is only ever requested on an explicit tap. Prompting on page load
  is the fastest route to being permanently blocked.
- If a customer clears site data or uninstalls, their push endpoint dies; the
  server prunes it automatically on the next 404/410 from the push service.

## Verifying it works

1. Set the keys and restart; confirm the startup log line.
2. Sign in on a phone, place an order, tap **Notify me about this order**, allow.
3. In the admin app, move that order to **Packing**.
4. The phone shows "We're packing your order" within a second or two — lock the
   screen first to prove it works with the app closed.
5. Check it was recorded:
   ```sql
   SELECT order_id, channel, type, created_at
     FROM notifications.notification_log ORDER BY created_at DESC LIMIT 10;
   ```
   A `WEB_PUSH` row means it was actually delivered to at least one device.

If nothing arrives: confirm the customer is subscribed
(`SELECT count(*) FROM notifications.push_subscriptions WHERE user_id = …`),
confirm they are still signed in on that device — a subscription with no live
session is skipped on purpose (`SELECT count(*) FROM identity.refresh_tokens
WHERE user_id = … AND NOT revoked AND expires_at > now()`) — and check the API
log for `Push to subscription … rejected`, where the HTTP status from the push
service says why.

## Adding WhatsApp or SMS later

Both are a natural fit for Indian customers, and both need a paid third party
before a line of code is worth writing:

- **WhatsApp** — a Business API provider (Gupshup, Interakt, Twilio…), a Meta
  Business account, and message templates approved in advance. Priced per
  conversation.
- **SMS** — a provider plus **DLT registration** with TRAI (sender ID and
  template registration), which takes days and is mandatory for transactional
  SMS in India.

When you have an account, each becomes one new class implementing
`NotificationChannel` — take `WebPushNotificationChannel` as the template:
report `isEnabled()` false unless its credentials are configured, send
best-effort, and return whether anything actually went out. Nothing in `orders`
or the event flow changes.

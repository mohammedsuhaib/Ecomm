# Town Basket on the Google Play Store (Trusted Web Activity)

The Play Store app is a **Trusted Web Activity (TWA)**: a thin Android wrapper
that opens the live storefront (https://shop.town-basket.com) full-screen with
no browser UI. There is no second codebase — **updating the website updates the
app**, with no Play review needed for content changes.

> ⚠️ **Do not submit until the real storefront is live** at
> shop.town-basket.com. Google's *minimum functionality* policy rejects
> low-content webviews — a "Coming Soon" page will be rejected and can flag a
> new developer account. The storefront (browse/cart/checkout) is exactly what
> passes review.

## Files here

| File | Purpose |
|---|---|
| `twa-manifest.json` | Bubblewrap build config (package `com.townbasket.app`, green theme, portrait) |
| `assetlinks.template.json` | Template for the Digital Asset Links file that removes the browser address bar |
| `town-basket.keystore` | **Never committed** (gitignored). Generated once, must be backed up — see below |

## One-time setup

1. **Play Console account** — https://play.google.com/console, $25 one-time,
   identity verification required.
   ⚠️ New personal accounts must run a **closed test (~12 testers for 14 days)**
   before production access — budget 3–4 weeks end to end.

2. **Install Bubblewrap** (needs Node 18+; it offers to install the JDK and
   Android SDK itself on first run):
   ```bash
   npm i -g @bubblewrap/cli
   bubblewrap doctor
   ```

3. **Build the app bundle** (from this directory):
   ```bash
   bubblewrap build
   ```
   On first run it generates `town-basket.keystore` and asks you to set
   passwords. Output: `app-release-bundle.aab` (upload this to Play Console)
   and an `app-release-signed.apk` (for local phone testing via
   `adb install`).

   > 🔑 **Back up `town-basket.keystore` + its passwords somewhere safe**
   > (password manager + offline copy). Losing it means you can never update
   > the app again — Google cannot recover it. Prefer enrolling in
   > **Play App Signing** during the first upload, which makes Google hold the
   > production key so this risk mostly disappears.

4. **Digital Asset Links** — proves you own both the app and the site, and is
   what removes the address bar:
   ```bash
   keytool -list -v -keystore town-basket.keystore -alias town-basket \
     | grep 'SHA256:'
   ```
   Copy the fingerprint (the `AA:BB:...` value) into a copy of
   `assetlinks.template.json` and ship it as
   `apps/storefront/public/.well-known/assetlinks.json` (Next.js serves
   `public/` at the site root). Redeploy the storefront, then verify:
   ```
   https://shop.town-basket.com/.well-known/assetlinks.json
   ```
   > If you enrol in **Play App Signing**, Play Console re-signs the app with
   > Google's key — add **that** SHA-256 (Play Console → Setup → App signing)
   > to `assetlinks.json` as a second entry, or the address bar comes back for
   > store installs.

5. **Store listing** (Play Console):
   - Privacy policy URL: `https://town-basket.com/privacy.html` (already live)
   - Screenshots (min 2), 512×512 icon (`apps/storefront/public/icons/icon-512.png`),
     1024×500 feature graphic
   - Data safety form: the app collects phone number (login), name, delivery
     address, and order history; no data sold; served over HTTPS
   - Content rating questionnaire: shopping app, no user-generated content

## Releasing an update

Only needed when the **wrapper** changes (icon, name, package, notification
support) — website changes ship instantly with a normal deploy.

```bash
# bump appVersionCode (+1) and appVersionName in twa-manifest.json, then
bubblewrap build
# upload the new .aab in Play Console
```

## Testing before the store

```bash
adb install app-release-signed.apk
```
Address-bar-free display only works once `assetlinks.json` (with the right
fingerprint) is live on the domain.

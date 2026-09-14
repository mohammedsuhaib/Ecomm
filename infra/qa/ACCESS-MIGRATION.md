# Migrating QA access off HTTP basic auth

**Status: planned, not applied.** Nothing in this file has been changed in the
Caddyfile or the deploy workflow yet, on purpose — see
[Do not reorder these steps](#do-not-reorder-these-steps). It is written out so
the change is one reviewed commit when the prerequisite is in place, rather than
something improvised on the droplet at the moment QA is broken.

## Why

The three QA app hosts sit behind Caddy `basic_auth`. That gate covers the whole
origin, and it is incompatible with an installable PWA. Measured against
`qa.town-basket.com`:

| path | status without credentials |
|---|---|
| `/` | 401 |
| `/sw.js` | 401 |
| `/manifest.webmanifest` | 401 |
| `/icons/icon-192.png` | 401 |
| `/_next/static/chunks/…` | 401 |
| `/offline` | 401 |

So on QA the service worker script cannot be fetched, which means it cannot
install or update; Serwist's precache fetches cannot succeed, so the app shell
is never stored; and the offline fallback is itself behind the gate. An
installed PWA window has no address bar, so the browser never offers the
credential prompt — the tester sees a bare `HTTP ERROR 401` with a Reload button
that cannot help. It surfaces after a deploy because a deploy changes every
asset hash, so whatever the old worker had cached stops being usable and
navigations reach the network for the first time in a while.

The deeper problem is not the inconvenience. **QA exists to be a rehearsal of
production, and this gate is the only thing in QA's request path that production
does not have.** Production (`infra/deploy/Caddyfile`) proxies
`shop.town-basket.com` straight through with no auth. So QA cannot validate the
single most distinctive property of the storefront — that it is an installable
PWA — and any bug in the install/update path is invisible until production.

Anything you put in the request path, you are also testing.

## Why the gate cannot simply be deleted

QA deliberately runs the **fake** phone verifier: `docker-compose.qa.yml` leaves
`TOWNBASKET_IDENTITY_FIREBASE_PROJECT_ID` out of the API container, so the API
accepts `dev:<any-10-digit-phone>` as a valid OTP (see the comment at
`docker-compose.qa.yml:65` and "When a login returns 401" in `README.md`).

**Anyone who can reach QA can log in as any customer by typing a phone number.**
QA's application-level auth is bypassable by design, which is exactly what makes
the gate load-bearing. The protection has to *move*, not disappear.

## The decision

Move QA's protection below HTTP, to the network layer, and delete the
application-layer gate. Then QA's HTTP behaviour is byte-identical to
production's, the PWA installs and updates there exactly as it will in
production, and the Caddyfile gets simpler rather than more complex.

**Tailscale** (or any WireGuard mesh) rather than a DigitalOcean firewall
IP-allowlist, because mobile data hands out dynamic IPs and the whole point is
to test a grocery PWA on a phone. A tailnet is also an off-the-shelf component
with no custom logic in it — cheaper to maintain than bespoke auth inside a
reverse proxy.

### Rejected: a cookie gate

Keeping `basic_auth` but having Caddy set a long-lived cookie on success, and
accepting that cookie thereafter, does make the PWA work: cookies ride along on
service-worker fetches and navigations with no prompt. It was the first
recommendation here and it is the wrong one. It puts bespoke auth logic in the
reverse proxy, it introduces a bearer cookie with its own expiry and leak
questions, and — decisively — it *preserves* the QA/production divergence
instead of removing it. It makes the symptom go away while leaving QA
structurally not-quite-production, which is how you get a bug that only
reproduces in production.

## Do not reorder these steps

Step 1 must be finished and verified before step 2 is merged. Between removing
the gate and having the tailnet, QA is a publicly reachable environment whose
OTP verifier accepts any phone number.

### 1. Put the droplet and the test devices on a tailnet

On the QA droplet:

```bash
curl -fsSL https://tailscale.com/install.sh | sh
tailscale up --ssh --hostname=townbasket-qa
tailscale ip -4                      # note the 100.x address
```

Then, so the qa-* hostnames resolve to the tailnet rather than the public IP,
either use Tailscale's MagicDNS names, or point the `qa.*` DNS records at the
tailnet address. Install the Tailscale app on each test phone and sign in.

Verify **before** going further — from a device that is NOT on the tailnet:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' --max-time 10 https://qa.town-basket.com/
# must time out or refuse. A 200 here means step 2 would expose QA.
```

Restrict the droplet's public ingress to 80/443 only if you still need
Let's Encrypt HTTP-01 challenges; otherwise close both and switch Caddy to a
DNS-01 issuer.

### 2. Delete the application-layer gate

`infra/qa/Caddyfile` — remove the `qa_gate` snippet and its three imports:

```diff
-(qa_gate) {
-	basic_auth {
-		qa {env.QA_BASIC_AUTH_HASH}
-	}
-}
-
 qa.town-basket.com {
 	import qa_common
-	import qa_gate
 	reverse_proxy storefront:3000
 }
```

…and the same single-line deletion under `qa-admin` and `qa-delivery`. Keep
`qa_common`: `X-Robots-Tag: noindex, nofollow` should stay regardless.

### 3. Update the deploy workflow, or the next deploy goes red

`.github/workflows/deploy-qa.yml` asserts the gate is present. Both of these
must change in the same commit as step 2:

- the `QA_BASIC_AUTH_HASH` interpolation check (around lines 197–213) becomes
  dead and should be deleted;
- the public probe `check qa.town-basket.com / 401 6` (around line 297) now
  expects **200**, not 401.

That second line is worth pausing on. It was added to prove the gate was still
up, but it cannot distinguish "correctly demanding credentials" from "rejecting
every password because the hash got corrupted" — the exact failure the README
warns about, and the one the workflow's hash check exists to catch separately.
A probe that returns the same result whether the system works or is completely
broken is not a test. Expecting 200 is strictly more informative: it proves an
app actually answered.

Note the probe runs from a GitHub runner, which will not be on the tailnet. Once
step 1 is done, an external probe of `qa.town-basket.com` should fail by design,
so this check should move inside the compose network alongside the existing
upstream probes (`for target in storefront:3000 admin:3001 delivery:3002`)
rather than be pointed at the public hostname.

### 4. Then remove the now-unused secret

Drop `QA_BASIC_AUTH_HASH` from the droplet's `.env` and the setup instructions
in `README.md`, and delete the hash-quoting warning — a whole section of
documentation that exists only to stop `$`-sequences being eaten by Compose
interpolation stops being needed.

## Interim, needing no deploy

Until step 1 is done, a tester hitting the 401 in the installed PWA can:

1. open `https://qa.town-basket.com` in a normal Chrome tab and authenticate as
   `qa` / the QA password — Chrome caches the credentials for the origin, and the
   installed PWA shares the profile;
2. if it still fails, the old service worker is wedged: Chrome → Settings → Site
   settings → `qa.town-basket.com` → **Clear & reset**, authenticate in a tab
   again, then reinstall the PWA.

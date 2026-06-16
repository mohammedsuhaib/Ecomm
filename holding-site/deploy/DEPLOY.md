# Deploying the Town Basket holding site to town-basket.com

This puts the "coming soon" + policy pages live on **town-basket.com** with
automatic HTTPS, so the payment provider (Paytm) KYC review can see the
required policy pages. It uses Caddy (auto-TLS) in Docker.

> Note: the support/grievance **email is still a placeholder** in the pages —
> deploy now, then update the email and re-deploy (just `git pull` +
> `docker compose restart` on the droplet).

## Prerequisites
- A DigitalOcean droplet (Bangalore region; the smallest $4–6/mo droplet is
  plenty for a static site). **Recommended: create it from the Marketplace
  "Docker on Ubuntu" image** so Docker + Compose are pre-installed.
  Note its **public IP**.
- Access to the **town-basket.com DNS** at your domain registrar.

## Steps

### 1. Point DNS at the droplet
At the registrar for town-basket.com, create two **A records**:

| Type | Host | Value |
|------|------|-------|
| A | `@`   | <droplet public IP> |
| A | `www` | <droplet public IP> |

Wait for propagation (usually minutes; can take up to ~30).

### 2. Prepare the droplet
SSH in and open the web ports:
```bash
ssh root@<droplet-ip>
ufw allow 80 && ufw allow 443 && ufw allow OpenSSH && ufw --force enable
```
If you used a plain Ubuntu image (not the Marketplace "Docker" image),
also install Docker first:
```bash
curl -fsSL https://get.docker.com | sh
```
The Marketplace "Docker on Ubuntu" image already has Docker + Compose —
skip the install line.

### 3. Get the site onto the droplet
Clone the repo (or copy the `holding-site/` folder up with scp):
```bash
git clone -b claude/quick-commerce-pwa-design-mgkv6g \
  https://github.com/mohammedsuhaib/Ecomm.git
cd Ecomm/holding-site/deploy
```

### 4. Launch
```bash
docker compose up -d
```
Caddy automatically obtains Let's Encrypt certificates for town-basket.com
and www.town-basket.com (DNS must already point here — step 1).

### 5. Verify
Open **https://town-basket.com**. Confirm these load (give these URLs to Paytm):
- https://town-basket.com/terms.html
- https://town-basket.com/privacy.html
- https://town-basket.com/refund.html
- https://town-basket.com/shipping.html
- https://town-basket.com/contact.html

## Updating later (e.g. once the support email is set)
```bash
cd Ecomm && git pull
cd holding-site/deploy && docker compose restart
```

## When the real app is ready
This holding site is temporary. When the Next.js storefront goes live,
the production deployment (see `infra/deploy/`) replaces this — the apex
domain will serve the storefront and these policy pages move into the app.

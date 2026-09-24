# How the holding site is served

The holding site no longer runs its own web server. The production Caddy
(`infra/deploy/Caddyfile`, started by `infra/deploy/docker-compose.prod.yml`)
bind-mounts this folder and, on **town-basket.com**:

- serves the policy pages and the files they load:
  - https://town-basket.com/terms.html
  - https://town-basket.com/privacy.html
  - https://town-basket.com/refund.html
  - https://town-basket.com/shipping.html
  - https://town-basket.com/contact.html
- 301-redirects everything else (including `/`) to https://shop.town-basket.com.

`www.town-basket.com` redirects the policy pages to the apex and everything
else to the shop.

Changes to `holding-site/` merged to `main` go out with the normal app deploy
(`.github/workflows/deploy-app.yml`), which also verifies the policy pages and
the redirect afterwards.

**Do not start a second Caddy on the droplet.** Every `*.town-basket.com` name
resolves to the same machine, only one process can hold ports 80/443, and the
old standalone stack here shared the compose project name `deploy` with the
production stack — each deploy silently replaced the other's container, which
took the shop offline.

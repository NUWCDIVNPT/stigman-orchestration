# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Docker Compose orchestration that runs STIG Manager with a Keycloak identity provider
behind an nginx reverse proxy. Auth is Keycloak username/password with self-registration,
plus **optional** CAC/PIV smartcard login via Keycloak's **built-in** X.509 authenticator
(no custom plugins). Designed for **localhost development/testing only** — NOT for production.

## Architecture

4 services behind a TLS-terminating reverse proxy (diagram at `diagrams/kc-reverse-1.svg`,
which still depicts the older mandatory-mTLS design — pending a redraw):

1. **nginx** (port 443) — terminates TLS, reverse-proxies `/stigman/` and `/kc/`.
   Client-certificate verification is `optional`; when a client cert is presented it is
   forwarded to Keycloak in the `ssl-client-cert` header. Hard mTLS enforcement (HTTP 496)
   is present but commented out. A `map` extracts the cert CN (`$client_cn`) so the SSI
   landing page (`nginx/index.html`) can show the user their CN for self-registration.
2. **keycloak** (port 8080) — identity provider on the `/kc` subpath. The realm's
   `X.509 Browser` flow uses the **built-in** `auth-x509-client-username-form` authenticator
   (CN → username) as an alternative above the username/password forms. No custom SPI jar.
3. **stigman** (port 54000) — STIG Manager API/web app; uses back channel
   (`STIGMAN_OIDC_PROVIDER`) and front channel (`STIGMAN_CLIENT_OIDC_PROVIDER`) Keycloak URLs.
4. **mysql** — database backend; persistent storage in `mysql-data/` (gitignored).

## Common Commands

```bash
docker compose up                 # start
docker compose up -d              # start detached
docker compose down               # stop
docker compose logs -f stigman    # follow a service's logs
docker compose down && rm -rf mysql-data && docker compose up   # reset DB
```

**Ready indicator**: STIG Manager's `started` message:
```json
{"date":"...","level":3,"component":"index","type":"started","data":{"durationS":...,"port":54000,...}}
```

## Access URLs

- STIG Manager: https://localhost/stigman/
- Keycloak Admin: https://localhost/kc/admin (credentials: `admin/Pa55w0rd`)

## Key Configuration Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Orchestration — service definitions and environment variables |
| `nginx/nginx.conf` | Reverse proxy; optional client-cert validation; commented-out mTLS 496 enforcement |
| `kc/stigman_realm.json` | Keycloak realm (`X.509 Browser` flow using the built-in X.509 authenticator + username/password fallback) |
| `kc/themes/stigman/` | Login theme; `login.ftl`/`register.ftl` add a static banner telling first-time CAC users to register with their certificate CN as the username (built-in Keycloak can't display the actual CN) |
| `certs/ca/demoCA.crt` | Demo CA for the localhost server cert — import to browser trusted roots |
| `certs/dod/DoD_Root_CAs.pem` | CA bundle used to validate client (CAC/PIV) certs |

## Critical Environment Variables

**Keycloak (reverse proxy on a subpath, Keycloak 26):**
- `KC_PROXY_HEADERS=xforwarded` — trust nginx `X-Forwarded-*` headers (edge TLS)
- `KC_HTTP_ENABLED=true` — required for edge TLS termination
- `KC_HOSTNAME=https://localhost/kc` — public base URL including the subpath
- `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` — back-channel URLs (e.g. `jwks_uri`) resolved from the request so the API at `http://keycloak:8080` gets internal endpoints (without it, STIG Manager OIDC discovery fails with `ECONNREFUSED`)
- `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD` — initial admin
- `KC_SPI_X509CERT_LOOKUP_PROVIDER=nginx`, `KC_SPI_X509CERT_LOOKUP_NGINX_SSL_CLIENT_CERT=SSL-CLIENT-CERT` — read the client cert from nginx (CAC/PIV)
- (`KC_PROXY=edge`, `KC_HOSTNAME_URL` are removed/renamed in Keycloak 26 — do not reintroduce.)

**STIG Manager:**
- `STIGMAN_OIDC_PROVIDER` — back channel (`http://keycloak:8080/realms/stigman`)
- `STIGMAN_CLIENT_OIDC_PROVIDER` — front channel (`https://localhost/kc/realms/stigman`); defaults to the back-channel value if unset
- `STIGMAN_CLIENT_ID=stig-manager` — OAuth clientId the web app sends; must match the realm Client ID

## Authentication Flow

1. Browser → nginx (443) over HTTPS; STIG Manager redirects it to Keycloak for auth.
2. If a client cert is presented and nginx validates it, the cert is forwarded to Keycloak.
3. Keycloak's built-in X.509 authenticator maps the cert CN to a username:
   - match → single sign-on;
   - no match (or no cert) → falls through to the username/password + **Register** form.
4. First-time CAC users register with their certificate CN as the username (the login
   theme adds a static reminder; built-in Keycloak can't display the actual CN value).
5. Keycloak issues an OAuth2 token; STIG Manager validates it for API access.

Auto-creating an account from a certificate is the only piece that needs a custom
authenticator/SPI; this sample uses self-registration instead.

## Privileges

The realm's `default-roles-stigman` composite grants **`admin` + `create_collection`** to
every user — so anyone who registers is a demo admin. Convenient for testing, NOT for
production. Adjust the default role / assign roles per user in the Keycloak admin console.

## Browser Setup Required

1. Import `certs/ca/demoCA.crt` into browser/OS trusted roots.
2. Remove it after testing.

**Chrome HSTS cleanup** (if localhost HTTP stops working):
- `chrome://net-internals/#hsts` — delete domain security policies
- `chrome://settings/clearBrowserData` — clear cached images and files

## Updating DoD Certificates (for CAC validation)

1. Download from https://dl.cyber.mil/pki-pke/zip/unclass-certificates_pkcs7_DoD.zip
2. Extract the PKCS#7 file
3. Run `certs/dod/p7b-convert.sh` to convert to PEM

## CAC toggles

- **Disable CAC:** set the X.509 execution to `DISABLED` in the realm's `X.509 Browser`
  flow (or point the realm Browser flow at the built-in `browser` flow).
- **Require CAC (mandatory mTLS):** uncomment the `if ($return_unauthorized)` line and the
  two `map` blocks in `nginx/nginx.conf`.

## Data Persistence

- **MySQL data**: `mysql-data/` (auto-created, gitignored).
- **Keycloak data**: ephemeral by default; uncomment the `kc/h2` volume mount to persist.

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `496` from nginx | mTLS enforcement is enabled and the client cert failed/was missing — present a valid cert or re-comment the 496 block |
| Keycloak won't start | Validate `kc/stigman_realm.json` (`python3 -m json.tool`); check for references to a removed authenticator/provider |
| Keycloak login URLs wrong/redirect loops | Check `KC_HOSTNAME`, `KC_PROXY_HEADERS`, `KC_HTTP_ENABLED` and the nginx `X-Forwarded-*` headers |
| Theme/CN page not updating | Theme caching is disabled via `KC_SPI_THEME_CACHE_*`; otherwise restart Keycloak |
| MySQL connection refused | Wait longer for MySQL to initialize on first run |
| WebSocket errors in logs | nginx config already includes WebSocket proxy headers for `/stigman/` |

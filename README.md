# STIG Manager + Keycloak with optional CAC authentication

An example docker-compose orchestration that runs **STIG Manager** with a **Keycloak**
identity provider behind an **nginx** reverse proxy that terminates TLS, with optional
CAC/PIV smartcard authentication. It demonstrates the configuration most deployments need:

- Keycloak running on a **subpath** (`/kc`) behind a TLS-terminating proxy
- STIG Manager's **front-channel / back-channel** OIDC split
- Username/password login with **self-registration**
- **Optional** CAC/PIV smartcard login using Keycloak's **built-in** X.509 authenticator

## Limitations of this example

**This example supports connections to and from `localhost` only and is NOT intended
for production use.** It is a starting point to illustrate the moving parts; adapt it to
your environment and your organization's security policy. The Keycloak realm in
`kc/stigman_realm.json` is a convenience for the demo and is **not** a production realm.

## General architecture

![Reverse proxy architecture](diagrams/kc-reverse-1.svg)

- `nginx` terminates TLS on the front-channel HTTPS port (443) and reverse-proxies to
  `stigman` and `keycloak` on their back-channel HTTP ports.
- `nginx` **optionally** validates a client (CAC/PIV) certificate and forwards it to
  Keycloak in the `ssl-client-cert` header.
- `stigman` communicates with `keycloak` and `mysql` over the container network.
- A browser connects to `nginx` over HTTPS and reaches `stigman` and `keycloak`.

This general architecture can be implemented with a wide range of technologies; this
example uses a simple docker-compose orchestration.

## Requirements

- Recent Windows, Linux, or macOS
- `docker` and `docker compose`
- Chrome, Edge, or Firefox
- (Optional) a configured CAC/PIV reader, only if you want to try smartcard login

The example uses a server certificate issued to `localhost` and signed by a demo CA
named `demoCA`. For the browser to trust it, temporarily import
[`certs/ca/demoCA.crt`](certs/ca/demoCA.crt) into your browser/OS trusted roots.

> How you do this varies by OS and browser. On Windows, import it into "Trusted Root
> Certification Authorities". **Remove it when you are finished.**

## Fetching the example files

Either clone this repository with `git`, or download a ZIP using the green **Code**
button above and extract it. Then change into the directory.

## Starting the orchestration

```
docker compose up
```

Container images are downloaded on first run. The orchestration has successfully
bootstrapped when the STIG Manager API prints a `started` message like:

```
{"date":"...","level":3,"component":"index","type":"started","data":{"durationS":...,"port":54000,"api":"/api","client":"/","documentation":"/docs"}}
```

## Authenticating to STIG Manager

Navigate to:

```
https://localhost/stigman/
```

The web app redirects you to Keycloak to authenticate.

### Username / password (with self-registration)

On the login page, click **Register** to create an account, then sign in. So you can try
every part of the system, this demo realm makes every registered user a full admin (see
[Privileges](#privileges-roles)).

### Optional: CAC/PIV smartcard login

If your browser presents a client certificate that nginx can validate:

- **If the certificate's Common Name (CN) already matches a Keycloak username**, you are
  signed in automatically (single sign-on).
- **If it does not match any account yet**, the flow falls through to the normal login
  page (with a Register link). Register an account using your certificate's **CN** as the
  username. After registering once, your certificate logs you in automatically every time
  after.

To find your CN, open the sample's **landing page at <https://localhost/>** — it displays
your certificate's CN (and full subject) for easy copy-paste. nginx extracts it from the
client certificate it already validated; the login and **Register** pages also link to it.

> **First-time CAC note:** when your card doesn't match an account yet, you land on the
> login page with an "Invalid user" warning shown directly above the "First time signing
> in with a CAC/PIV smartcard?" notice — this is expected. Follow that notice: register
> with your CN (shown on <https://localhost/>), and your card signs you in automatically
> from then on.

### Keycloak admin console

```
https://localhost/kc/admin
```

Login with `admin` / `Pa55w0rd` (set by `KC_BOOTSTRAP_ADMIN_USERNAME` /
`KC_BOOTSTRAP_ADMIN_PASSWORD` in `docker-compose.yml`).

## Ending the orchestration

```
docker compose down
```

To also wipe the database and start fresh (the `-v` removes the MySQL data volume):

```
docker compose down -v && docker compose up
```

## Configurations

### nginx — [`nginx/nginx.conf`](nginx/nginx.conf)

nginx terminates TLS and reverse-proxies `/stigman/` and `/kc/`. It forwards the
`X-Forwarded-*` headers Keycloak needs (see `KC_PROXY_HEADERS` below) and forwards any
client certificate to Keycloak in the `ssl-client-cert` header.

Client-certificate verification is set to `optional`, so the sample works with or
without a smartcard. To **require** a CAC/PIV certificate on the Keycloak auth endpoint
(mandatory mTLS, returning HTTP `496` otherwise), uncomment the `if ($return_unauthorized)`
line and the two `map` blocks noted in the config.

The CA bundle used to validate client certs is mounted from
[`certs/dod/DoD_Root_CAs.pem`](certs/dod/DoD_Root_CAs.pem). Replace it with your own
CA(s) as needed.

A `map` extracts the certificate CN (`$client_cn`) from the client cert's subject so the
static landing page ([`nginx/index.html`](nginx/index.html), served with SSI) can show
the user their CN — handy for self-registration.

### STIG Manager

Behind a reverse proxy, the browser and the API reach Keycloak at different URLs (the
browser through the proxy, the API directly on the container network). STIG Manager lets
you configure those two OIDC provider URLs separately:

| Variable | Purpose | Value in this sample |
|----------|---------|----------------------|
| `STIGMAN_OIDC_PROVIDER` | Back channel (API → Keycloak, container network) | `http://keycloak:8080/realms/stigman` |
| `STIGMAN_CLIENT_OIDC_PROVIDER` | Front channel (browser → Keycloak via the proxy) | `https://localhost/kc/realms/stigman` |
| `STIGMAN_CLIENT_ID` | OAuth clientId the web app sends; must match the realm's Client ID | `stig-manager` |

> If `STIGMAN_CLIENT_OIDC_PROVIDER` is unset it defaults to `STIGMAN_OIDC_PROVIDER`. A
> healthy API/back channel does **not** prove the front channel is correct — the browser
> must be able to reach the front-channel URL and find the client there.

See the [STIG Manager Environment Variables](https://stig-manager.readthedocs.io/) docs
for the full list.

### Keycloak — reverse proxy on a subpath

The Keycloak settings that make it work behind the proxy on `/kc` (for **Keycloak 26**):

| Variable | Purpose |
|----------|---------|
| `KC_PROXY_HEADERS=xforwarded` | Trust nginx's `X-Forwarded-*` headers (edge TLS termination) |
| `KC_HTTP_ENABLED=true` | Required when TLS terminates at the proxy |
| `KC_HOSTNAME=https://localhost/kc` | Public base URL **including** the subpath; Keycloak builds all endpoint URLs from this |
| `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` | Resolve back-channel URLs (e.g. `jwks_uri`) from the request, so the API reaching Keycloak at `http://keycloak:8080` gets internal endpoints instead of the public `https://localhost/kc` ones. Without this the API fails OIDC discovery with `ECONNREFUSED`. |
| `KC_SPI_X509CERT_LOOKUP_PROVIDER=nginx` | Read the client cert forwarded by nginx (for CAC/PIV) |
| `KC_SPI_X509CERT_LOOKUP_NGINX_SSL_CLIENT_CERT=SSL-CLIENT-CERT` | Header name carrying the client cert |

> See the Keycloak [reverse proxy](https://www.keycloak.org/server/reverseproxy) and
> [hostname](https://www.keycloak.org/server/hostname) guides for details.

#### Authentication flow / CAC

The realm's browser flow (`X.509 Browser`) is the stock Keycloak browser flow with the
built-in **X509/Validate Username Form** added as an alternative above the
username/password forms. A validated certificate whose CN matches a user logs in; any
other case falls through to the username/password + Register form.

- **To disable CAC entirely:** set the X.509 execution to `DISABLED` in the realm's
  authentication flow (or change the realm Browser flow back to the built-in `browser`).
- **To require CAC:** see the nginx note above to enforce mTLS.

#### Privileges (roles)

STIG Manager reads two privilege roles from the token: `admin` and `create_collection`.
To make it easy to exercise the whole system, this demo realm's default role
(`default-roles-stigman`) grants **both** to every user — so anyone who registers is a
full admin. That's a demo convenience, not a recommendation: for a real deployment we'd
suggest granting `create_collection` by default and assigning `admin` per-user. Change it
either way by editing `default-roles-stigman` and assigning roles in the Keycloak admin
console (Users → Role mapping).

### Going beyond localhost

To make this reachable at a real hostname, the main changes are:

- **TLS certificate** — replace the demo `localhost` cert in [`certs/localhost/`](certs/localhost/)
  with one issued for your hostname by a CA your clients already trust, and update the
  `nginx` cert/key mounts in `docker-compose.yml`. (Then users no longer import `demoCA`.)
- **nginx** — set `server_name` in [`nginx/nginx.conf`](nginx/nginx.conf) to your hostname,
  and make sure DNS and your firewall route `443` to the host.
- **Keycloak public URL** — set `KC_HOSTNAME=https://your.host/kc`.
- **STIG Manager front channel** — set `STIGMAN_CLIENT_OIDC_PROVIDER=https://your.host/kc/realms/stigman`.
  The back channel (`STIGMAN_OIDC_PROVIDER`) stays on the internal container URL.
- **Client redirect URIs / web origins** — the demo `stig-manager` client uses wildcards;
  restrict its Valid Redirect URIs and Web Origins to your host.
- **Change the demo secrets** — the admin password (`KC_BOOTSTRAP_ADMIN_PASSWORD`), MySQL
  passwords, and any client secrets.
- **Persist Keycloak** — the demo imports the realm into an ephemeral H2 database on each
  start; back it with a real database and manage the realm directly instead of re-importing.
- **Review per your security policy** — default roles, token lifespans, registration,
  email verification (SMTP), CAC enforcement, etc.

## Updating DoD certificates

If you use the DoD PKI bundle for CAC validation:

1. Download the current DoD PKI CA certificate bundle (PKCS#7) from the
   [DoD Cyber Exchange PKI-PKE page](https://public.cyber.mil/pki-pke/)
2. Extract the PKCS#7 file
3. Run [`certs/dod/p7b-convert.sh`](certs/dod/p7b-convert.sh) to convert it to PEM

## Notes

### To clear a Chrome HSTS entry (for localhost)

After connecting to `https://localhost`, Chrome may refuse plain HTTP to `localhost`.

- `chrome://net-internals/#hsts` — delete domain security policies
- `chrome://settings/clearBrowserData` — clear cached images and files

## Community Solutions

The following repos are not maintained or tested by the STIG Manager team, but offer
alternate STIG Manager deployment configurations shared by the community:

- [@jeremyatourville/stigman-orchestration](https://github.com/jeremyatourville/stigman-orchestration) — a docker-compose orchestration using a reverse proxy and username/password authentication.

If you have a solution you'd like to share, open a pull request to add it to this list!

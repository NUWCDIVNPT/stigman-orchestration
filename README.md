# STIG Manager with Proxy

## Limitations of this example

This is an example orchestration for deploying STIG Manager with support for user authentication incorporating the U.S. Department of Defense Common Access Card (CAC). **The example supports connections to and from `localhost` only and is NOT intended for production use.**

## General architecture

![Keycloak native diagram](diagrams/kc-reverse-1.svg)

- `nginx` executes a TLS stack with client certificate verification and listens on a front channel HTTPS port.
- `nginx` proxies traffic to `stigman` and `keycloak` which are listening on back channel HTTP ports.
- `stigman` communicates with `keycloak` and `mysql` using their back channel ports.
<!-- - `browser with CAC` connects to `nginx` on the front channel HTTPS port and requests resources from `stigman` and `keycloak`. -->

This general architecture can be implemented with a wide range of technologies, from bare-metal deployments to complex containerized orchestrations. The example uses a simple docker-compose orchestration. 

## Requirements for running the example

- Recent Windows, Linux, or macOS
<!-- - CAC reader configured for your OS -->
- docker
- docker-compose
- Chrome, Edge, or Firefox browser

The example uses a server certificate issued to the host `localhost` and signed by a CA named `demoCA`. For the example to work, you must (temporarily) import trust in your browser for the `demoCA` certificate, found at [`certs/ca/demoCA.crt`](certs/ca/demoCA.crt).

> How you do this varies across operating systems and browsers. For Windows, you import the certificate into "Trusted Root Certification Authorities". You should remove the certificate when finished running the orchestrations.

## Fetching the example files

You have two options:

- If you have `git` installed, clone this repository. Then change to the newly created directory.

- Download a ZIP of this repository using the green Code button above. Extract the archive to an appropriate directory and change to the newly extracted directory.
## Starting the orchestration

```
docker-compose up
```

The orchestration's container images will be downloaded if they are not already available on your system. How long this takes depends on your connection speed and registry performance. Once all container images are available, the orchestration will start.

The orchestration has successfully bootstrapped when you see a `started` message like this from the STIG Manager API:

```
{"date":"2022-10-01T18:04:26.734Z","level":3,"component":"index","type":"started","data":{"durationS":21.180474449,"port":54000,"api":"/api","client":"/","documentation":"/docs"}}
```

## Authenticating to STIG Manager with Demo Keycloak

Once STIG Manager has started, navigate your browser to:

```
https://localhost/stigman/
```

This Keycloak container has preconfigured users. Usernames and passwords available here: [https://hub.docker.com/r/nuwcdivnpt/stig-manager-auth](https://hub.docker.com/r/nuwcdivnpt/stig-manager-auth)

You can access the Keycloak admin pages by navigating to:

```
https://localhost/kc/admin
```

Login with the credentials `admin/Pa55w0rd`


## Ending the orchestration

Type `Ctrl-C` to end the orchestration, followed by:

```
docker-compose down
```

> After using Chrome to HTTPS connect to `https://localhost`, you may find Chrome will no longer make HTTP connections to `http://localhost:[ANY_PORT]`. Once you're finished with the example, see [this note](#to-clear-chrome-hsts-entry-for-localhost-perhaps) for how to remedy this.

## Configurations

### Nginx

Nginx is configured to listen on port 443 for HTTPS connections, and forward them to the API and Auth containers. The server certificate is `certs/server/localhost.crt` and the private key is `certs/server/localhost.key`. The CA certificate is `certs/ca/demoCA.crt`.

<!-- Client certificate authentication is **required** for access to the Keycloak authorization endpoint. Client certificate authentication is **optional** for API endpoints since access to the API is controlled by OAuth2 tokens. -->

<!-- Nginx requires a PEM file containing certificates for the DoD Root CA and Intermediate CAs used to sign CAC certificates. 

> The example provides the file `certs/dod/Certificates_PKCS7_v5.9_DoD.pem.pem` for this purpose, which is mounted to the Nginx container at `/etc/nginx/dod-certs.pem`

You can [review the file `nginx/nginx.conf`](nginx/nginx.conf). -->

### STIG Manager

The environment variables `STIGMAN_OIDC_PROVIDER` and `STIGMAN_CLIENT_OIDC_PROVIDER` are set to the Keycloak back channel and front channel realm URLs, respectively.

### Keycloak

[The Keycloak Guides](https://www.keycloak.org/guides) provide documentation on configuring Keycloak for many deployment scenarios including this example orchestration. 
#### Keycloak Authentication Flow

During startup, Keycloak imports a [realm configuration file](kc/stigman_realm.json) which includes the `X.509 Browser` Authentication Flow to support X.509 certificate mapping. [This Keycloak documentation](https://www.keycloak.org/docs/latest/server_admin/#_x509) describes how to configure authentication flows to include X.509 client certificates.


The example uses a custom provider [modified from this project](https://github.com/lscorcia/keycloak-cns-authenticator/) that extends the built-in X.509 authenticator. The custom provider will create a new user account if a certificate cannot be mapped to an existing account. The provider file is `kc/create-x509-user.jar` which is mounted to the Keycloak container at `/opt/keycloak/providers`.

#### Keycloak keystores

Keycloak behind Nginx requires a keystore that contains certificates for the DoD Root CA and Intermediate CAs used to sign CAC certificates. 

> The example provides the file `certs/dod/Certificates_PKCS7_v5.9_DoD.pem.p12` for this purpose, which is mounted to the Keycloak container at `/tmp/truststore.p12`

## Notes
### To clear Chrome HSTS entry (for localhost, perhaps)

`chrome://net-internals/#hsts` -  Delete domain security policies

`chrome://settings/clearBrowserData` - Cached images and files


## Alternate non-CAC Example

The [demo-auth-no-CAC branch of this repo](https://github.com/NUWCDIVNPT/stigman-orchestration/tree/demo-auth-no-CAC) demonstrates a configuration that does not require CAC or mutual TLS, and uses a Keycloak container with built-in usernames and passwords available here: [https://hub.docker.com/r/nuwcdivnpt/stig-manager-auth](https://hub.docker.com/r/nuwcdivnpt/stig-manager-auth)

## Community Solutions

The following repos are not maintained or tested by the STIG Manager team, but offer alternate STIG Manager deployment configurations that have been shared by the community:

- [@jeremyatourville/stigman-orchestration](https://github.com/jeremyatourville/stigman-orchestration) - A docker-compose orchestration that uses a reverse proxy and username/password authentication.

If you have a solution you'd like to share, you can open a pull request to add it to this list!

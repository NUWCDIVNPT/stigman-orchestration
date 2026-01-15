# Keycloak X509 Create User Authenticator

Custom Keycloak authenticator plugin that automatically creates users from X.509 client certificates (CAC/PIV).

## Overview

This plugin extends Keycloak's authentication capabilities to:

1. Extract the Common Name (CN) from a client X.509 certificate
2. Look up an existing user by that username
3. **Automatically create a new user** if one doesn't exist
4. Assign configurable roles to new users (`admin`, `create_collection`, both, or neither)
5. Show a confirmation page before completing authentication

This is designed for CAC (Common Access Card) authentication scenarios where users should be provisioned on first login.

## Requirements

- Java 17+
- Maven 3.6+
- Keycloak 26.x

## Building

```bash
cd kc-x509-create-user
mvn clean package
```

The JAR file will be created at `target/x509-create-user.jar`.

## Deployment

### Docker Compose

The `docker-compose.yml` in the parent directory is already configured to mount this JAR:

```yaml
keycloak:
  volumes:
    - ./kc-x509-create-user/target/x509-create-user.jar:/opt/keycloak/providers/x509-create-user.jar
```

### Manual Deployment

Copy the JAR to Keycloak's providers directory:

```bash
cp target/x509-create-user.jar /opt/keycloak/providers/
```

For non-dev mode, rebuild Keycloak:

```bash
/opt/keycloak/bin/kc.sh build
```

## Configuration

### Realm Authentication Flow

The authenticator is registered with provider ID `x509-create-user`. In your realm's authentication flow (e.g., "X.509 Browser"), add an execution with:

- **Authenticator**: X509 Certificate with Auto User Creation
- **Requirement**: ALTERNATIVE (or REQUIRED if X.509 is mandatory)

### X.509 Certificate Lookup

Keycloak must be configured to retrieve client certificates. For nginx reverse proxy:

```bash
KC_SPI_X509CERT_LOOKUP_PROVIDER=nginx
KC_SPI_X509CERT_LOOKUP_NGINX_SSL_CLIENT_CERT=SSL-CLIENT-CERT
```

### Role Assignment

After adding the authenticator to your flow, click the gear icon (⚙️) to configure role assignment for new users:

| Option | Description | Default |
|--------|-------------|---------|
| **Grant 'admin' role** | Assign the `admin` role to newly created users, granting administrative privileges | `true` |
| **Grant 'create_collection' role** | Assign the `create_collection` role to newly created users, allowing them to create new Collections | `true` |

**Demo mode:** Both options enabled (all users get full access)

**Production mode:** Disable both options, then manually assign roles to specific users via Keycloak Admin Console (Users → Role Mappings)

## How It Works

1. When a user accesses a protected resource, the browser sends the client certificate
2. nginx validates the certificate against the DoD PKI chain
3. nginx forwards the certificate to Keycloak via the `ssl-client-cert` header
4. This authenticator:
   - Retrieves the certificate via Keycloak's X509ClientCertificateLookup SPI
   - Extracts the CN from the certificate subject
   - Looks up a user by that username
   - Creates a new user if not found (with configured roles)
   - Shows a confirmation page ("Account Created" or "Welcome Back")
   - User clicks Continue to complete authentication

## Logging

The authenticator logs to the `org.stigman.keycloak.X509CreateUserAuthenticator` category:

- **INFO**: User creation, successful authentication
- **DEBUG**: Certificate processing details
- **WARN**: Missing CN, disabled users
- **ERROR**: Certificate retrieval failures

## Customization

The current implementation uses the certificate's CN as the username. To modify this behavior (e.g., use email, UPN, or apply transformations), edit the `extractCommonName()` method in `X509CreateUserAuthenticator.java`.

## License

Apache 2.0

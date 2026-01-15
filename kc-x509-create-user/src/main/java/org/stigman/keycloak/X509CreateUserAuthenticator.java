package org.stigman.keycloak;

import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;

import java.util.Map;
import org.keycloak.services.x509.X509ClientCertificateLookup;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;

import java.security.cert.X509Certificate;

/**
 * Custom Keycloak authenticator that authenticates users via X.509 client certificates
 * and automatically creates new users if they don't already exist.
 *
 * The authenticator extracts the Common Name (CN) from the certificate's subject
 * and uses it as the username. If no user exists with that username, a new user
 * is created and assigned roles based on the authenticator configuration.
 *
 * A confirmation page is shown to the user before completing authentication.
 */
public class X509CreateUserAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(X509CreateUserAuthenticator.class);

    // Auth session note keys
    private static final String AUTH_NOTE_IS_NEW_USER = "x509.isNewUser";
    private static final String AUTH_NOTE_USERNAME = "x509.username";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // Get the X509 certificate lookup provider configured for this Keycloak instance
        X509ClientCertificateLookup lookup = context.getSession()
                .getProvider(X509ClientCertificateLookup.class);

        if (lookup == null) {
            LOG.error("X509ClientCertificateLookup provider not found. Check Keycloak X509 configuration.");
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        X509Certificate[] certs;
        try {
            certs = lookup.getCertificateChain(context.getHttpRequest());
        } catch (Exception e) {
            LOG.error("Failed to retrieve certificate chain", e);
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        if (certs == null || certs.length == 0) {
            LOG.debug("No client certificate provided in request");
            context.attempted();
            return;
        }

        X509Certificate clientCert = certs[0];
        LOG.debugf("Processing certificate: %s", clientCert.getSubjectX500Principal().getName());

        // Extract Common Name from certificate subject
        String username = extractCommonName(clientCert);
        if (username == null || username.trim().isEmpty()) {
            LOG.warn("Could not extract CN from certificate subject: " +
                     clientCert.getSubjectX500Principal().getName());
            context.failure(AuthenticationFlowError.INVALID_USER);
            return;
        }

        username = username.trim();
        LOG.infof("Extracted username from certificate CN: %s", username);

        RealmModel realm = context.getRealm();
        KeycloakSession session = context.getSession();

        // Look up existing user by username
        UserModel user = session.users().getUserByUsername(realm, username);
        boolean isNewUser = false;

        if (user == null) {
            // Create new user
            LOG.infof("User '%s' not found, creating new user", username);
            try {
                user = session.users().addUser(realm, username);
                user.setEnabled(true);
                isNewUser = true;

                // Assign roles based on authenticator configuration
                assignConfiguredRoles(context, realm, user, username);

                LOG.infof("Successfully created user '%s'", username);
            } catch (Exception e) {
                LOG.error("Failed to create user: " + username, e);
                context.failure(AuthenticationFlowError.INTERNAL_ERROR);
                return;
            }
        } else {
            LOG.debugf("Found existing user '%s'", username);
        }

        // Check if user is enabled
        if (!user.isEnabled()) {
            LOG.warnf("User '%s' is disabled", username);
            context.failure(AuthenticationFlowError.USER_DISABLED);
            return;
        }

        // Set user in context
        context.setUser(user);

        // Store state in auth session for the confirmation page
        context.getAuthenticationSession().setAuthNote(AUTH_NOTE_IS_NEW_USER, String.valueOf(isNewUser));
        context.getAuthenticationSession().setAuthNote(AUTH_NOTE_USERNAME, username);

        // Show confirmation page
        Response challenge = context.form()
                .setAttribute("isNewUser", isNewUser)
                .setAttribute("username", username)
                .createForm("x509-confirm.ftl");
        context.challenge(challenge);
    }

    /**
     * Extracts the Common Name (CN) from an X.509 certificate's subject
     * using BouncyCastle's ASN.1 parsing.
     */
    private String extractCommonName(X509Certificate cert) {
        if (cert == null) {
            LOG.warn("Certificate is null");
            return null;
        }

        var principal = cert.getSubjectX500Principal();
        if (principal == null) {
            LOG.warn("Certificate subject principal is null");
            return null;
        }

        String dn = principal.getName();
        if (dn == null || dn.isEmpty()) {
            LOG.warn("Certificate distinguished name is null or empty");
            return null;
        }

        X500Name x500name = new X500Name(dn);
        RDN[] rdns = x500name.getRDNs(BCStyle.CN);
        if (rdns == null || rdns.length == 0) {
            LOG.debug("No CN found in certificate subject");
            return null;
        }

        var firstRdn = rdns[0].getFirst();
        if (firstRdn == null || firstRdn.getValue() == null) {
            LOG.warn("CN RDN value is null");
            return null;
        }

        return IETFUtils.valueToString(firstRdn.getValue());
    }

    /**
     * Assigns roles to a newly created user based on authenticator configuration.
     */
    private void assignConfiguredRoles(AuthenticationFlowContext context, RealmModel realm,
                                       UserModel user, String username) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null) {
            LOG.debug("No authenticator config found, skipping role assignment");
            return;
        }

        Map<String, String> configMap = config.getConfig();
        if (configMap == null) {
            return;
        }

        // Check if admin role should be granted
        if ("true".equals(configMap.get(X509CreateUserAuthenticatorFactory.CONFIG_GRANT_ADMIN))) {
            RoleModel adminRole = realm.getRole("admin");
            if (adminRole != null) {
                user.grantRole(adminRole);
                LOG.debugf("Assigned 'admin' role to user '%s'", username);
            } else {
                LOG.warn("'admin' role not found in realm, cannot assign to user");
            }
        }

        // Check if create_collection role should be granted
        if ("true".equals(configMap.get(X509CreateUserAuthenticatorFactory.CONFIG_GRANT_CREATE_COLLECTION))) {
            RoleModel createCollectionRole = realm.getRole("create_collection");
            if (createCollectionRole != null) {
                user.grantRole(createCollectionRole);
                LOG.debugf("Assigned 'create_collection' role to user '%s'", username);
            } else {
                LOG.warn("'create_collection' role not found in realm, cannot assign to user");
            }
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        String username = context.getAuthenticationSession().getAuthNote(AUTH_NOTE_USERNAME);
        LOG.infof("User confirmed authentication for '%s'", username);
        context.success();
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}

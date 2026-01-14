package org.stigman.keycloak;

import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.x509.X509ClientCertificateLookup;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom Keycloak authenticator that authenticates users via X.509 client certificates
 * and automatically creates new users if they don't already exist.
 *
 * The authenticator extracts the Common Name (CN) from the certificate's subject
 * and uses it as the username. If no user exists with that username, a new user
 * is created and assigned the realm's default roles.
 *
 * A confirmation page is shown to the user before completing authentication.
 */
public class X509CreateUserAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(X509CreateUserAuthenticator.class);

    // Pattern to extract CN from X.500 Distinguished Name
    // Handles both "CN=Name" and "CN=Name," formats, including quoted values
    private static final Pattern CN_PATTERN = Pattern.compile("CN=(?:\"([^\"]+)\"|([^,]+))", Pattern.CASE_INSENSITIVE);

    // Auth session note keys
    private static final String AUTH_NOTE_IS_NEW_USER = "x509.isNewUser";
    private static final String AUTH_NOTE_USERNAME = "x509.username";
    private static final String AUTH_NOTE_CERTIFICATE_CN = "x509.certificateCN";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // Get the X509 certificate lookup provider configured for this Keycloak instance
        // This respects KC_SPI_X509CERT_LOOKUP_PROVIDER setting (e.g., "nginx")
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

                // Assign default realm roles
                RoleModel defaultRole = realm.getDefaultRole();
                if (defaultRole != null) {
                    user.grantRole(defaultRole);
                    LOG.debugf("Assigned default role '%s' to user '%s'",
                              defaultRole.getName(), username);
                }

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
        context.getAuthenticationSession().setAuthNote(AUTH_NOTE_CERTIFICATE_CN, username);

        // Show confirmation page
        Response challenge = createConfirmationForm(context, isNewUser, username);
        context.challenge(challenge);
    }

    /**
     * Creates the confirmation form to show to the user.
     */
    private Response createConfirmationForm(AuthenticationFlowContext context, boolean isNewUser, String username) {
        LoginFormsProvider form = context.form()
                .setAttribute("isNewUser", isNewUser)
                .setAttribute("username", username)
                .setAttribute("certificateCN", username);

        return form.createForm("x509-confirm.ftl");
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // User clicked Continue on the confirmation form
        String username = context.getAuthenticationSession().getAuthNote(AUTH_NOTE_USERNAME);
        LOG.infof("User confirmed authentication for '%s'", username);

        // Complete authentication
        context.success();
        LOG.infof("Successfully authenticated user '%s' via X.509 certificate", username);
    }

    /**
     * Extracts the Common Name (CN) from an X.509 certificate's subject.
     *
     * @param cert The X.509 certificate
     * @return The CN value, or null if not found
     */
    private String extractCommonName(X509Certificate cert) {
        X500Principal principal = cert.getSubjectX500Principal();
        String dn = principal.getName(X500Principal.RFC2253);

        Matcher matcher = CN_PATTERN.matcher(dn);
        if (matcher.find()) {
            // Group 1 is quoted value, Group 2 is unquoted value
            String cn = matcher.group(1);
            if (cn == null) {
                cn = matcher.group(2);
            }
            return cn;
        }

        return null;
    }

    @Override
    public boolean requiresUser() {
        // We find/create the user ourselves
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        // Always configured - no user-specific setup required
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions for X.509 authentication
    }

    @Override
    public void close() {
        // Nothing to clean up
    }
}

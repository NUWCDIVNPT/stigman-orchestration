package org.stigman.keycloak;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.Collections;
import java.util.List;

/**
 * Factory for creating X509CreateUserAuthenticator instances.
 *
 * This factory registers the authenticator with Keycloak's authentication SPI,
 * making it available in the Admin Console under Authentication > Flows.
 */
public class X509CreateUserAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "x509-create-user";

    private static final X509CreateUserAuthenticator SINGLETON = new X509CreateUserAuthenticator();

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "X509 Certificate with Auto User Creation";
    }

    @Override
    public String getHelpText() {
        return "Authenticates users via X.509 client certificates (e.g., CAC/PIV). " +
               "Automatically creates a new user account using the certificate's CN " +
               "if one doesn't exist. New users are assigned the realm's default roles. " +
               "Shows a confirmation page before completing login.";
    }

    @Override
    public String getReferenceCategory() {
        return "x509";
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[] {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Collections.emptyList();
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}

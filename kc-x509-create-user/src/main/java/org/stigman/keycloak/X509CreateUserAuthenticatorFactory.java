package org.stigman.keycloak;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

/**
 * Factory for creating X509CreateUserAuthenticator instances.
 *
 * This factory registers the authenticator with Keycloak's authentication SPI,
 * making it available in the Admin Console under Authentication > Flows.
 */
public class X509CreateUserAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "x509-create-user";

    // Configuration property names
    public static final String CONFIG_GRANT_ADMIN = "grantAdminRole";
    public static final String CONFIG_GRANT_CREATE_COLLECTION = "grantCreateCollectionRole";

    private static final X509CreateUserAuthenticator SINGLETON = new X509CreateUserAuthenticator();

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

    static {
        ProviderConfigProperty grantAdmin = new ProviderConfigProperty();
        grantAdmin.setName(CONFIG_GRANT_ADMIN);
        grantAdmin.setLabel("Grant 'admin' role");
        grantAdmin.setHelpText("Assign the 'admin' role to newly created users, granting administrative privileges.");
        grantAdmin.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        grantAdmin.setDefaultValue("true");

        ProviderConfigProperty grantCreateCollection = new ProviderConfigProperty();
        grantCreateCollection.setName(CONFIG_GRANT_CREATE_COLLECTION);
        grantCreateCollection.setLabel("Grant 'create_collection' role");
        grantCreateCollection.setHelpText("Assign the 'create_collection' role to newly created users, allowing them to create new Collections.");
        grantCreateCollection.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        grantCreateCollection.setDefaultValue("true");

        CONFIG_PROPERTIES = List.of(grantAdmin, grantCreateCollection);
    }

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
               "if one doesn't exist. New users can be assigned configurable roles. " +
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
        return true;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
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

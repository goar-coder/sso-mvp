package com.empresa.spi;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class D1TokenAuthenticator implements Authenticator {

    private static final String TOKEN_PARAM = "d1_token";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // Get token from query parameters first
        System.err.println("DEBUG-D1: !!! MÉTODO AUTHENTICATE INVOCADO !!!");
        String token = null;
        
        // Try to get from URL query parameters
        if (context.getHttpRequest().getUri().getQueryParameters().containsKey(TOKEN_PARAM)) {
            token = context.getHttpRequest().getUri().getQueryParameters().getFirst(TOKEN_PARAM);
            System.err.println("DEBUG-D1: Token encontrado en parámetros de URI: " + token);
        }
        
        // If not found, try form data (POST parameters)
        if (token == null && context.getHttpRequest().getDecodedFormParameters() != null) {
            token = context.getHttpRequest().getDecodedFormParameters().getFirst(TOKEN_PARAM);
            System.err.println("DEBUG-D1: Token encontrado en parámetros de formulario: " + token);
        }

        if (token == null || token.trim().isEmpty()) {
            System.err.println("DEBUG-D1: No se encontró token.");
            
            // NOTA: Es obligatorio llamar a un método del context antes del return
            context.attempted(); 
            
            System.err.println("DEBUG-D1: Se llamó a context.attempted(), pasando al siguiente paso...");
            return; 
        }

        // System.out.println("D1TokenAuthenticator: Processing token: " + token);

        // Get the D1 API client configuration
        // En una implementación real, obtendrías esto de la configuración del realm
        String baseUrl = "http://d1:8001"; // Puerto corregido: 8001
        String apiKey = "internal-api-key-super-secret-mvp"; // API key correcta

        D1ApiClient apiClient = new D1ApiClient(baseUrl, apiKey);

        try {
            D1UserData userData = apiClient.verifyToken(token);

            if (userData == null) {
                System.out.println("D1TokenAuthenticator: Token validation failed");
                context.failure(org.keycloak.authentication.AuthenticationFlowError.INVALID_CREDENTIALS);
                return;
            }

            System.out.println("D1TokenAuthenticator: Token valid for user: " + userData.getUsername());

            // Find or create user through the existing UserStorageProvider
            UserModel user = findOrCreateUser(context, userData);

            if (user == null) {
                System.out.println("D1TokenAuthenticator: Failed to find/create user");
                context.failure(org.keycloak.authentication.AuthenticationFlowError.INVALID_USER);
                return;
            }

            System.out.println("D1TokenAuthenticator: Authentication successful for user: " + user.getUsername());
            System.out.println("D1TokenAuthenticator: Authentication successful for user: " + user.getUsername());
            System.out.println("D1TokenAuthenticator: ¿El usuario está habilitado?: " + user.isEnabled());
            context.setUser(user);
            context.success();

        } catch (Exception e) {
            System.out.println("D1TokenAuthenticator: Exception during authentication: " + e.getMessage());
            e.printStackTrace();
            context.failure(org.keycloak.authentication.AuthenticationFlowError.GENERIC_AUTHENTICATION_ERROR);
        }
    }

    private UserModel findOrCreateUser(AuthenticationFlowContext context, D1UserData userData) {
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        // Look for existing user by username
        System.out.println("D1TokenAuthenticator: Buscando usuario local: " + userData.getUsername());
        UserModel user = session.users().getUserByUsername(realm, userData.getUsername());

        if (user == null) {
            // Create new user
            System.out.println("D1TokenAuthenticator: Usuario no encontrado, creando nuevo usuario local...");
            user = session.users().addUser(realm, userData.getUsername());
            System.out.println("D1TokenAuthenticator: Usuario creado con ID: " + user.getId());
        } else {
            System.out.println("D1TokenAuthenticator: Usuario encontrado con ID: " + user.getId() + " - Service Provider ID: " + user.getFederationLink());
        }

        
        // SIEMPRE actualizar estos campos (new o existing user)
        user.setEnabled(userData.isActive());
        user.setEmail(userData.getEmail());
        user.setFirstName(userData.getFirstName());
        user.setLastName(userData.getLastName());

        return user;
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // Not used in this implementation
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
        // No required actions needed
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
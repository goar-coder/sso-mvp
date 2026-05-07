package com.empresa.spi;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class D1TokenAuthenticator implements Authenticator {

    private static final String TOKEN_PARAM = "d1_token";
    private static int instanceCounter = 0;
    private final int instanceId;

    public D1TokenAuthenticator() {
        this.instanceId = ++instanceCounter;
        System.err.println("DEBUG-D1: [CONSTRUCTOR] D1TokenAuthenticator instancia #" + this.instanceId + " creada");
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // Get token from query parameters first
        System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] INICIO - método authenticate() invocado");
        System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] Request URI: " + context.getHttpRequest().getUri());
        String token = null;
        
        // Try to get from URL query parameters
        if (context.getHttpRequest().getUri().getQueryParameters().containsKey(TOKEN_PARAM)) {
            token = context.getHttpRequest().getUri().getQueryParameters().getFirst(TOKEN_PARAM);
            System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] Token encontrado en parámetros de URI: " + token);
        }
        
        // If not found, try form data (POST parameters)
        if (token == null && context.getHttpRequest().getDecodedFormParameters() != null) {
            if (context.getHttpRequest().getDecodedFormParameters().containsKey(TOKEN_PARAM)) {
                token = context.getHttpRequest().getDecodedFormParameters().getFirst(TOKEN_PARAM);
                System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] Token encontrado en parámetros de formulario: " + token);
            } else {
                System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] No hay parámetro d1_token en formulario");
            }
        } else if (token == null) {
            System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] DecodedFormParameters es null");
        }

        if (token == null || token.trim().isEmpty()) {
            System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] No se encontró token válido. Llamando context.attempted()");
            
            // NOTA: Es obligatorio llamar a un método del context antes del return
            context.attempted(); 
            
            System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] context.attempted() llamado, retornando para permitir siguiente step");
            return; 
        }

        System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] Procesando token: " + token);

        // Get the D1 API client configuration
        // En una implementación real, obtendrías esto de la configuración del realm
        String baseUrl = "http://d1:8001"; // Puerto corregido: 8001
        String apiKey = "internal-api-key-super-secret-mvp"; // API key correcta

        D1ApiClient apiClient = new D1ApiClient(baseUrl, apiKey);

        try {
            System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] Verificando token contra D1...");
            D1UserData userData = apiClient.verifyToken(token);

            if (userData == null) {
                System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] Validación de token fallida - userData es null");
                context.failure(org.keycloak.authentication.AuthenticationFlowError.INVALID_CREDENTIALS);
                return;
            }

            System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] Token válido para usuario: " + userData.getUsername());

            // Find or create user through the existing UserStorageProvider
            UserModel user = findOrCreateUser(context, userData);

            if (user == null) {
                System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] No se pudo encontrar/crear usuario");
                context.failure(org.keycloak.authentication.AuthenticationFlowError.INVALID_USER);
                return;
            }

            System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] Autenticación exitosa para usuario: " + user.getUsername());
            System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] Usuario habilitado: " + user.isEnabled());
            context.setUser(user);
            context.success();
            System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] context.success() llamado");

        } catch (Exception e) {
            System.err.println("DEBUG-D1: [AUTHENTICATE #" + this.instanceId + "] Excepción durante autenticación: " + e.getMessage());
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
        System.err.println("DEBUG-D1: [ACTION] action() fue llamado");
        // Not used in this implementation
    }

    @Override
    public boolean requiresUser() {
        System.err.println("DEBUG-D1: [REQUIRES_USER] requiresUser() devolviendo false");
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        System.err.println("DEBUG-D1: [CONFIGURED_FOR] configuredFor() devolviendo true");
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        System.err.println("DEBUG-D1: [SET_REQUIRED_ACTIONS] setRequiredActions() fue llamado");
        // No required actions needed
    }

    @Override
    public void close() {
        System.err.println("DEBUG-D1: [CLOSE] close() fue llamado");
        // Nothing to close
    }
}
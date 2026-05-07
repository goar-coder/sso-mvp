package com.empresa.spi;

import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.*;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserRegistrationProvider;

import java.util.stream.Stream;

public class D1UserStorageProvider implements
        UserStorageProvider,
        UserLookupProvider,
        UserRegistrationProvider,
        CredentialInputValidator {

    private final KeycloakSession session;
    private final ComponentModel  model;
    private final D1ApiClient     apiClient;
    private final RealmModel      realm;

    public D1UserStorageProvider(KeycloakSession session, ComponentModel model,
                                  D1ApiClient apiClient, RealmModel realm) {
        this.session   = session;
        this.model     = model;
        this.apiClient = apiClient;
        this.realm     = realm;
        System.err.println("DEBUG-D1: [USER_STORAGE_PROVIDER] Constructor invocado - instancia creada");
    }

    // ── UserLookupProvider ────────────────────────────────────────────

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        System.err.println("DEBUG-D1: [USER_LOOKUP] getUserByUsername(" + username + ") invocado");
        D1UserData data = apiClient.findByUsername(username);
        if (data == null) {
            System.err.println("DEBUG-D1: [USER_LOOKUP] Usuario NO encontrado en D1 para: " + username);
            return null;
        }
        System.err.println("DEBUG-D1: [USER_LOOKUP] Usuario encontrado en D1: " + data.getUsername() + ", activo: " + data.isActive());
        return new D1UserAdapter(session, realm, model, data);
    }

    private UserModel createUserFromD1Data(RealmModel realm, D1UserData data) {
        UserModel user = session.users().addUser(realm, data.getUsername());
        user.setEnabled(data.isActive());
        user.setEmail(data.getEmail());
        user.setFirstName(data.getFirstName());
        user.setLastName(data.getLastName());
        // No set password - let credential validator handle it
        return user;
    }

    private void updateUserFromD1Data(UserModel user, D1UserData data) {
        user.setEnabled(data.isActive());
        user.setEmail(data.getEmail());
        user.setFirstName(data.getFirstName());
        user.setLastName(data.getLastName());
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        String externalId = StorageId.externalId(id);
        System.err.println("DEBUG-D1: [USER_LOOKUP] getUserById(" + id + ") -> externalId: " + externalId);
        D1UserData data = apiClient.findById(externalId);
        if (data == null) {
            System.err.println("DEBUG-D1: [USER_LOOKUP] Usuario NO encontrado en D1 por ID: " + externalId);
            return null;
        }
        System.err.println("DEBUG-D1: [USER_LOOKUP] Usuario encontrado en D1 por ID, username: " + data.getUsername());
        
        // Try to find user in local Keycloak database first
        UserModel localUser = session.users().getUserByUsername(realm, data.getUsername());
        if (localUser == null) {
            // User doesn't exist locally, create it
            System.err.println("DEBUG-D1: [USER_LOOKUP] Usuario no existe en BD local, creando...");
            localUser = createUserFromD1Data(realm, data);
            System.err.println("DEBUG-D1: [USER_LOOKUP] Usuario creado localmente con ID: " + localUser.getId());
        } else {
            // User exists locally, update their info
            System.err.println("DEBUG-D1: [USER_LOOKUP] Usuario existe en BD local, actualizando info...");
            updateUserFromD1Data(localUser, data);
        }
        
        return localUser;
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        return null; // no implementado en MVP
    }

    // ── UserRegistrationProvider ──────────────────────────────────────
    // No registramos nuevos usuarios desde Keycloak, solo los traemos de D1
    @Override
    public UserModel addUser(RealmModel realm, String username) {
        // No permitir registro de nuevos usuarios desde Keycloak
        // Solo importar desde D1
        return null;
    }

    @Override
    public boolean removeUser(RealmModel realm, UserModel user) {
        return false;
    }

    // ── CredentialInputValidator ──────────────────────────────────────

    @Override
    public boolean supportsCredentialType(String credentialType) {
        System.err.println("DEBUG-D1: [CREDENTIAL_VALIDATOR] supportsCredentialType(" + credentialType + ") invocado");
        boolean supports = PasswordCredentialModel.TYPE.equals(credentialType);
        System.err.println("DEBUG-D1: [CREDENTIAL_VALIDATOR] supportsCredentialType resultado: " + supports);
        return supports;
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        System.err.println("DEBUG-D1: [CREDENTIAL_VALIDATOR] isConfiguredFor(user=" + user.getUsername() + ", credentialType=" + credentialType + ") invocado");
        boolean result = supportsCredentialType(credentialType);
        System.err.println("DEBUG-D1: [CREDENTIAL_VALIDATOR] isConfiguredFor resultado: " + result);
        return result;
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user,
                           CredentialInput credentialInput) {
        System.err.println("DEBUG-D1: [CREDENTIAL_VALIDATOR] isValid() INVOCADO para usuario: " + user.getUsername());
        String password = credentialInput.getChallengeResponse();
        System.err.println("DEBUG-D1: [CREDENTIAL_VALIDATOR] Password proporcionado: " + (password != null && !password.isEmpty()));
        
        D1UserData data = apiClient.verify(user.getUsername(), password);
        boolean valid = data != null;
        
        System.err.println("DEBUG-D1: [CREDENTIAL_VALIDATOR] Validación contra D1 - Resultado: " + valid);
        if (data != null) {
            System.err.println("DEBUG-D1: [CREDENTIAL_VALIDATOR] Usuario en D1: " + data.getUsername() + " | Activo: " + data.isActive());
        }
        
        return valid;
    }

    // ── UserStorageProvider ───────────────────────────────────────────

    @Override
    public void close() {}
}

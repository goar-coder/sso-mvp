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
        System.err.println("DEBUG-D1: !!! MÉTODO D1UserStorageProvider !!!");
    }

    // ── UserLookupProvider ────────────────────────────────────────────

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        System.out.println("D1UserStorageProvider.getUserByUsername: " + username);
        D1UserData data = apiClient.findByUsername(username);
        if (data == null) {
            System.out.println("D1UserStorageProvider.getUserByUsername: user not found in D1");
            return null;
        }
        System.out.println("D1UserStorageProvider.getUserByUsername: found user in D1, returning adapter");
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
        System.out.println("D1UserStorageProvider.getUserById: " + externalId);
        D1UserData data = apiClient.findById(externalId);
        if (data == null) {
            System.out.println("D1UserStorageProvider.getUserById: user not found in D1");
            return null;
        }
        System.out.println("D1UserStorageProvider.getUserById: found user in D1, username: " + data.getUsername());
        
        // Try to find user in local Keycloak database first
        UserModel localUser = session.users().getUserByUsername(realm, data.getUsername());
        if (localUser == null) {
            // User doesn't exist locally, create it
            System.out.println("D1UserStorageProvider.getUserById: user not in local DB, creating...");
            localUser = createUserFromD1Data(realm, data);
            System.out.println("D1UserStorageProvider.getUserById: user created locally with ID: " + localUser.getId());
        } else {
            // User exists locally, update their info
            System.out.println("D1UserStorageProvider.getUserById: user exists locally, updating...");
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
        return PasswordCredentialModel.TYPE.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user,
                           CredentialInput credentialInput) {
        String password = credentialInput.getChallengeResponse();
        System.out.println("D1UserStorageProvider.isValid: user=" + user.getUsername() + ", password_provided=" + (password != null));
        D1UserData data = apiClient.verify(user.getUsername(), password);
        boolean valid = data != null;
        System.out.println("D1UserStorageProvider.isValid: result=" + valid);
        return valid;
    }

    // ── UserStorageProvider ───────────────────────────────────────────

    @Override
    public void close() {}
}

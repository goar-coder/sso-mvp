package com.empresa.spi;

import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.*;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;

import java.util.stream.Stream;

public class D1UserStorageProvider implements
        UserStorageProvider,
        UserLookupProvider,
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
    }

    // ── UserLookupProvider ────────────────────────────────────────────

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        D1UserData data = apiClient.findByUsername(username);
        if (data == null) return null;
        return new D1UserAdapter(session, realm, model, data);
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        String username = StorageId.externalId(id);
        return getUserByUsername(realm, username);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        return null; // no implementado en MVP
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
        D1UserData data = apiClient.verify(user.getUsername(), password);
        return data != null;
    }

    // ── UserStorageProvider ───────────────────────────────────────────

    @Override
    public void close() {}
}

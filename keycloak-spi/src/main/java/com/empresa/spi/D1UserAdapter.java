package com.empresa.spi;

import org.keycloak.component.ComponentModel;
import org.keycloak.credential.UserCredentialManager;
// import org.keycloak.credential.LegacyUserCredentialManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapter;
import org.keycloak.models.UserModel.RequiredAction;


import java.util.List;
import java.util.stream.Stream;

public class D1UserAdapter extends AbstractUserAdapter {

    private final D1UserData data;
    private final ComponentModel componentModel;

    public D1UserAdapter(KeycloakSession session, RealmModel realm,
                         ComponentModel model, D1UserData data) {
        super(session, realm, model);
        this.data = data;
        this.componentModel = model;
    }

    @Override
    public String getId() {
        return StorageId.keycloakId(componentModel, data.getUsername()); // ✅ fix anterior
    }

    @Override public String getUsername()  { return data.getUsername(); }
    @Override public String getEmail()     { return data.getEmail(); }
    @Override public String getFirstName() { return data.getFirstName(); }
    @Override public String getLastName()  { return data.getLastName(); }
    @Override public boolean isEnabled()   { return data.isActive(); }

    @Override
    public Stream<RoleModel> getRealmRoleMappingsStream() {
        List<String> roles = data.getAppRoles();
        if (roles == null) return Stream.empty();
        return roles.stream()
                .map(roleName -> realm.getRole(roleName))
                .filter(role -> role != null);
    }

    @Override
    public Stream<RoleModel> getRoleMappingsStream() {
        return getRealmRoleMappingsStream();
    }

    @Override
    public SubjectCredentialManager credentialManager() {
        // return session.userCredentialManager().createStorageCredentialManager(session, realm, this);
        // return new LegacyUserCredentialManager(session, realm, this); 
        return new org.keycloak.credential.UserCredentialManager(session, realm, this);
        
    }


    // ── Solo lectura: ignorar cualquier intento de escritura ──────────

    @Override
    public void setSingleAttribute(String name, String value) {}

    @Override
    public void setAttribute(String name, List<String> values) {}

    @Override
    public void removeAttribute(String name) {}

    @Override
    public void setEmailVerified(boolean verified) {}

    @Override
    public void setEnabled(boolean enabled) {}

    @Override public void setUsername(String s)  {}
    @Override public void setEmail(String s)     {}
    @Override public void setFirstName(String s) {}
    @Override public void setLastName(String s)  {}

    @Override
    public void removeRequiredAction(String action) {}

    @Override
    public void removeRequiredAction(RequiredAction action) {}

    @Override
    public void addRequiredAction(String action) {}

    @Override
    public void addRequiredAction(RequiredAction action) {}

    @Override
    public Stream<String> getRequiredActionsStream() {
        return Stream.empty(); // nunca tiene required actions
    }
}
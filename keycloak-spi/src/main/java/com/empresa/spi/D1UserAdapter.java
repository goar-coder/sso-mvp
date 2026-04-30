package com.empresa.spi;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapter;

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
        return StorageId.keycloakId(componentModel, data.getId());
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
        throw new UnsupportedOperationException("Not supported for external users");
    }

    @Override public void setUsername(String s)  {}
    @Override public void setEmail(String s)     {}
    @Override public void setFirstName(String s) {}
    @Override public void setLastName(String s)  {}
}
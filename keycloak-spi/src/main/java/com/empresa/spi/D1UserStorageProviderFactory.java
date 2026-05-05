package com.empresa.spi;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.List;
import org.keycloak.provider.ProviderConfigProperty;

public class D1UserStorageProviderFactory
        implements UserStorageProviderFactory<D1UserStorageProvider> {

    public static final String PROVIDER_ID = "d1-user-storage";
    

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public D1UserStorageProvider create(KeycloakSession session, ComponentModel model) {
        System.err.println("DEBUG-D1: Factory creando D1UserStorageProvider");
        String d1Url = model.getConfig().getFirst("d1Url");
        String apiKey = model.getConfig().getFirst("apiKey");

        RealmModel realm = session.getContext().getRealm();
        D1ApiClient client = new D1ApiClient(d1Url, apiKey);

        return new D1UserStorageProvider(session, model, client, realm);
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        ProviderConfigProperty urlProp = new ProviderConfigProperty();
        urlProp.setName("d1Url");
        urlProp.setLabel("D1 Base URL");
        urlProp.setType(ProviderConfigProperty.STRING_TYPE);
        urlProp.setHelpText("URL base de D1, ej: http://d1:8001");

        ProviderConfigProperty keyProp = new ProviderConfigProperty();
        keyProp.setName("apiKey");
        keyProp.setLabel("Internal API Key");
        keyProp.setType(ProviderConfigProperty.PASSWORD);
        keyProp.setHelpText("Valor de X-Internal-Api-Key");

        return List.of(urlProp, keyProp);
    }

    @Override
    public String getHelpText() {
        return "Autentica usuarios contra la API interna de D1";
    }
}

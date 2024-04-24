package com.oracle.pic.networking.lvv.service.common;

import com.oracle.bmc.auth.tls.KeystoreGenerator;
import com.oracle.bmc.http.ClientConfigurator;
import java.security.KeyStore;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import lombok.Builder;

@Builder(builderClassName = "Builder", toBuilder = true)
public class RootCertAuthorityConfigurator implements ClientConfigurator {

    private static final KeystoreGenerator keystoreGenerator = new KeystoreGenerator();

    private String caPemPath;

    @Override
    public void customizeBuilder(ClientBuilder builder) {

        try {

            KeyStore trustStore = keystoreGenerator.createTrustStoreWithServerCa(caPemPath);
            builder.trustStore(trustStore);

        } catch (Exception e) {

            throw new IllegalArgumentException("Failed to override root CA for client", e);
        }
    }

    @Override
    public void customizeClient(Client client) {}
}

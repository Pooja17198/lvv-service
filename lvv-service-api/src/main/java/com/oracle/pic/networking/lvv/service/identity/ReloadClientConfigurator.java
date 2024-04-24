package com.oracle.pic.networking.lvv.service.identity;

import com.oracle.bmc.http.DefaultConfigurator;
import com.oracle.bmc.http.internal.ContentLengthFilter;
import com.oracle.bmc.internal.client.http.OracleConnectorProvider;
import com.oracle.pic.commons.ssl.DynamicSslContextProvider;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import lombok.RequiredArgsConstructor;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.RequestEntityProcessing;

@RequiredArgsConstructor
public class ReloadClientConfigurator extends DefaultConfigurator {
    private final DynamicSslContextProvider dynamicSslContextProvider;

    @Override
    public void customizeBuilder(ClientBuilder clientBuilder) {
        clientBuilder.sslContext(dynamicSslContextProvider.getSslContext());
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.connectorProvider(new OracleConnectorProvider(dynamicSslContextProvider));
        clientBuilder.withConfig(clientConfig);
    }

    @Override
    public void customizeClient(Client client) {
        // If you run into issues with POST/PUT calls, please override this method and add the below
        // client property
        client.property(
                ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.BUFFERED);

        // If you run into issues w.r.t Content-Length being already set when talking to auth
        // service, override this method and register the below Content-Length Filter
        client.register(new ContentLengthFilter());
    }
}

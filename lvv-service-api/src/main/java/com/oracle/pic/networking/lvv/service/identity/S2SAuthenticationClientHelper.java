package com.oracle.pic.networking.lvv.service.identity;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.auth.RefreshableFileBasedX509CertificateSupplier;
import com.oracle.bmc.auth.S2SAuthenticationDetailsProvider;
import com.oracle.bmc.auth.tls.TlsConfig;
import com.oracle.bmc.http.ClientConfigurator;
import com.oracle.bmc.retrier.RetryConfiguration;
import com.oracle.bmc.waiter.FixedTimeDelayStrategy;
import com.oracle.bmc.waiter.MaxAttemptsTerminationStrategy;
import com.oracle.pic.commons.client.authentication.X509CertificateChainSupplierImpl;
import com.oracle.pic.commons.ssl.DynamicSslContextProvider;
import com.oracle.pic.commons.ssl.DynamicSslContextProviderConfig;
import com.oracle.pic.identity.auth.AuthMetricsConstants;
import com.oracle.pic.identity.auth.AuthMetricsFactory;
import com.oracle.pic.identity.authentication.AuthServiceAuthenticationClient;
import com.oracle.pic.identity.authentication.ServiceAuthenticationClient;
import com.oracle.pic.identity.authentication.key.X509CertificateChainReloader;
import com.oracle.pic.networking.lvv.service.common.DynamicCertRotationHelper;
import com.oracle.pic.networking.lvv.service.common.RootCertAuthorityConfigurator;
import java.time.Duration;
import java.util.Collections;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

public class S2SAuthenticationClientHelper {
    private static final int MAX_API_RETRY_ON_FAILURE = 3;

    public static S2SAuthenticationDetailsProvider provideS2SAuthenticationClientConfig(
            IdentityConfiguration identityConfig) {

        return S2SAuthenticationClientHelper.getS2SAuthProvider(identityConfig);
    }

    public static S2SAuthenticationDetailsProvider getS2SAuthProvider(
            IdentityConfiguration identityConfiguration) {
        TlsConfig tlsConfig =
                DynamicCertRotationHelper.getCommonsTlsConfigFromIdentityConfig(
                        identityConfiguration);
        DynamicSslContextProviderConfig dynamicSslContextProviderConfig =
                new DynamicSslContextProviderConfig(
                        tlsConfig.getClientCertificatePath(), // leafCertPath
                        tlsConfig.getPrivateKeyPath(), // leafKeyPath
                        tlsConfig.getClientIntermediateCertificatePath(), // intermediateCertPath
                        tlsConfig.getCaBundle(), // rootCertPath
                        Duration.ofMinutes(5), // pollingInterval
                        "TLS"); // default ssl algorithm

        DynamicSslContextProvider dynamicSslContextProvider = new DynamicSslContextProvider();
        dynamicSslContextProvider.initialize(dynamicSslContextProviderConfig);
        ReloadClientConfigurator reloadClientConfigurator =
                new ReloadClientConfigurator(dynamicSslContextProvider);

        return S2SAuthenticationDetailsProvider.builder()
                .federationEndpoint(identityConfiguration.getAuthorizationEndpoint())
                .tenancyId(identityConfiguration.getServiceTenantOcid())
                .federationClientConfigurator(reloadClientConfigurator)
                .intermediateCertificateSuppliers(
                        Collections.singleton(
                                new RefreshableFileBasedX509CertificateSupplier(
                                        FileUtils.getFile(
                                                identityConfiguration
                                                        .getIntermediateCertificateFile()),
                                        null,
                                        null)))
                .leafCertificateSupplier(
                        new RefreshableFileBasedX509CertificateSupplier(
                                FileUtils.getFile(identityConfiguration.getCertificateFile()),
                                FileUtils.getFile(identityConfiguration.getPrivateKeyFile()),
                                null))
                .build();
    }

    public static ClientConfiguration getFixedDelayRetryClientConfiguration() {
        return ClientConfiguration.builder()
                .retryConfiguration(
                        RetryConfiguration.builder()
                                .delayStrategy(new FixedTimeDelayStrategy(300))
                                .terminationStrategy(
                                        new MaxAttemptsTerminationStrategy(
                                                MAX_API_RETRY_ON_FAILURE))
                                .build())
                .build();
    }

    public static ClientConfigurator getRootCaConfigurator(
            IdentityConfiguration identityConfiguration) {
        if (StringUtils.isNotBlank(identityConfiguration.getCaBundleFile())) {

            return RootCertAuthorityConfigurator.builder()
                    .caPemPath(identityConfiguration.getCaBundleFile())
                    .build();
        }
        return null;
    }

    public static ServiceAuthenticationClient serviceAuthenticationClientGenerator(
            IdentityConfiguration identityConfig) {
        ServiceAuthenticationClient serviceAuthenticationClient = null;

        try {
            X509CertificateChainSupplierImpl x509CertificateChainSupplier =
                    new X509CertificateChainSupplierImpl(
                            identityConfig.getCertificateFile(),
                            identityConfig.getPrivateKeyFile(),
                            /* privateKeyPassphrasePath= */ null,
                            identityConfig.getIntermediateCertificateFile());
            X509CertificateChainReloader certificateChainReloader =
                    (X509CertificateChainReloader) x509CertificateChainSupplier;

            TlsConfig tlsConfig =
                    DynamicCertRotationHelper.getCommonsTlsConfigFromIdentityConfig(identityConfig);
            DynamicSslContextProviderConfig dynamicSslContextProviderConfig =
                    new DynamicSslContextProviderConfig(
                            tlsConfig.getClientCertificatePath(),
                            tlsConfig.getPrivateKeyPath(),
                            tlsConfig.getClientIntermediateCertificatePath(),
                            tlsConfig.getCaBundle());

            serviceAuthenticationClient =
                    AuthServiceAuthenticationClient.builder()
                            .authServiceEndpoint(identityConfig.getAuthorizationEndpoint())
                            .certificateSupplier(x509CertificateChainSupplier)
                            .certificateReloader(certificateChainReloader)
                            .trustRootConfig(dynamicSslContextProviderConfig)
                            .globalBusinessUnit(identityConfig.getGlobalBusinessUnit())
                            .teamName(identityConfig.getTeamName())
                            .applicationName(identityConfig.getApplicationName())
                            .authMetrics(
                                    AuthMetricsFactory.getInstance(
                                            AuthMetricsConstants.TELEMETRY_LIB))
                            .build();

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format(
                            "Error loading S2S certs. %s %s %s",
                            identityConfig.getCertificateFile(),
                            identityConfig.getIntermediateCertificateFile(),
                            identityConfig.getPrivateKeyFile()),
                    e);
        }
        return serviceAuthenticationClient;
    }
}

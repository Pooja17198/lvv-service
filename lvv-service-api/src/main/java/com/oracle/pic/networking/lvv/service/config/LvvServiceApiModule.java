package com.oracle.pic.networking.lvv.service.config;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.monitoring.MonitoringClient;
import com.oracle.pic.commons.metrics.naming.FilteringNamingStrategy;
import com.oracle.pic.commons.metrics.naming.SimpleMetricsNamingStrategy;
import com.oracle.pic.commons.ssl.DynamicSslContextProviderConfig;
import com.oracle.pic.commons.util.Region;
import com.oracle.pic.identity.auth.AuthMetricsConstants;
import com.oracle.pic.identity.auth.AuthMetricsFactory;
import com.oracle.pic.identity.authentication.AuthServiceAuthenticationClient;
import com.oracle.pic.identity.authentication.ServiceAuthenticationClient;
import com.oracle.pic.identity.authentication.entities.X509FederationRequest;
import com.oracle.pic.identity.authentication.supplier.InstancePrincipalCertificateSupplier;
import com.oracle.pic.identity.authorization.sdk.AuthorizationClient;
import com.oracle.pic.kiev.DataStoreConfig;
import com.oracle.pic.kiev.DirectDbStoreConfig;
import com.oracle.pic.kiev.KaasStoreConfig;
import com.oracle.pic.kiev.mapping.MappedDataStore;
import com.oracle.pic.kiev.mapping.MappedHashBucket;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import com.oracle.pic.kiev.registry.data.ClientRegistryLocality;
import com.oracle.pic.networking.lvv.service.LvvServiceApi;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDConfig;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.dependencies.ncp.MockNcpClients;
import com.oracle.pic.networking.lvv.service.dependencies.ncp.NcpService;
import com.oracle.pic.networking.lvv.service.dependencies.ncp.NcpServiceConfiguration;
import com.oracle.pic.networking.lvv.service.health.LvvServiceApiDeepCheck;
import com.oracle.pic.networking.lvv.service.kiev.ConfigurationStore;
import com.oracle.pic.networking.lvv.service.kiev.DataStoreProvider;
import com.oracle.pic.networking.lvv.service.kiev.KievConfigurationStore;
import com.oracle.pic.networking.lvv.service.kiev.KievHashBucketProvider;
import com.oracle.pic.networking.lvv.service.kiev.KievManager;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItem;
import com.oracle.pic.networking.lvv.service.secret.FileBasedSecretRetriever;
import com.oracle.pic.networking.lvv.service.secret.SecretRetriever;
import com.oracle.pic.networking.lvv.service.secret.SecretRetrieverException;
import com.oracle.pic.networking.lvv.service.secret.SecretServiceBasedSecretRetriever;
import com.oracle.pic.networking.lvv.service.service.CablingTaskService;
import com.oracle.pic.networking.lvv.service.service.ProjectService;
import com.oracle.pic.networking.ncp.JobsClient;
import com.oracle.pic.telemetry.overlay.metrics.MetricsModules;
import com.oracle.pic.vault.MockAuthenticationDetailsProvider;
import com.oracle.pic.vault.VaultClient;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class LvvServiceApiModule extends AbstractModule {

    private static final int REFRESH_AUTH_TOKEN_BEFORE_SECONDS_TO_EXPIRE = 5;
    private static final String LOCAL = "dummyEndpoint";
    private final Region region;

    private final LvvServiceApiConfiguration config;

    public LvvServiceApiModule(LvvServiceApiConfiguration config) {
        this.region = config.getAvailabilityDomain().getRegion();
        this.config = config;
    }

    @Override
    protected void configure() {
        log.info("Binding");

        bind(NcpService.class).in(Singleton.class);
        bind(NcpServiceConfiguration.class).toInstance(config.getNcpServiceConfiguration());
        bind(LvvServiceApiConfiguration.class).toInstance(config);
        bind(AuthConfig.class).toInstance(config.getAuthConfig());
        bind(CablingTaskService.class).in(Singleton.class);
        bind(ProjectService.class).in(Singleton.class);
        for (Class<?> c : LvvServiceApi.RESOURCE_CLASSES) {
            bind(c).in(Singleton.class);
        }
        bind(LvvServiceApiDeepCheck.class).in(Singleton.class);
        bind(MappedDataStore.class).toProvider(DataStoreProvider.class);
        this.binder().requireExplicitBindings();
        install(
                new MetricsModules.Builder()
                        .config(config.getMetricsConfig())
                        .namingStrategy(
                                FilteringNamingStrategy.byLevel(
                                        new SimpleMetricsNamingStrategy(), 3))
                        .monitoringClient(getMonitoringClient())
                        .shouldOverrideMetricKeys(false)
                        .build());

        bindProjectBucket();
        bind(ProjectService.class).in(Singleton.class);
        bind(KievManager.class).in(Singleton.class);
    }

    private void bindProjectBucket() {
        KievHashBucketProvider<String, ProjectItem> kievHashBucketProvider =
                new KievHashBucketProvider<>(
                        "projectListBucket",
                        "Bucket which stores all project list",
                        String.class,
                        ProjectItem.class);
        bind(new TypeLiteral<MappedHashBucket<String, ProjectItem>>() {})
                .toProvider(kievHashBucketProvider);

        bind(new TypeLiteral<ConfigurationStore<String, ProjectItem>>() {})
                .to(new TypeLiteral<KievConfigurationStore<String, ProjectItem>>() {});
    }

    @Provides
    @Singleton
    public ServiceAuthenticationClient getServiceAuthenticationClient() {

        // Read this guide to learn about Using Instance Principal Certificates for S2S
        // https://confluence.oci.oraclecorp.com/pages/viewpage.action?spaceKey=OCIID&title=How+to+Integrate+with+S2S

        Preconditions.checkArgument(config.getAuthConfig() != null, "AuthConfig is null");
        final InstancePrincipalCertificateSupplier certificateSupplier;
        if (config.getStage().equals("DEVELOPMENT")
                && StringUtils.isNotBlank(config.getAuthConfig().getInstancePrincipalUrl())) {
            certificateSupplier =
                    new InstancePrincipalCertificateSupplier(
                            config.getAuthConfig().getInstancePrincipalUrl());
        } else {
            certificateSupplier = new InstancePrincipalCertificateSupplier();
        }

        // This is used in *overlay* hosts to contact identity, if you are in service enclave, set
        // to null
        DynamicSslContextProviderConfig dynamicSslConfig =
                new DynamicSslContextProviderConfig(
                        null, null, null, config.getAuthConfig().getDefaultTrustStorePath());

        // You can use region in overlay to autodetect the endpoint or use
        // .authServiceEndpoint(config.getAuthConfig().authServiceEndpoint) to specify it yourself.
        return AuthServiceAuthenticationClient.builder()
                .authServiceEndpoint(config.getAuthConfig().getAuthServiceEndpoint())
                .certificateSupplier(certificateSupplier)
                .expirationBufferInSeconds(REFRESH_AUTH_TOKEN_BEFORE_SECONDS_TO_EXPIRE)
                .purpose(X509FederationRequest.Purpose.SERVICE_PRINCIPAL)
                .trustRootConfig(dynamicSslConfig)
                .globalBusinessUnit(config.getAuthConfig().getGlobalBusinessUnit())
                .teamName(config.getAuthConfig().getTeamName())
                .applicationName(config.getAuthConfig().getApplicationName())
                .authMetrics(AuthMetricsFactory.getInstance(AuthMetricsConstants.TELEMETRY_LIB))
                // You can use AuthMetricsConstants.COMMONS_LIB for commons
                .build();
    }

    @Provides
    @Singleton
    public MonitoringClient getMonitoringClient() {
        BasicAuthenticationDetailsProvider authProvider;
        String t2IngestionEndpoint = LOCAL;

        if (config.getStage().equals("DEVELOPMENT")) {
            authProvider = new MockAuthenticationDetailsProvider();
        } else {
            t2IngestionEndpoint = config.getMetricsConfig().getT2Config().getEndpointOverride();
            authProvider = InstancePrincipalsAuthenticationDetailsProvider.builder().build();
        }

        MonitoringClient monitoringClient = new MonitoringClient(authProvider, null);

        log.info("t2IngestionEndpoint: {}", t2IngestionEndpoint);

        monitoringClient.setEndpoint(t2IngestionEndpoint);
        return monitoringClient;
    }

    @Provides
    @Singleton
    public AuthorizationClient getAuthorizationClient(
            ServiceAuthenticationClient serviceAuthenticationClient) {

        return AuthorizationClient.builder()
                .serviceName(config.getAuthConfig().getIdentityWhitelistedName())
                .authorizationEndpoint(config.getAuthConfig().getAuthServiceEndpoint())
                .region(config.getRegion())
                .physicalAD(config.getAvailabilityDomain().getName())
                .serviceAuthenticationClient(serviceAuthenticationClient)
                .rootCertPath(config.getAuthConfig().getDefaultTrustStorePath())
                .authMetrics(AuthMetricsFactory.getInstance(AuthMetricsConstants.TELEMETRY_LIB))
                // You can use AuthMetricsConstants.COMMONS_LIB for commons
                .build();
    }

    @Provides
    @Singleton
    public SecretRetriever getSecretRetriever() {
        if (config.getRegion() == Region.DEV) {
            return new FileBasedSecretRetriever();
        } else {
            VaultClient vaultClient =
                    new VaultClient(
                            config.getSecretServiceConfig(),
                            InstancePrincipalsAuthenticationDetailsProvider.builder().build());
            return new SecretServiceBasedSecretRetriever(vaultClient);
        }
    }

    @Provides
    @Singleton
    public JiraSDService getJiraSDService(SecretRetriever secretRetriever)
            throws URISyntaxException, SecretRetrieverException {
        JiraSDConfig jiraSDConfig = this.config.getJiraSDConfig();
        String jiraUsername = null;
        String jiraAccessPass = null;
        if (config.getRegion() == Region.DEV) {
            String usernameSecretPath = jiraSDConfig.getUsernameSecretPath();
            String passwordSecretPath = jiraSDConfig.getPasswordSecretPath();
            jiraUsername = secretRetriever.retrieveSecret(usernameSecretPath);
            jiraAccessPass = secretRetriever.retrieveSecret(passwordSecretPath);
        }
        // Beta phx and prod phx shares the same secret, as Jira team only allows one account per
        // region
        // https://jira-sd.mc1.oracleiaas.com/browse/JADMIN-5313
        else if (config.getRegion() == Region.PHX) {
            jiraUsername = "jirasd-lvv-service-us-phoenix-1";
            jiraAccessPass =
                    secretRetriever.retrieveSecret(
                            "/secret/lvv-service-beta/jira_admin_user/latest");
        } else {
            jiraUsername = "jirasd-lvv-service-" + config.getRegion().getPublicRegionName();
            jiraAccessPass =
                    secretRetriever.retrieveSecret(
                            "/secret/lvv-service-prod/jira_admin_user/latest");
        }
        JiraRestClientFactory clientFactory = new AsynchronousJiraRestClientFactory();
        JiraRestClient jiraRestClient =
                clientFactory.createWithBasicHttpAuthentication(
                        new URI(jiraSDConfig.getJiraSDEndpoint()), jiraUsername, jiraAccessPass);
        JiraSDService jiraProxy = new JiraSDService(jiraRestClient);
        return jiraProxy;
    }

    @Provides
    @Singleton
    public BasicAuthenticationDetailsProvider getAuthProvider() {
        //        config.setStage("DEVELOPMENT_WITH_INST_PRINCIPAL");
        switch (config.getStage()) {
            case "DEVELOPMENT":
                return new MockAuthenticationDetailsProvider();
            case "DEVELOPMENT_WITH_INST_PRINCIPAL":
                /*
                 * Apply below command for using instance principal in local
                 * ssh -L 8000:169.254.169.254:80 <guid>@<instance-ip-in-beta-region>
                 */
                return InstancePrincipalsAuthenticationDetailsProvider.builder()
                        .metadataBaseUrl("http://localhost:15001/")
                        .build();
            default:
                return InstancePrincipalsAuthenticationDetailsProvider.builder().build();
        }
    }

    @Named("NcpJobsClient")
    @Provides
    @Singleton
    public JobsClient provideNcpJobsClient(
            BasicAuthenticationDetailsProvider basicAuthenticationDetailsProvider) {
        JobsClient jobsClient;
        //        config.setStage("DEVELOPMENT_WITH_INST_PRINCIPAL");
        switch (config.getStage()) {
            case "DEVELOPMENT":
                return MockNcpClients.getMockJobsClient();
            default:
                /*
                 * Apply below command for using instance principal in local
                 * ssh -L 8000:169.254.169.254:80 <guid>@<instance-ip-in-beta-region>
                 */
                jobsClient =
                        JobsClient.builder()
                                .endpoint(this.config.getNcpServiceConfiguration().getEndpoint())
                                .build(basicAuthenticationDetailsProvider);
                return jobsClient;
        }
    }

    @Named("NcpServiceClient")
    @Provides
    @Singleton
    public NcpService createNcpService(@Named("NcpJobsClient") JobsClient jobsClient) {
        return new NcpService(jobsClient);
    }

    @Provides
    @Singleton
    public DataStoreConfig getKievConfig() throws IOException {
        log.info("Connecting to Kiev in KaaS Mode");

        if (config.getRegion() == Region.DEV) {
            DataStoreConfig dsc =
                    new DirectDbStoreConfig(
                            "pdbdev",
                            "lvv-service",
                            "jdbc:oracle:thin:@//localhost:1521/pdbdev",
                            "lvvproject",
                            "lvvproject123456");
            return dsc;
        } else {
            KaasStoreConfig kaasStoreConfig =
                    new KaasStoreConfig(
                            config.getKaasStoreConfig().getStoreName(),
                            config.getKaasStoreConfig().getAppName());
            kaasStoreConfig.setAuthEndpoint(config.getKaasStoreConfig().getAuthEndpoint());
            kaasStoreConfig.setFrontendEndpoint(config.getKaasStoreConfig().getFrontendEndpoint());
            kaasStoreConfig.setCompartmentId(config.getKaasStoreConfig().getCompartmentId());
            kaasStoreConfig.setTenantId(config.getKaasStoreConfig().getTenantId());
            kaasStoreConfig.setRootCertPemPath(config.getKaasStoreConfig().getRootCertPemPath());
            kaasStoreConfig.setDynamicSslContextProviderConfig(
                    new DynamicSslContextProviderConfig(
                            null, null, null, config.getKaasStoreConfig().getRootCertPemPath()));
            kaasStoreConfig.setLocality(ClientRegistryLocality.REGIONAL);
            log.info("Found data store config: {}", kaasStoreConfig);
            log.info(
                    "Using KaaS Data Store endpoint: {}",
                    config.getKaasStoreConfig().getFrontendEndpoint());
            return kaasStoreConfig;
        }
    }

    @Provides
    @Singleton
    public PaginationTokenSerializer getPaginationTokenSerializer(MappedDataStore dataStore) {
        return new PaginationTokenSerializer(dataStore);
    }
}

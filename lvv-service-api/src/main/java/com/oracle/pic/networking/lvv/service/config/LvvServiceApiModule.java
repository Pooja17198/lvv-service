package com.oracle.pic.networking.lvv.service.config;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.S2SAuthenticationDetailsProvider;
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
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDHelper;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.dependencies.ncp.NcpClientHelper;
import com.oracle.pic.networking.lvv.service.dependencies.planservice.PlanServiceClient;
import com.oracle.pic.networking.lvv.service.dependencies.planservice.PlanServiceHelper;
import com.oracle.pic.networking.lvv.service.dependencies.storekeeper.StoreKeeperHelper;
import com.oracle.pic.networking.lvv.service.health.LvvServiceApiDeepCheck;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetails;
import com.oracle.pic.networking.lvv.service.kiev.BlockDetailsDao;
import com.oracle.pic.networking.lvv.service.kiev.ConfigurationStore;
import com.oracle.pic.networking.lvv.service.kiev.DataStoreProvider;
import com.oracle.pic.networking.lvv.service.kiev.KievConfigurationStore;
import com.oracle.pic.networking.lvv.service.kiev.KievHashBucketProvider;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetails;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetailsDao;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItem;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItemDao;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResultDao;
import com.oracle.pic.networking.lvv.service.resources.ResourceModelTransformer;
import com.oracle.pic.networking.lvv.service.secret.FileBasedSecretRetriever;
import com.oracle.pic.networking.lvv.service.secret.SecretRetriever;
import com.oracle.pic.networking.lvv.service.secret.SecretRetrieverException;
import com.oracle.pic.networking.lvv.service.secret.SecretServiceBasedSecretRetriever;
import com.oracle.pic.networking.lvv.service.service.CablingTaskService;
import com.oracle.pic.networking.lvv.service.service.CablingValidationService;
import com.oracle.pic.networking.lvv.service.service.ProjectService;
import com.oracle.pic.networking.lvv.service.service.RacksService;
import com.oracle.pic.networking.lvv.service.service.RegionsService;
import com.oracle.pic.storekeeper.StoreKeeper;
import com.oracle.pic.storekeeper.StoreKeeperClient;
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

    private final LvvServiceApiConfiguration config;

    public LvvServiceApiModule(LvvServiceApiConfiguration config) {
        this.config = config;
    }

    @Override
    protected void configure() {
        log.info("Binding");

        for (Class<?> c : LvvServiceApi.RESOURCE_CLASSES) {
            bind(c).in(Singleton.class);
        }

        bind(LvvServiceApiConfiguration.class).toInstance(config);
        bind(AuthConfig.class).toInstance(config.getAuthConfig());

        bind(CablingTaskService.class).in(Singleton.class);
        bind(ProjectService.class).in(Singleton.class);
        bind(CablingValidationService.class).in(Singleton.class);
        bind(RegionsService.class).in(Singleton.class);
        bind(RacksService.class).in(Singleton.class);

        bind(NcpClientHelper.class).in(Singleton.class);
        bind(PlanServiceHelper.class).in(Singleton.class);
        bind(JiraSDHelper.class).in(Singleton.class);
        bind(StoreKeeperHelper.class).in(Singleton.class);

        bindProjectBucket();
        bindValidationFailureResultBucket();
        bindBlockDetailsBucket();
        bindNcpJobDetailsBucket();

        bind(ProjectItemDao.class).in(Singleton.class);
        bind(ValidationFailureResultDao.class).in(Singleton.class);
        bind(BlockDetailsDao.class).in(Singleton.class);
        bind(NcpJobDetailsDao.class).in(Singleton.class);

        bind(ResourceModelTransformer.class).in(Singleton.class);

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
    }

    private void bindProjectBucket() {
        KievHashBucketProvider<Long, ProjectItem> kievHashBucketProvider =
                new KievHashBucketProvider<>(
                        "projectItemsBucket",
                        "Bucket which stores all project items",
                        Long.class,
                        ProjectItem.class);
        bind(new TypeLiteral<MappedHashBucket<Long, ProjectItem>>() {})
                .toProvider(kievHashBucketProvider);

        bind(new TypeLiteral<ConfigurationStore<Long, ProjectItem>>() {})
                .to(new TypeLiteral<KievConfigurationStore<Long, ProjectItem>>() {});
    }

    private void bindValidationFailureResultBucket() {
        KievHashBucketProvider<ValidationFailureResult.LinkSource, ValidationFailureResult>
                validationResultProvider =
                        new KievHashBucketProvider<>(
                                "cableValidationsStore",
                                "Bucket to store validation failure results",
                                ValidationFailureResult.LinkSource.class,
                                ValidationFailureResult.class);

        bind(new TypeLiteral<
                        MappedHashBucket<
                                ValidationFailureResult.LinkSource, ValidationFailureResult>>() {})
                .toProvider(validationResultProvider);

        bind(new TypeLiteral<
                        ConfigurationStore<
                                ValidationFailureResult.LinkSource, ValidationFailureResult>>() {})
                .to(
                        new TypeLiteral<
                                KievConfigurationStore<
                                        ValidationFailureResult.LinkSource,
                                        ValidationFailureResult>>() {});
    }

    private void bindBlockDetailsBucket() {
        KievHashBucketProvider<BlockDetails.Block, BlockDetails> blockDetailsProvider =
                new KievHashBucketProvider<>(
                        "blockDetailBucket",
                        "Bucket to store block details for projects",
                        BlockDetails.Block.class,
                        BlockDetails.class);

        bind(new TypeLiteral<MappedHashBucket<BlockDetails.Block, BlockDetails>>() {})
                .toProvider(blockDetailsProvider);

        bind(new TypeLiteral<ConfigurationStore<BlockDetails.Block, BlockDetails>>() {})
                .to(new TypeLiteral<KievConfigurationStore<BlockDetails.Block, BlockDetails>>() {});
    }

    private void bindNcpJobDetailsBucket() {
        KievHashBucketProvider<String, NcpJobDetails> kievHashBucketProvider =
                new KievHashBucketProvider<>(
                        "ncpJobDetailsBucket",
                        "Bucket which NCP Validation job details",
                        String.class,
                        NcpJobDetails.class);
        bind(new TypeLiteral<MappedHashBucket<String, NcpJobDetails>>() {})
                .toProvider(kievHashBucketProvider);

        bind(new TypeLiteral<ConfigurationStore<String, NcpJobDetails>>() {})
                .to(new TypeLiteral<KievConfigurationStore<String, NcpJobDetails>>() {});
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
    public JiraSDService getJiraSDService(
            SecretRetriever secretRetriever, JiraSDHelper jiraSDHelper)
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
        JiraSDService jiraProxy = new JiraSDService(jiraRestClient, jiraSDHelper);
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

    @Provides
    @Singleton
    public StoreKeeper skClient() {
        final StoreKeeperClient skClient;

        if (config.getStage().equals("DEVELOPMENT")) {
            BasicAuthenticationDetailsProvider authProvider =
                    new MockAuthenticationDetailsProvider();
            skClient = new StoreKeeperClient(authProvider, null);
        } else {
            skClient =
                    StoreKeeperClient.builder()
                            .build(
                                    S2SAuthenticationDetailsProvider.builder()
                                            .useInstancePrincipals()
                                            .build());
        }
        String endpoint = resolveStoreKeeperEndpoint();
        log.info(
                "Using StoreKeeper endpoint '{}' (availabilityDomain='{}', region='{}')",
                endpoint,
                this.config.getAvailabilityDomain().getName(),
                this.config.getRegion().getPublicRegionName());
        skClient.setEndpoint(endpoint);
        return skClient;
    }

    private String resolveStoreKeeperEndpoint() {
        String configured = this.config.getSkConfig().getEndpoint();
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("skConfig.endpoint must be configured");
        }
        return configured.trim();
    }

    @Provides
    @Singleton
    public PlanServiceClient getPlanServiceClient() {
        BasicAuthenticationDetailsProvider authProvider =
                S2SAuthenticationDetailsProvider.builder().useInstancePrincipals().build();
        return new PlanServiceClient(config.getPlanServiceConfiguration(), authProvider);
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
            kaasStoreConfig.setTransactionMaxWrites(
                    config.getKaasStoreConfig().getTransactionMaxWrites());
            kaasStoreConfig.setTransactionMaxReads(
                    config.getKaasStoreConfig().getTransactionMaxReads());
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

package com.oracle.pic.networking.lvv.service.identity;

import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/** Required input parameters to the identity configuration */
@Setter
@Getter
public class IdentityConfiguration {

    /** Should we call the identity server to authorize? */
    @NonNull private Boolean enabled;

    /** Identity server endpoint used for authorization. */
    @NonNull private String authorizationEndpoint;

    /** The CA bundle used for TLS/mTLS. Leave it null for using system default */
    private String caBundleFile;

    /**
     * For the S2S call using OBO token, Certificate file used for instantiating the Service AuthN
     * Client
     */
    @NonNull private String certificateFile;

    /**
     * For the S2S call using OBO token, Intermediate Certificate file used for instantiating the
     * Service AuthN Client
     */
    @NonNull private String intermediateCertificateFile;

    /**
     * For the S2S call using OBO token, RSA Private Key file used for instantiating the Service
     * AuthN Client
     */
    @NonNull private String privateKeyFile;

    /** Service Tenant OCID used in instantiating the Service AuthN Client */
    @NonNull private String serviceTenantOcid;

    /**
     * Service Name registered with Authorization Service, and used in policy, S2S, and OBO call.
     */
    @NonNull private String serviceName;

    /**
     * List of target C3 service name when making OBO call to local or remote C3 public endpoint.
     */
    private List<String> targetServiceNames;

    /** Account Service server endpoint. */
    private String accountServiceEndpoint;

    /** Identity Service server endpoint. */
    private String identityServiceEndpoint;

    /** Account Service max duration time. */
    private int maxDurationInMilSeconds = 5000;

    /** Should we call the identity server to authenticate?. */
    private Boolean authenticationEnabled = false;

    /**
     * the global business unit (GBU) of the team making the request. An example GBU is Cloud-Infra,
     * which is Identity's GBU.
     */
    @NonNull private String globalBusinessUnit;

    /** the name of the team that is making auth requests */
    @NonNull private String teamName;

    /**
     * the name of the application running the AuthServiceAuthenticationClient/X509FederationClient
     */
    @NonNull private String applicationName;
}

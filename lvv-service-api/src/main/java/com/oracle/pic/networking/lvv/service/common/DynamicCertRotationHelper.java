package com.oracle.pic.networking.lvv.service.common;

import com.oracle.bmc.auth.tls.TlsConfig;
import com.oracle.pic.networking.lvv.service.identity.IdentityConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DynamicCertRotationHelper {

    public static TlsConfig getCommonsTlsConfigFromIdentityConfig(
            IdentityConfiguration identityConfig) {
        return identityConfig == null
                ? null
                : TlsConfig.builder()
                        .caBundle(identityConfig.getCaBundleFile())
                        .clientCertificatePath(identityConfig.getCertificateFile())
                        .clientIntermediateCertificatePath(
                                identityConfig.getIntermediateCertificateFile())
                        /* .privateKeyPassphrase(null) */
                        .privateKeyPath(identityConfig.getPrivateKeyFile())
                        .build();
    }
}

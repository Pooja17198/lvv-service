package com.oracle.pic.networking.lvv.service.identity;

import com.oracle.pic.identity.authentication.AuthUtils;
import com.oracle.pic.identity.authentication.key.X509CertificateAndRsaPrivateKey;
import com.oracle.pic.identity.authentication.key.X509CertificateChainSupplier;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@Slf4j
public class CertificateChainSupplier implements X509CertificateChainSupplier {

    private final IdentityConfiguration identityConfiguration;

    public CertificateChainSupplier(IdentityConfiguration identityConfiguration) {
        this.identityConfiguration = identityConfiguration;
    }

    @Override
    public List<X509CertificateAndRsaPrivateKey> get() {
        try {
            // Prepare the leaf service certificate.
            String certPayload =
                    IOUtils.toString(
                            new FileInputStream(identityConfiguration.getCertificateFile()),
                            Charset.defaultCharset());
            X509Certificate leaf = AuthUtils.loadCertificateFromString(certPayload);
            log.info("Loaded identity cert from {}", identityConfiguration.getCertificateFile());

            String privateKeyPayload =
                    IOUtils.toString(
                            new FileInputStream(identityConfiguration.getPrivateKeyFile()),
                            Charset.defaultCharset());
            RSAPrivateKey privateKey = AuthUtils.loadRSAPrivateKeyFromString(privateKeyPayload);
            log.info("Loaded identity key from {}", identityConfiguration.getPrivateKeyFile());

            X509CertificateAndRsaPrivateKey leafCertificateAndRsaPrivateKey =
                    new X509CertificateAndRsaPrivateKey(leaf, privateKey);

            // Prepare the intermediate certificate. Note that you will not have the private key for
            // this.
            String intermediateCertPayload =
                    IOUtils.toString(
                            new FileInputStream(
                                    identityConfiguration.getIntermediateCertificateFile()),
                            Charset.defaultCharset());
            X509Certificate intermediate =
                    AuthUtils.loadCertificateFromString(intermediateCertPayload);
            log.info(
                    "Loaded identity intermediate cert from {}",
                    identityConfiguration.getIntermediateCertificateFile());

            X509CertificateAndRsaPrivateKey intCertificateAndRsaPrivateKey =
                    new X509CertificateAndRsaPrivateKey(intermediate, Optional.empty());

            return Arrays.asList(leafCertificateAndRsaPrivateKey, intCertificateAndRsaPrivateKey);
        } catch (IOException e) {
            log.error("Failed to read resource(s).", e);
            throw new RuntimeException("Failed to load identity cert sets", e);
        }
    }
}

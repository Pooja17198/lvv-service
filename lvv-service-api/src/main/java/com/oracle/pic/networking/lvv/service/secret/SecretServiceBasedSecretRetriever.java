package com.oracle.pic.networking.lvv.service.secret;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.bmc.model.BmcException;
import com.oracle.pic.vault.VaultClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;

@Slf4j
/** Retrieves secrets from Secret Service. */
public class SecretServiceBasedSecretRetriever implements SecretRetriever {

    // constants declared as package-private to allow test cases access to those

    static final String VAULT_SECRET_KEY = "secret";

    static final String MISSING_KEY_ERROR =
            "Secret map should contain key '" + VAULT_SECRET_KEY + "'.";
    static final String TOO_MANY_ENTRIES =
            "Secret map should contain only one entry, however there are ";
    static final String UNKNOWN_ERROR = "Error occurred while retrieving secrets";

    private static final String PASSWORD_KEY_NAME = "password";

    private final VaultClient vaultClient;

    /**
     * Construct an instance specifying the {@link VaultClient} to retrieve secrets with.
     *
     * @param vaultClient The specified {@link VaultClient}.
     */
    public SecretServiceBasedSecretRetriever(VaultClient vaultClient) {
        this.vaultClient = vaultClient;
    }

    @Override
    public String retrieveSecret(String secretName) throws SecretRetrieverException {
        return getSecretValueFromPath(secretName);
    }

    public String getSecretValueFromPath(@NonNull String secretPath)
            throws SecretRetrieverException {
        // Secret path is always converted to lowercase by SMS
        String decodedSecret = getSecretFromPath(secretPath);
        return getSecretFromJsonIfApplicable(secretPath, decodedSecret);
    }

    private String getSecretFromPath(String secretPath) throws SecretRetrieverException {
        try {
            Map<String, String> rawSecrets = vaultClient.getSecret(secretPath).getData();
            if (rawSecrets.size() != 1) {
                throw new RuntimeException(TOO_MANY_ENTRIES + rawSecrets.size());
            } else if (!rawSecrets.containsKey(VAULT_SECRET_KEY)) {
                throw new RuntimeException(MISSING_KEY_ERROR);
            }
            String secretValue = rawSecrets.get(VAULT_SECRET_KEY);
            log.info("Found secret with path: {} from SMSV2", secretPath);

            // The secrets are always Base64 encoded string
            String decodedSecret =
                    new String(Base64.getDecoder().decode(secretValue), StandardCharsets.UTF_8);
            return decodedSecret;
        } catch (final BmcException ex) {
            final int httpCode = ex.getStatusCode();
            if (httpCode == HttpStatus.SC_NOT_FOUND) {
                throw new SecretRetrieverException(
                        secretPath, SecretRetrieverException.ErrorCode.NotFound, ex);
            } else if (httpCode == HttpStatus.SC_FORBIDDEN) {
                throw new SecretRetrieverException(
                        secretPath, SecretRetrieverException.ErrorCode.Forbidden, ex);
            }
            throw new RuntimeException(UNKNOWN_ERROR, ex);
        }
    }

    private String getSecretFromJsonIfApplicable(String secretPath, String decodedSecret) {
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<Map<String, String>> typeRef = new TypeReference<Map<String, String>>() {};
        try {
            Map<String, String> secretJson = mapper.readValue(decodedSecret, typeRef);
            return secretJson.get(PASSWORD_KEY_NAME);
        } catch (IOException io) {
            log.info("failed to parse json for secret path: {}", secretPath);
            return decodedSecret;
        }
    }
}

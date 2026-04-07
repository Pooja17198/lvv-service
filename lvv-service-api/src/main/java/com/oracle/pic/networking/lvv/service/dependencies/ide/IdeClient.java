package com.oracle.pic.networking.lvv.service.dependencies.ide;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.http.signing.RequestSigningFilter;
import com.oracle.pic.networking.lvv.service.config.ServiceProviderMetricsFilter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

/**
 * Client for the IDE (Infrastructure Design Engineering) physical cutsheets API.
 *
 * <p>Fetches patch panel (physical cutsheet) data for an entire rack in a single paginated call.
 */
@Slf4j
@Singleton
public class IdeClient {

    private static final String PHYSICAL_CUTSHEETS_PATH = "/idelvv/physicalcutsheets";
    private static final int TIMEOUT_IN_MILLIS = (int) Duration.ofSeconds(30).toMillis();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final String ideEndpoint;
    private final Client httpClient;
    private final ObjectMapper objectMapper;

    @Inject
    public IdeClient(
            IdeClientConfig config,
            ObjectMapper objectMapper,
            BasicAuthenticationDetailsProvider authProvider) {
        this.ideEndpoint = config.getEndpoint().replaceAll("/$", "");
        this.httpClient = buildSignedClient(authProvider);
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches all physical cutsheet entries for the given rack from IDE, handling pagination
     * transparently.
     *
     * @return list of raw item maps, each with keys: deviceName, devicePort, buildingName,
     *     roomName, rackNumber, easyMark
     */
    public List<Map<String, Object>> fetchPhysicalCutsheetsForRack(
            String buildingName, String rackNumber) {

        List<Map<String, Object>> allItems = new ArrayList<>();
        String nextPage = null;

        do {
            String url = buildUrl(buildingName, rackNumber, nextPage);
            log.info("Fetching IDE physical cutsheets: {}", url);

            WebTarget target = httpClient.target(ideEndpoint).path(PHYSICAL_CUTSHEETS_PATH);
            if (buildingName != null && !buildingName.isBlank()) {
                target = target.queryParam("buildingName", buildingName);
            }
            if (rackNumber != null && !rackNumber.isBlank()) {
                target = target.queryParam("rackNumber", rackNumber);
            }
            if (nextPage != null) {
                target = target.queryParam("page", nextPage);
            }

            int statusCode;
            String responseBody;
            String nextPageHeader;
            try (Response response =
                    target.request(MediaType.APPLICATION_JSON_TYPE)
                            .property("jersey.config.client.connectTimeout", TIMEOUT_IN_MILLIS)
                            .property("jersey.config.client.readTimeout", TIMEOUT_IN_MILLIS)
                            .get()) {
                statusCode = response.getStatus();
                responseBody = response.readEntity(String.class);
                nextPageHeader = response.getHeaderString("opc-next-page");
            } catch (ProcessingException e) {
                log.error(
                        "Failed to fetch IDE physical cutsheets for building={} rack={}",
                        buildingName,
                        rackNumber,
                        e);
                throw new RuntimeException("IDE physical cutsheets request failed", e);
            }

            if (statusCode < 200 || statusCode >= 300) {
                log.error(
                        "IDE returned {} for building={} rack={}: {}",
                        statusCode,
                        buildingName,
                        rackNumber,
                        responseBody);
                throw new RuntimeException("IDE physical cutsheets returned HTTP " + statusCode);
            }

            List<Map<String, Object>> items = parseItems(responseBody);
            allItems.addAll(items);

            nextPage = nextPageHeader;
            log.debug("Fetched {} items, nextPage={}", items.size(), nextPage);

        } while (nextPage != null);

        log.info("Total IDE physical cutsheet items fetched for rack {}: {}", rackNumber, allItems.size());
        return allItems;
    }

    private String buildUrl(String buildingName, String rackNumber, String page) {
        StringBuilder sb = new StringBuilder(ideEndpoint).append(PHYSICAL_CUTSHEETS_PATH).append("?");
        if (buildingName != null && !buildingName.isBlank()) {
            sb.append("buildingName=").append(buildingName).append("&");
        }
        if (rackNumber != null && !rackNumber.isBlank()) {
            sb.append("rackNumber=").append(rackNumber).append("&");
        }
        if (page != null) {
            sb.append("page=").append(page).append("&");
        }
        // Remove trailing & or ?
        String url = sb.toString();
        return url.endsWith("&") || url.endsWith("?") ? url.substring(0, url.length() - 1) : url;
    }

    private Client buildSignedClient(BasicAuthenticationDetailsProvider authProvider) {
        RequestSigningFilter signingFilter = RequestSigningFilter.fromAuthProvider(authProvider);
        return ClientBuilder.newBuilder()
                .register(signingFilter)
                .register(new ServiceProviderMetricsFilter("Ide"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseItems(String body) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, MAP_TYPE);
            Object items = parsed.get("items");
            if (items instanceof List) {
                return (List<Map<String, Object>>) items;
            }
            log.warn("IDE response missing 'items' array, returning empty list");
            return Collections.emptyList();
        } catch (IOException e) {
            log.error("Failed to parse IDE response: {}", body, e);
            throw new RuntimeException("Failed to parse IDE physical cutsheets response", e);
        }
    }
}

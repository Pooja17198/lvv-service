package com.oracle.pic.networking.lvv.service.dependencies.ide;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final String ideEndpoint;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Inject
    public IdeClient(IdeClientConfig config, ObjectMapper objectMapper) {
        this.ideEndpoint = config.getEndpoint().replaceAll("/$", "");
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
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

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .GET()
                            .timeout(TIMEOUT)
                            .header("Accept", "application/json")
                            .build();

            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.error("Failed to fetch IDE physical cutsheets for building={} rack={}", buildingName, rackNumber, e);
                throw new RuntimeException("IDE physical cutsheets request failed", e);
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("IDE returned {} for building={} rack={}: {}",
                        response.statusCode(), buildingName, rackNumber, response.body());
                throw new RuntimeException(
                        "IDE physical cutsheets returned HTTP " + response.statusCode());
            }

            List<Map<String, Object>> items = parseItems(response.body());
            allItems.addAll(items);

            nextPage = response.headers().firstValue("opc-next-page").orElse(null);
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

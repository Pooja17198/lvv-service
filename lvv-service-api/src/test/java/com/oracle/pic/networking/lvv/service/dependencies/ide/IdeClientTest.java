package com.oracle.pic.networking.lvv.service.dependencies.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.http.signing.RequestSigner;
import com.oracle.pic.vault.MockAuthenticationDetailsProvider;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IdeClientTest {

    private IdeClient ideClient;
    private RequestSigner requestSigner;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() throws Exception {
        IdeClientConfig config = new IdeClientConfig();
        config.setEndpoint("https://lvv.us-phoenix-1.oci.oc-test.com/");

        BasicAuthenticationDetailsProvider authProvider = new MockAuthenticationDetailsProvider();
        ideClient = new IdeClient(config, new ObjectMapper(), authProvider);

        requestSigner = mock(RequestSigner.class);
        httpClient = mock(HttpClient.class);

        setField(ideClient, "requestSigner", requestSigner);
        setField(ideClient, "httpClient", httpClient);
    }

    @Test
    void buildUrl_includesRackRoleSourceAndOptionalPage() throws Exception {
        String url = (String) invokeMethod(ideClient, "buildUrl", "iad60", "4426", "nextPageToken");

        assertTrue(url.startsWith("https://lvv.us-phoenix-1.oci.oc-test.com/idelvv/physicalcutsheets?"));
        assertTrue(url.contains("buildingName=iad60"));
        assertTrue(url.contains("rackNumber=4426"));
        assertTrue(url.contains("rackRole=source"));
        assertTrue(url.contains("page=nextPageToken"));
    }

    @Test
    void buildSignedGetRequest_skipsRestrictedHeadersAndKeepsSignedHeaders() throws Exception {
        URI uri = URI.create("https://example.com/idelvv/physicalcutsheets?rackRole=source");
        when(requestSigner.signRequest(any(), any(), any(), any()))
                .thenReturn(
                        Map.of(
                                "host", "example.com",
                                "content-length", "10",
                                "authorization", "Signature abc",
                                "date", "Tue, 08 Apr 2026 00:00:00 GMT"));

        HttpRequest request = (HttpRequest) invokeMethod(ideClient, "buildSignedGetRequest", uri);

        assertEquals("application/json", request.headers().firstValue("accept").orElse(""));
        assertEquals("Signature abc", request.headers().firstValue("authorization").orElse(""));
        assertEquals("Tue, 08 Apr 2026 00:00:00 GMT", request.headers().firstValue("date").orElse(""));
        assertTrue(request.headers().firstValue("host").isEmpty());
        assertTrue(request.headers().firstValue("content-length").isEmpty());
    }

    @Test
    void fetchPhysicalCutsheetsForRack_paginatesAndAggregatesAllItems() throws Exception {
        when(requestSigner.signRequest(any(), any(), any(), any())).thenReturn(Map.of());

        @SuppressWarnings("unchecked")
        HttpResponse<String> responsePage1 = mock(HttpResponse.class);
        when(responsePage1.statusCode()).thenReturn(200);
        when(responsePage1.body())
                .thenReturn(
                        "{\"items\":[{\"deviceName\":\"device-a\",\"devicePort\":\"Eth1/1\"}]}");
        when(responsePage1.headers())
                .thenReturn(
                        HttpHeaders.of(
                                Map.of("opc-next-page", List.of("next-token")), (k, v) -> true));

        @SuppressWarnings("unchecked")
        HttpResponse<String> responsePage2 = mock(HttpResponse.class);
        when(responsePage2.statusCode()).thenReturn(200);
        when(responsePage2.body())
                .thenReturn(
                        "{\"items\":[{\"deviceName\":\"device-b\",\"devicePort\":\"Eth1/2\"}]}");
        when(responsePage2.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(responsePage1)
                .thenReturn(responsePage2);

        List<Map<String, Object>> allItems = ideClient.fetchPhysicalCutsheetsForRack("iad60", "4426");

        assertEquals(2, allItems.size());
        assertEquals("device-a", allItems.get(0).get("deviceName"));
        assertEquals("device-b", allItems.get(1).get("deviceName"));

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        List<HttpRequest> sentRequests = requestCaptor.getAllValues();
        assertTrue(sentRequests.get(0).uri().toString().contains("rackRole=source"));
        assertTrue(sentRequests.get(1).uri().toString().contains("page=next-token"));
    }

    @Test
    void fetchPhysicalCutsheetsForRack_non2xxResponse_throwsRuntimeException() throws Exception {
        when(requestSigner.signRequest(any(), any(), any(), any())).thenReturn(Map.of());

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("{\"message\":\"NotAuthenticated\"}");
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        RuntimeException ex =
                assertThrows(
                        RuntimeException.class,
                        () -> ideClient.fetchPhysicalCutsheetsForRack("iad60", "4426"));
        assertTrue(ex.getMessage().contains("HTTP 401"));
    }

    @Test
    void fetchPhysicalCutsheetsForRack_ioException_throwsWrappedRuntimeException() throws Exception {
        when(requestSigner.signRequest(any(), any(), any(), any())).thenReturn(Map.of());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("network down"));

        RuntimeException ex =
                assertThrows(
                        RuntimeException.class,
                        () -> ideClient.fetchPhysicalCutsheetsForRack("iad60", "4426"));
        assertTrue(ex.getMessage().contains("request failed"));
    }

    private static Object invokeMethod(Object target, String methodName, Object... args) throws Exception {
        Class<?>[] argTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i].getClass();
        }
        Method method = target.getClass().getDeclaredMethod(methodName, argTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

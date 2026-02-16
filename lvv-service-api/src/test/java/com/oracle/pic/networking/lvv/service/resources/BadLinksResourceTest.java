package com.oracle.pic.networking.lvv.service.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.model.BadLinkDetail;
import com.oracle.pic.networking.lvv.service.service.BadLinksService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for BadLinksResource:<br>
 * 1) Verifies correct interaction with BadLinksService, including passing MetricsScope instance.
 * <br>
 * 3) Ensures MetricsScope is used without real side effects via static mocking and that success is
 * recorded in happy-path scenarios.<br>
 */
@ExtendWith(MockitoExtension.class)
class BadLinksResourceTest {

    @Mock BadLinksService badLinksService;
    @Mock MetricsScope metricsScope;
    @Mock Principal principal;
    @Mock AuthorizationRequest authorizationRequest;

    /**
     * Verifies the success path:<br>
     * 1) Creates a MetricsScope statically (mocked to avoid external side effects) and attaches
     * dimensions/emissions as part of resource handling.<br>
     * 2) Returns the service result unchanged and records success on the metrics scope.<br>
     * 3) Asserts that the service is invoked and the scope is marked successful.<br>
     */
    @Test
    void testGetBadLinks_success() {
        BadLinksResource resource = new BadLinksResource(badLinksService);
        String buildingName = "BLD-1";
        String opcRequestId = "req-1";

        // Expected response from the service call
        List<BadLinkDetail> expected =
                List.of(
                        BadLinkDetail.builder().device("devA").remoteDevice("remA").build(),
                        BadLinkDetail.builder().device("devB").remoteDevice("remB").build());

        // Mock MetricsScope static factory and behavior to avoid real emissions and ensure
        // recordSuccess() is reachable on the happy path
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);
            when(metricsScope.withDimension(anyString(), anyString())).thenReturn(metricsScope);
            when(metricsScope.emit(anyString(), anyDouble())).thenReturn(metricsScope);
            when(metricsScope.recordSuccess()).thenReturn(metricsScope);

            // Service returns the expected list
            when(badLinksService.getBadLinks(eq(buildingName), eq(metricsScope)))
                    .thenReturn(expected);

            // Act: invoke resource
            List<BadLinkDetail> result =
                    resource.getBadLinks(
                            "us-ashburn-1",
                            buildingName,
                            opcRequestId,
                            principal,
                            authorizationRequest);

            // Assert: response equals the service output; verify correct normalization and metrics
            assertEquals(expected, result);
            verify(badLinksService).getBadLinks(eq(buildingName), eq(metricsScope));
            verify(metricsScope).recordSuccess();
        }
    }

    /**
     * Ensures input validation for the building name:<br>
     * 1) When the building parameter is blank (only whitespace), the resource should:<br>
     * 2) Throw a RenderableException to signal a 4xx client error.<br>
     * 3) Not call BadLinksService (guarding against invalid downstream operations).<br>
     * 4) Not record a success metric (since the request fails validation).<br>
     * 5) MetricsScope.create(...) is still mocked to ensure the resource can construct its scope,
     * but success is not recorded in this error path.<br>
     */
    @Test
    void testGetBadLinks_emptyBuilding_throws() {
        BadLinksResource resource = new BadLinksResource(badLinksService);
        String buildingName = "   ";
        String opcRequestId = "req-1";

        // Mock MetricsScope static factory to prevent real emissions even on error path
        try (MockedStatic<MetricsScope> staticMock = mockStatic(MetricsScope.class)) {
            staticMock.when(() -> MetricsScope.create(anyString())).thenReturn(metricsScope);

            // Act + Assert: expect a RenderableException due to invalid (blank) building
            assertThrows(
                    RenderableException.class,
                    () ->
                            resource.getBadLinks(
                                    "",
                                    buildingName,
                                    opcRequestId,
                                    principal,
                                    authorizationRequest));

            // Verify no downstream service calls occur on invalid input
            verify(badLinksService, never()).getBadLinks(anyString(), any());

            // Success should not be recorded on the metrics scope in error scenarios
            verify(metricsScope, never()).recordSuccess();
        }
    }
}

package com.oracle.pic.networking.lvv.service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.jira.JiraSDService;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.dependencies.ncp.NcpClientHelper;
import com.oracle.pic.networking.lvv.service.kiev.JobStatus;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetails;
import com.oracle.pic.networking.lvv.service.kiev.NcpJobDetailsDao;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
import com.oracle.pic.networking.lvv.service.models.ncp.JobType;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

/**
 * Unit tests for CablingValidationService covering edge cases and maximizing coverage based on the
 * current implementation.
 */
class CablingValidationServiceTest {

    @Mock private NcpClientHelper ncpClientHelper;
    @Mock private JiraSDService jiraSDService;

    @Mock
    private com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResultDao
            validationFailureResultDao;

    @Mock private NcpJobDetailsDao ncpJobDetailsDao;
    @Mock private MetricsScope metricsScope;

    private CablingValidationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service =
                new CablingValidationService(
                        ncpClientHelper,
                        jiraSDService,
                        validationFailureResultDao,
                        ncpJobDetailsDao);
    }

    // ========== validateCablingTasks ==========

    @Test
    void validateCablingTasks_withNonEmptyDevices_usesHealthCheckPayload_andPersistsJobs() {
        String region = "reg";
        String building = "bld";
        String rackSerial = "RSN-1";
        String rackUnit = "RU-42";
        List<String> devices = List.of("dev1", "dev2");

        HashMap<String, String> jobs = new HashMap<>();
        jobs.put("dev1", "job-1");
        jobs.put("dev2", "job-2");

        ArgumentCaptor<String> jobTypeCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> rackSerialCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<String>> devicesCap = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<MetricsScope> scopeCap = ArgumentCaptor.forClass(MetricsScope.class);
        ArgumentCaptor<String> regionCap = ArgumentCaptor.forClass(String.class);

        when(ncpClientHelper.createJobs(
                        jobTypeCap.capture(),
                        rackSerialCap.capture(),
                        payloadCap.capture(),
                        devicesCap.capture(),
                        scopeCap.capture(),
                        regionCap.capture()))
                .thenReturn(jobs);

        // DAO returns device names
        NcpJobDetails j1 =
                NcpJobDetails.builder()
                        .rackSerial(rackSerial)
                        .deviceName("dev1")
                        .jobId(null)
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();
        NcpJobDetails j2 =
                NcpJobDetails.builder()
                        .rackSerial(rackSerial)
                        .deviceName("dev2")
                        .jobId(null)
                        .jobStatus(JobStatus.COMPLETED)
                        .build();
        when(ncpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(List.of(j1, j2));
        when(ncpJobDetailsDao.getNcpJobDetails("dev1")).thenReturn(j1);
        when(ncpJobDetailsDao.getNcpJobDetails("dev2")).thenReturn(j2);

        service.validateCablingTasks(region, building, rackSerial, rackUnit, devices, metricsScope);

        // Verify job creation args
        assertEquals(JobType.HEALTH_CHECK, jobTypeCap.getValue());
        assertEquals(rackSerial, rackSerialCap.getValue());
        assertEquals(region, regionCap.getValue());
        assertEquals(1, devicesCap.getValue().size());
        assertEquals("dev2", devicesCap.getValue().get(0));

        String payload = payloadCap.getValue();
        assertTrue(payload.contains("\"testSuites\""));
        assertTrue(payload.contains("\"rackNumber\":\"" + rackUnit + "\""));
        assertTrue(payload.contains("\"building\":\"" + building + "\""));

        // Verify persistence of jobs
        verify(ncpJobDetailsDao).addUpdateNcpJobDetails(jobs, rackSerial, metricsScope);

        // Verify metrics interactions
        verify(metricsScope).withDimension("buildingName", building);
        verify(metricsScope).withDimension("rackLocation", rackUnit);
        verify(metricsScope).withDimension("region", GeneralUtils.getRegionInternalName(region));
        verify(metricsScope).emit(MetricNames.ValidateCables.ValidateCable.name(), 1.0);
    }

    @Test
    void validateCablingTasks_withEmptyDevices_fetchesFromDao_thenUsesHealthCheck() {
        String region = "reg";
        String building = "bld";
        String rackSerial = "RSN-2";
        String rackUnit = "RU-7";

        // DAO returns device names
        NcpJobDetails j1 =
                NcpJobDetails.builder()
                        .rackSerial(rackSerial)
                        .deviceName("d1")
                        .jobId(null)
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();
        NcpJobDetails j2 =
                NcpJobDetails.builder()
                        .rackSerial(rackSerial)
                        .deviceName("d2")
                        .jobId(null)
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();
        when(ncpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(List.of(j1, j2));

        HashMap<String, String> jobs = new HashMap<>();
        jobs.put("d1", "job-a");
        jobs.put("d2", "job-b");

        ArgumentCaptor<String> jobTypeCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<String>> devicesCap = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> payloadCap = ArgumentCaptor.forClass(String.class);

        when(ncpClientHelper.createJobs(
                        jobTypeCap.capture(),
                        anyString(),
                        payloadCap.capture(),
                        devicesCap.capture(),
                        any(),
                        anyString()))
                .thenReturn(jobs);

        service.validateCablingTasks(
                region, building, rackSerial, rackUnit, new ArrayList<>(), metricsScope);

        assertEquals(JobType.HEALTH_CHECK, jobTypeCap.getValue());
        assertEquals(List.of("d1", "d2"), devicesCap.getValue());

        String payload = payloadCap.getValue();
        assertTrue(payload.contains("\"testSuites\""));
        assertTrue(payload.contains("\"rackNumber\":\"" + rackUnit + "\""));
        assertTrue(payload.contains("\"building\":\"" + building + "\""));

        verify(ncpJobDetailsDao).addUpdateNcpJobDetails(jobs, rackSerial, metricsScope);
    }

    @Test
    void validateCablingTasks_withEmptyDevicesAndDaoEmpty_usesPerRackPayload_dryRunTrue() {
        String region = "reg";
        String building = "bld";
        String rackSerial = "RSN-3";
        String rackUnit = "RU-9";

        when(ncpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(List.of());

        HashMap<String, String> jobs = new HashMap<>();
        // For per-rack job, key is rack serial usually
        jobs.put(rackSerial, "job-per-rack");

        ArgumentCaptor<String> jobTypeCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<String>> devicesCap = ArgumentCaptor.forClass(List.class);

        when(ncpClientHelper.createJobs(
                        jobTypeCap.capture(),
                        anyString(),
                        payloadCap.capture(),
                        devicesCap.capture(),
                        any(),
                        anyString()))
                .thenReturn(jobs);

        service.validateCablingTasks(
                region, building, rackSerial, rackUnit, List.of(), metricsScope);

        assertEquals(JobType.PER_RACK_VALIDATION_JOB, jobTypeCap.getValue());

        String payload = payloadCap.getValue();
        assertTrue(payload.contains("\"rack_number\":\"" + rackUnit + "\""));
        assertTrue(payload.contains("\"building\":\"" + building + "\""));
        assertTrue(payload.contains("\"dry_run\":true"));

        assertTrue(devicesCap.getValue().isEmpty());

        verify(ncpJobDetailsDao).addUpdateNcpJobDetails(jobs, rackSerial, metricsScope);
    }

    @Test
    void validateCablingTasks_onJsonError_throwsRenderableException_InternalError()
            throws Exception {
        String region = "reg";
        String building = "bld";
        String rackSerial = "RSN-4";
        String rackUnit = "RU-11";
        List<String> devices = List.of("d1");

        // Swap out the internal ObjectMapper to force a JsonProcessingException
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

        Field f = CablingValidationService.class.getDeclaredField("objectMapper");
        f.setAccessible(true);
        f.set(service, failing);

        RenderableException ex =
                assertThrows(
                        RenderableException.class,
                        () ->
                                service.validateCablingTasks(
                                        region,
                                        building,
                                        rackSerial,
                                        rackUnit,
                                        devices,
                                        metricsScope));
        assertEquals(ErrorCode.InternalError, ex.getErrorCode());

        verify(ncpClientHelper, never()).createJobs(any(), any(), any(), anyList(), any(), any());
        verify(ncpJobDetailsDao, never()).addUpdateNcpJobDetails(any(), anyString(), any());
    }

    // ========== updateValidationJobStatus ==========

    @Test
    void updateValidationJobStatus_whenFailed_setsFailedAndUpdatesDao() {
        String device = "devA";
        Map<String, JobStatus> status = Map.of(device, JobStatus.FAILED);

        NcpJobDetails curr =
                NcpJobDetails.builder()
                        .rackSerial("RSN")
                        .deviceName(device)
                        .jobId("jid")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();

        when(ncpJobDetailsDao.getNcpJobDetails(device)).thenReturn(curr);

        service.updateValidationJobStatus(status, "RSN", "reg", "RU", false, metricsScope);

        assertEquals(JobStatus.FAILED, curr.getJobStatus());
        verify(ncpJobDetailsDao).updateNcpJobDetails(curr);
        verifyNoInteractions(validationFailureResultDao);
    }

    @Test
    void updateValidationJobStatus_whenCompleted_updatesResults_andMarksCompleted() {
        String device = "devB";
        Map<String, JobStatus> status = Map.of(device, JobStatus.COMPLETED);

        NcpJobDetails curr =
                NcpJobDetails.builder()
                        .rackSerial("RSN")
                        .deviceName(device)
                        .jobId("jid-123")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();

        // First getNcpJobDetails returns curr; after processing results, guard check calls it
        // again.
        when(ncpJobDetailsDao.getNcpJobDetails(device))
                .thenReturn(curr) // top-level retrieval
                .thenReturn(
                        NcpJobDetails.builder()
                                .rackSerial("RSN")
                                .deviceName(device)
                                .jobId("jid-123")
                                .jobStatus(
                                        JobStatus.IN_PROGRESS) // not DEVICE_UNREACHABLE -> should
                                // mark COMPLETED
                                .build());

        List<ValidationFailureResult> output = List.of();
        when(ncpClientHelper.getNcpJobOutput("jid-123", "reg", "RSN", "RU")).thenReturn(output);

        MetricsScope addResultsScope = mock(MetricsScope.class);
        try (MockedStatic<MetricsScope> metricsScopeMocked =
                Mockito.mockStatic(MetricsScope.class)) {
            metricsScopeMocked
                    .when(
                            () ->
                                    MetricsScope.create(
                                            MetricNames.MetricScopeNames.ADD_VALIDATION_RESULTS
                                                    .name()))
                    .thenReturn(addResultsScope);

            service.updateValidationJobStatus(status, "RSN", "reg", "RU", false, metricsScope);

            verify(validationFailureResultDao)
                    .addUpdateValidationFailureResultsForDevices(output, addResultsScope, "reg");
            verify(metricsScope).recordSuccess();

            // After guard, should mark as COMPLETED
            ArgumentCaptor<NcpJobDetails> updated = ArgumentCaptor.forClass(NcpJobDetails.class);
            verify(ncpJobDetailsDao).updateNcpJobDetails(updated.capture());
            assertEquals(JobStatus.COMPLETED, updated.getValue().getJobStatus());
        }
    }

    @Test
    void updateValidationJobStatus_whenCompleted_butDeviceMarkedUnreachable_doesNotOverwrite() {
        String device = "devC";
        Map<String, JobStatus> status = Map.of(device, JobStatus.COMPLETED);

        NcpJobDetails curr =
                NcpJobDetails.builder()
                        .rackSerial("RSN")
                        .deviceName(device)
                        .jobId("jid-999")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();

        when(ncpJobDetailsDao.getNcpJobDetails(device))
                .thenReturn(curr) // initial
                .thenReturn(
                        NcpJobDetails.builder()
                                .rackSerial("RSN")
                                .deviceName(device)
                                .jobId("jid-999")
                                .jobStatus(JobStatus.DEVICE_UNREACHABLE) // guard prevents overwrite
                                .build());

        when(ncpClientHelper.getNcpJobOutput("jid-999", "reg", "RSN", "RU")).thenReturn(List.of());

        MetricsScope addResultsScope = mock(MetricsScope.class);
        try (MockedStatic<MetricsScope> metricsScopeMocked =
                Mockito.mockStatic(MetricsScope.class)) {
            metricsScopeMocked
                    .when(
                            () ->
                                    MetricsScope.create(
                                            MetricNames.MetricScopeNames.ADD_VALIDATION_RESULTS
                                                    .name()))
                    .thenReturn(addResultsScope);

            service.updateValidationJobStatus(status, "RSN", "reg", "RU", false, metricsScope);

            // Results were updated
            verify(validationFailureResultDao)
                    .addUpdateValidationFailureResultsForDevices(
                            anyList(), eq(addResultsScope), eq("reg"));
            verify(metricsScope).recordSuccess();

            // But final status wasn't overwritten to COMPLETED
            verify(ncpJobDetailsDao, never())
                    .updateNcpJobDetails(argThat(n -> n.getJobStatus() == JobStatus.COMPLETED));
        }
    }

    @Test
    void updateValidationJobStatus_inProgress_andLastAttempt_marksDeviceUnreachable() {
        String device = "devD";
        Map<String, JobStatus> status = Map.of(device, JobStatus.IN_PROGRESS);

        NcpJobDetails curr =
                NcpJobDetails.builder()
                        .rackSerial("RSN")
                        .deviceName(device)
                        .jobId("jid-1")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();

        when(ncpJobDetailsDao.getNcpJobDetails(device)).thenReturn(curr);

        service.updateValidationJobStatus(status, "RSN", "reg", "RU", true, metricsScope);

        assertEquals(JobStatus.DEVICE_UNREACHABLE, curr.getJobStatus());
        verify(ncpJobDetailsDao).updateNcpJobDetails(curr);
    }

    @Test
    void updateValidationJobStatus_inProgress_andNotLastAttempt_noop() {
        String device = "devE";
        Map<String, JobStatus> status = Map.of(device, JobStatus.IN_PROGRESS);

        NcpJobDetails curr =
                NcpJobDetails.builder()
                        .rackSerial("RSN")
                        .deviceName(device)
                        .jobId("jid-1")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();

        when(ncpJobDetailsDao.getNcpJobDetails(device)).thenReturn(curr);

        service.updateValidationJobStatus(status, "RSN", "reg", "RU", false, metricsScope);

        verify(ncpJobDetailsDao, never()).updateNcpJobDetails(any());
        assertEquals(JobStatus.IN_PROGRESS, curr.getJobStatus());
    }

    @Test
    void updateValidationJobStatus_currentNotInProgress_noop() {
        String device = "devF";
        Map<String, JobStatus> status = Map.of(device, JobStatus.COMPLETED);

        NcpJobDetails curr =
                NcpJobDetails.builder()
                        .rackSerial("RSN")
                        .deviceName(device)
                        .jobId("jid-1")
                        .jobStatus(JobStatus.COMPLETED)
                        .build();

        when(ncpJobDetailsDao.getNcpJobDetails(device)).thenReturn(curr);

        service.updateValidationJobStatus(status, "RSN", "reg", "RU", false, metricsScope);

        verifyNoInteractions(validationFailureResultDao);
        verify(ncpJobDetailsDao, never()).updateNcpJobDetails(any());
    }

    // ========== getValidationJobStatus ==========

    @Test
    void getValidationJobStatus_fetchesAndReturnsMap_fromDao() {
        String region = "reg";
        String rackSerial = "RSN-10";
        String rackUnit = "RU-55";

        // ncpClientHelper returns some raw status (it will be passed into
        // updateValidationJobStatus)
        when(ncpClientHelper.fetchJobStatus(rackSerial, region))
                .thenReturn(Map.of("dev1", JobStatus.IN_PROGRESS));

        // Provide current job details for device to avoid null during updateValidationJobStatus
        when(ncpJobDetailsDao.getNcpJobDetails("dev1"))
                .thenReturn(
                        NcpJobDetails.builder()
                                .rackSerial(rackSerial)
                                .deviceName("dev1")
                                .jobId("jid-xyz")
                                .jobStatus(JobStatus.IN_PROGRESS)
                                .build());

        // Return final statuses from DAO for response mapping
        NcpJobDetails d1 =
                NcpJobDetails.builder()
                        .rackSerial(rackSerial)
                        .deviceName("dev1")
                        .jobId("jid-xyz")
                        .jobStatus(JobStatus.FAILED)
                        .build();
        NcpJobDetails d2 =
                NcpJobDetails.builder()
                        .rackSerial(rackSerial)
                        .deviceName("dev2")
                        .jobId("jid-uvw")
                        .jobStatus(JobStatus.COMPLETED)
                        .build();
        when(ncpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(List.of(d1, d2));

        // Use a spy to assert updateValidationJobStatus is invoked with the right arguments
        CablingValidationService spyService =
                Mockito.spy(
                        new CablingValidationService(
                                ncpClientHelper,
                                jiraSDService,
                                validationFailureResultDao,
                                ncpJobDetailsDao));

        Map<String, JobStatus> result =
                spyService.getValidationJobStatus(
                        metricsScope, region, rackSerial, rackUnit, false);

        assertEquals(2, result.size());
        assertEquals(JobStatus.FAILED, result.get("dev1"));
        assertEquals(JobStatus.COMPLETED, result.get("dev2"));

        verify(ncpClientHelper).fetchJobStatus(rackSerial, region);
        verify(spyService)
                .updateValidationJobStatus(
                        eq(Map.of("dev1", JobStatus.IN_PROGRESS)),
                        eq(rackSerial),
                        eq(region),
                        eq(rackUnit),
                        eq(false),
                        eq(metricsScope));
    }
}

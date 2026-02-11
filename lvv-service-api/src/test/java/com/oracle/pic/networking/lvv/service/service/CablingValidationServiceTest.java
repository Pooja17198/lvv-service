package com.oracle.pic.networking.lvv.service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResultDao;
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
    @Mock private ValidationFailureResultDao validationFailureResultDao;
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
    void validateCablingTasks_withNonEmptyDevices_filtersInProgress_andCreatesHealthCheck() {
        String region = "reg1";
        String building = "bld1";
        String rackSerial = "RSN-1";
        String rackUnit = "RU-42";
        List<String> devices = List.of("dev1", "dev2");

        // dev1 is IN_PROGRESS -> should be filtered; dev2 is COMPLETED -> should remain
        NcpJobDetails j1 =
                NcpJobDetails.builder()
                        .rackSerial(rackSerial)
                        .deviceName("dev1")
                        .jobId("jid-1")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();
        NcpJobDetails j2 =
                NcpJobDetails.builder()
                        .rackSerial(rackSerial)
                        .deviceName("dev2")
                        .jobId("jid-2")
                        .jobStatus(JobStatus.COMPLETED)
                        .build();
        when(ncpJobDetailsDao.getNcpJobDetails("dev1")).thenReturn(j1);
        when(ncpJobDetailsDao.getNcpJobDetails("dev2")).thenReturn(j2);

        HashMap<String, String> jobs = new HashMap<>();
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

        service.validateCablingTasks(region, building, rackSerial, rackUnit, devices, metricsScope);

        // Verify job creation args
        assertEquals(JobType.HEALTH_CHECK, jobTypeCap.getValue());
        assertEquals(rackSerial, rackSerialCap.getValue());
        assertEquals(region, regionCap.getValue());
        assertEquals(List.of("dev2"), devicesCap.getValue());

        String payload = payloadCap.getValue();
        // Basic payload assertions
        org.junit.jupiter.api.Assertions.assertTrue(payload.contains("\"testSuites\""));
        org.junit.jupiter.api.Assertions.assertTrue(
                payload.contains("\"rackNumber\":\"" + rackUnit + "\""));
        org.junit.jupiter.api.Assertions.assertTrue(
                payload.contains("\"building\":\"" + building + "\""));

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
        String region = "reg2";
        String building = "bld2";
        String rackSerial = "RSN-2";
        String rackUnit = "RU-7";

        NcpJobDetails j1 =
                NcpJobDetails.builder()
                        .rackSerial(rackSerial)
                        .deviceName("d1")
                        .jobId("jid-1")
                        .jobStatus(JobStatus.COMPLETED)
                        .build();
        NcpJobDetails j2 =
                NcpJobDetails.builder()
                        .rackSerial(rackSerial)
                        .deviceName("d2")
                        .jobId("jid-2")
                        .jobStatus(JobStatus.IN_PROGRESS) // will be filtered out
                        .build();
        when(ncpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(List.of(j1, j2));
        when(ncpJobDetailsDao.getNcpJobDetails("d1")).thenReturn(j1);
        when(ncpJobDetailsDao.getNcpJobDetails("d2")).thenReturn(j2);

        HashMap<String, String> jobs = new HashMap<>();
        jobs.put("d1", "job-a");

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
        // After filtering, only d1 remains
        assertEquals(List.of("d1"), devicesCap.getValue());

        String payload = payloadCap.getValue();
        org.junit.jupiter.api.Assertions.assertTrue(payload.contains("\"testSuites\""));
        org.junit.jupiter.api.Assertions.assertTrue(
                payload.contains("\"rackNumber\":\"" + rackUnit + "\""));
        org.junit.jupiter.api.Assertions.assertTrue(
                payload.contains("\"building\":\"" + building + "\""));

        verify(ncpJobDetailsDao).addUpdateNcpJobDetails(jobs, rackSerial, metricsScope);
    }

    @Test
    void validateCablingTasks_withEmptyDevicesAndDaoEmpty_usesPerRackPayload_dryRunTrue() {
        String region = "reg3";
        String building = "bld3";
        String rackSerial = "RSN-3";
        String rackUnit = "RU-9";

        when(ncpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(List.of());

        HashMap<String, String> jobs = new HashMap<>();
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
        org.junit.jupiter.api.Assertions.assertTrue(
                payload.contains("\"rack_number\":\"" + rackUnit + "\""));
        org.junit.jupiter.api.Assertions.assertTrue(
                payload.contains("\"building\":\"" + building + "\""));
        org.junit.jupiter.api.Assertions.assertTrue(payload.contains("\"dry_run\":true"));

        org.junit.jupiter.api.Assertions.assertTrue(devicesCap.getValue().isEmpty());

        verify(ncpJobDetailsDao).addUpdateNcpJobDetails(jobs, rackSerial, metricsScope);
    }

    @Test
    void validateCablingTasks_perRackJobAlreadyInProgress_skipsCreateJobs() {
        String region = "reg4";
        String building = "bld4";
        String rackSerial = "RSN-4";
        String rackUnit = "RU-10";

        // No devices provided and none in DAO -> PER_RACK job type
        when(ncpJobDetailsDao.getNcpJobDetailsForRack(rackSerial)).thenReturn(List.of());

        // Existing rack-level job is IN_PROGRESS -> should early return
        NcpJobDetails rackJob =
                NcpJobDetails.builder()
                        .rackSerial(rackSerial)
                        .deviceName(rackSerial) // keyed by rack serial for rack-level job
                        .jobId("jid-rack")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();
        when(ncpJobDetailsDao.getNcpJobDetails(rackSerial)).thenReturn(rackJob);

        service.validateCablingTasks(
                region, building, rackSerial, rackUnit, List.of(), metricsScope);

        verify(ncpClientHelper, never()).createJobs(any(), any(), any(), anyList(), any(), any());
        verify(ncpJobDetailsDao, never()).addUpdateNcpJobDetails(any(), anyString(), any());
    }

    @Test
    void validateCablingTasks_allDevicesFilteredByInProgress_skipsCreateJobs() {
        String region = "reg5";
        String building = "bld5";
        String rackSerial = "RSN-5";
        String rackUnit = "RU-11";

        List<String> devices = List.of("d1", "d2");
        NcpJobDetails d1 =
                NcpJobDetails.builder()
                        .rackSerial(rackSerial)
                        .deviceName("d1")
                        .jobId("jid-1")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();
        NcpJobDetails d2 =
                NcpJobDetails.builder()
                        .rackSerial(rackSerial)
                        .deviceName("d2")
                        .jobId("jid-2")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();
        when(ncpJobDetailsDao.getNcpJobDetails("d1")).thenReturn(d1);
        when(ncpJobDetailsDao.getNcpJobDetails("d2")).thenReturn(d2);

        service.validateCablingTasks(region, building, rackSerial, rackUnit, devices, metricsScope);

        verify(ncpClientHelper, never()).createJobs(any(), any(), any(), anyList(), any(), any());
        verify(ncpJobDetailsDao, never()).addUpdateNcpJobDetails(any(), anyString(), any());
    }

    @Test
    void validateCablingTasks_onJsonError_throwsRenderableException_InternalError()
            throws Exception {
        String region = "reg6";
        String building = "bld6";
        String rackSerial = "RSN-6";
        String rackUnit = "RU-12";
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

        when(ncpJobDetailsDao.getNcpJobDetails(device))
                .thenReturn(curr) // initial
                .thenReturn(
                        NcpJobDetails.builder()
                                .rackSerial("RSN")
                                .deviceName(device)
                                .jobId("jid-123")
                                .jobStatus(JobStatus.IN_PROGRESS) // not DEVICE_UNREACHABLE
                                .build());

        // Build output structure matching service expectations
        Map<String, Map<String, List<Map<String, String>>>> output = new HashMap<>();
        output.put(device, Map.of("test", List.of(Map.of("k", "v"))));
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
                    .addUpdateValidationFailureResultsForDevices(
                            eq("RSN"), eq(output), eq(addResultsScope));
            verify(metricsScope).recordSuccess();

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
                                .jobStatus(JobStatus.DEVICE_UNREACHABLE)
                                .build());

        Map<String, Map<String, List<Map<String, String>>>> output = new HashMap<>();
        output.put(device, Map.of());
        when(ncpClientHelper.getNcpJobOutput("jid-999", "reg", "RSN", "RU")).thenReturn(output);

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
                    .addUpdateValidationFailureResultsForDevices(
                            eq("RSN"), eq(output), eq(addResultsScope));
            // Final status shouldn't be overwritten to COMPLETED
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
    void getValidationJobStatus_fetchesAndReturnsMap_fromDao_andCallsUpdate() {
        String region = "reg";
        String rackSerial = "RSN-10";
        String rackUnit = "RU-55";

        when(ncpClientHelper.fetchJobStatus(rackSerial, region))
                .thenReturn(Map.of("dev1", JobStatus.IN_PROGRESS));

        when(ncpJobDetailsDao.getNcpJobDetails("dev1"))
                .thenReturn(
                        NcpJobDetails.builder()
                                .rackSerial(rackSerial)
                                .deviceName("dev1")
                                .jobId("jid-xyz")
                                .jobStatus(JobStatus.IN_PROGRESS)
                                .build());

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

    // ========== getValidationFailuresByRack ==========

    @Test
    void getValidationFailuresByRack_wrapsDaoResult() {
        String rackSerial = "RSN-20";
        Map<String, Object> daoResult = Map.of("devX", Map.of("k", "v"));

        when(validationFailureResultDao.getValidationFailuresByRack(rackSerial))
                .thenReturn(daoResult);

        Object resultObj = service.getValidationFailuresByRack(rackSerial);
        assertEquals(
                Map.of(rackSerial, daoResult),
                resultObj,
                "Service should wrap DAO result under rackSerial key");
    }
}

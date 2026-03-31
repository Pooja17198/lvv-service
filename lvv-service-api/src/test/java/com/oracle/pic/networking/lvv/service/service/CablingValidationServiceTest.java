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
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class CablingValidationServiceTest {

    private static final String REGION = "reg";
    private static final String BUILDING = "bld";
    private static final String RACK_SERIAL = "RSN";
    private static final String RACK_UNIT = "RU";

    @org.mockito.Mock private NcpClientHelper ncpClientHelper;
    @org.mockito.Mock private JiraSDService jiraSDService;
    @org.mockito.Mock private ValidationFailureResultDao validationFailureResultDao;
    @org.mockito.Mock private NcpJobDetailsDao ncpJobDetailsDao;
    @org.mockito.Mock private MetricsScope metricsScope;

    private CablingValidationService service;

    private static NcpClientHelper.ValidationJobRuntimeInfo runtimeInfo(
            JobStatus status, String jobType, String startDate, String endDate) {
        return NcpClientHelper.ValidationJobRuntimeInfo.builder()
                .jobStatus(status)
                .jobType(jobType)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

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

    @Test
    void validateCablingTasks_withNonEmptyDevices_filtersInProgress_andCreatesHealthCheck() {
        List<String> devices = List.of("dev1", "dev2");

        NcpJobDetails j1 =
                NcpJobDetails.builder()
                        .rackSerial(RACK_SERIAL)
                        .deviceName("dev1")
                        .jobId("jid-1")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();
        NcpJobDetails j2 =
                NcpJobDetails.builder()
                        .rackSerial(RACK_SERIAL)
                        .deviceName("dev2")
                        .jobId("jid-2")
                        .jobStatus(JobStatus.COMPLETED)
                        .build();
        when(ncpJobDetailsDao.getNcpJobDetails("dev1")).thenReturn(j1);
        when(ncpJobDetailsDao.getNcpJobDetails("dev2")).thenReturn(j2);

        HashMap<String, String> jobs = new HashMap<>();
        jobs.put("dev2", "job-2");

        when(ncpClientHelper.createJobs(
                        anyString(), anyString(), anyString(), anyList(), any(), anyString()))
                .thenReturn(jobs);

        service.validateCablingTasks(
                REGION, BUILDING, RACK_SERIAL, RACK_UNIT, devices, metricsScope);

        verify(ncpJobDetailsDao)
                .addUpdateNcpJobDetails(eq(jobs), eq(RACK_SERIAL), eq(metricsScope));
        verify(metricsScope).emit(MetricNames.ValidateCables.ValidateCable.name(), 1.0);
    }

    @Test
    void validateCablingTasks_withEmptyDevicesAndDaoEmpty_usesPerRackPayload() {
        when(ncpJobDetailsDao.getNcpJobDetailsForRack(RACK_SERIAL)).thenReturn(List.of());

        HashMap<String, String> jobs = new HashMap<>();
        jobs.put(RACK_SERIAL, "job-per-rack");
        when(ncpClientHelper.createJobs(
                        anyString(), anyString(), anyString(), anyList(), any(), anyString()))
                .thenReturn(jobs);

        service.validateCablingTasks(
                REGION, BUILDING, RACK_SERIAL, RACK_UNIT, List.of(), metricsScope);

        verify(ncpJobDetailsDao)
                .addUpdateNcpJobDetails(eq(jobs), eq(RACK_SERIAL), eq(metricsScope));
    }

    @Test
    void validateCablingTasks_onJsonError_throwsRenderableException_InternalError()
            throws Exception {
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
                                        REGION,
                                        BUILDING,
                                        RACK_SERIAL,
                                        RACK_UNIT,
                                        List.of("d1"),
                                        metricsScope));
        assertEquals(ErrorCode.InternalError, ex.getErrorCode());
    }

    @Test
    void updateValidationJobStatus_whenFailed_setsFailedAndUpdatesDao() {
        String device = "devA";
        Map<String, NcpClientHelper.ValidationJobRuntimeInfo> status =
                Map.of(device, runtimeInfo(JobStatus.FAILED, JobType.HEALTH_CHECK, null, null));

        NcpJobDetails curr =
                NcpJobDetails.builder()
                        .rackSerial(RACK_SERIAL)
                        .deviceName(device)
                        .jobId("jid")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();

        when(ncpJobDetailsDao.getNcpJobDetails(device)).thenReturn(curr);

        service.updateValidationJobStatus(
                status, RACK_SERIAL, REGION, BUILDING, RACK_UNIT, false, metricsScope);

        assertEquals(JobStatus.FAILED, curr.getJobStatus());
        verify(ncpJobDetailsDao).updateNcpJobDetails(curr);
    }

    @Test
    void updateValidationJobStatus_completedHealthCheck_emitsDeviceValidationDurationMetric() {
        String device = "devMetric";
        Map<String, NcpClientHelper.ValidationJobRuntimeInfo> status =
                Map.of(
                        device,
                        runtimeInfo(
                                JobStatus.COMPLETED,
                                JobType.HEALTH_CHECK,
                                "2026-03-26T20:00:00Z",
                                "2026-03-26T20:00:05Z"));

        NcpJobDetails curr =
                NcpJobDetails.builder()
                        .rackSerial("RSN-M1")
                        .deviceName(device)
                        .jobId("jid-m1")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();

        when(ncpJobDetailsDao.getNcpJobDetails(device))
                .thenReturn(curr)
                .thenReturn(
                        NcpJobDetails.builder()
                                .rackSerial("RSN-M1")
                                .deviceName(device)
                                .jobId("jid-m1")
                                .jobStatus(JobStatus.IN_PROGRESS)
                                .build());

        Map<String, Map<String, List<Map<String, String>>>> output = new HashMap<>();
        output.put(device, Map.of());
        when(ncpClientHelper.getNcpJobOutput("jid-m1", REGION, "RSN-M1", "RU-1"))
                .thenReturn(output);

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

            service.updateValidationJobStatus(
                    status, "RSN-M1", REGION, BUILDING, "RU-1", false, metricsScope);

            verify(metricsScope).withDimension("device", device);
            verify(metricsScope)
                    .emit(
                            eq(MetricNames.ValidationDuration.DeviceValidationDuration.name()),
                            eq(5000.0));
        }
    }

    @Test
    void updateValidationJobStatus_completedRackValidation_emitsRackValidationDurationMetric() {
        String rackSerial = "RSN-R1";
        Map<String, NcpClientHelper.ValidationJobRuntimeInfo> status =
                Map.of(
                        rackSerial,
                        runtimeInfo(
                                JobStatus.COMPLETED,
                                JobType.PER_RACK_VALIDATION_JOB,
                                "2026-03-26T20:00:00Z",
                                "2026-03-26T20:00:10Z"));

        NcpJobDetails curr =
                NcpJobDetails.builder()
                        .rackSerial(rackSerial)
                        .deviceName(rackSerial)
                        .jobId("jid-rack")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();

        when(ncpJobDetailsDao.getNcpJobDetails(rackSerial))
                .thenReturn(curr)
                .thenReturn(
                        NcpJobDetails.builder()
                                .rackSerial(rackSerial)
                                .deviceName(rackSerial)
                                .jobId("jid-rack")
                                .jobStatus(JobStatus.IN_PROGRESS)
                                .build());

        Map<String, Map<String, List<Map<String, String>>>> output = new HashMap<>();
        output.put(rackSerial, Map.of());
        when(ncpClientHelper.getNcpJobOutput("jid-rack", REGION, rackSerial, "RU-9"))
                .thenReturn(output);

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

            service.updateValidationJobStatus(
                    status, rackSerial, REGION, BUILDING, "RU-9", false, metricsScope);

            verify(metricsScope)
                    .emit(
                            eq(MetricNames.ValidationDuration.RackValidationDuration.name()),
                            eq(10000.0));
            verify(metricsScope, never()).withDimension(eq("device"), anyString());
        }
    }

    @Test
    void updateValidationJobStatus_completedWithMissingTimestamps_skipsDurationMetric() {
        String device = "devMissingTs";
        Map<String, NcpClientHelper.ValidationJobRuntimeInfo> status =
                Map.of(
                        device,
                        runtimeInfo(
                                JobStatus.COMPLETED,
                                JobType.HEALTH_CHECK,
                                null,
                                "2026-03-26T20:00:05Z"));

        NcpJobDetails curr =
                NcpJobDetails.builder()
                        .rackSerial("RSN-M2")
                        .deviceName(device)
                        .jobId("jid-m2")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();

        when(ncpJobDetailsDao.getNcpJobDetails(device))
                .thenReturn(curr)
                .thenReturn(
                        NcpJobDetails.builder()
                                .rackSerial("RSN-M2")
                                .deviceName(device)
                                .jobId("jid-m2")
                                .jobStatus(JobStatus.IN_PROGRESS)
                                .build());

        Map<String, Map<String, List<Map<String, String>>>> output = new HashMap<>();
        output.put(device, Map.of());
        when(ncpClientHelper.getNcpJobOutput("jid-m2", REGION, "RSN-M2", "RU-2"))
                .thenReturn(output);

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

            service.updateValidationJobStatus(
                    status, "RSN-M2", REGION, BUILDING, "RU-2", false, metricsScope);

            verify(metricsScope, never())
                    .emit(
                            eq(MetricNames.ValidationDuration.DeviceValidationDuration.name()),
                            anyDouble());
            verify(metricsScope, never())
                    .emit(
                            eq(MetricNames.ValidationDuration.RackValidationDuration.name()),
                            anyDouble());
        }
    }

    @Test
    void updateValidationJobStatus_completedWithMalformedTimestamp_skipsDurationMetric() {
        String device = "devBadTs";
        Map<String, NcpClientHelper.ValidationJobRuntimeInfo> status =
                Map.of(
                        device,
                        runtimeInfo(
                                JobStatus.COMPLETED,
                                JobType.HEALTH_CHECK,
                                "bad-start",
                                "2026-03-26T20:00:05Z"));

        NcpJobDetails curr =
                NcpJobDetails.builder()
                        .rackSerial("RSN-B1")
                        .deviceName(device)
                        .jobId("jid-b1")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();

        when(ncpJobDetailsDao.getNcpJobDetails(device))
                .thenReturn(curr)
                .thenReturn(
                        NcpJobDetails.builder()
                                .rackSerial("RSN-B1")
                                .deviceName(device)
                                .jobId("jid-b1")
                                .jobStatus(JobStatus.IN_PROGRESS)
                                .build());

        Map<String, Map<String, List<Map<String, String>>>> output = new HashMap<>();
        output.put(device, Map.of());
        when(ncpClientHelper.getNcpJobOutput("jid-b1", REGION, "RSN-B1", "RU-4"))
                .thenReturn(output);

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

            service.updateValidationJobStatus(
                    status, "RSN-B1", REGION, BUILDING, "RU-4", false, metricsScope);
            verify(metricsScope, never())
                    .emit(
                            eq(MetricNames.ValidationDuration.RackValidationDuration.name()),
                            anyDouble());
            verify(metricsScope, never())
                    .emit(
                            eq(MetricNames.ValidationDuration.DeviceValidationDuration.name()),
                            anyDouble());
        }
    }

    @Test
    void updateValidationJobStatus_completedWithNegativeDuration_skipsDurationMetric() {
        String device = "devNegative";
        Map<String, NcpClientHelper.ValidationJobRuntimeInfo> status =
                Map.of(
                        device,
                        runtimeInfo(
                                JobStatus.COMPLETED,
                                JobType.HEALTH_CHECK,
                                "2026-03-26T20:00:10Z",
                                "2026-03-26T20:00:05Z"));

        NcpJobDetails curr =
                NcpJobDetails.builder()
                        .rackSerial("RSN-N1")
                        .deviceName(device)
                        .jobId("jid-n1")
                        .jobStatus(JobStatus.IN_PROGRESS)
                        .build();

        when(ncpJobDetailsDao.getNcpJobDetails(device))
                .thenReturn(curr)
                .thenReturn(
                        NcpJobDetails.builder()
                                .rackSerial("RSN-N1")
                                .deviceName(device)
                                .jobId("jid-n1")
                                .jobStatus(JobStatus.IN_PROGRESS)
                                .build());

        Map<String, Map<String, List<Map<String, String>>>> output = new HashMap<>();
        output.put(device, Map.of());
        when(ncpClientHelper.getNcpJobOutput("jid-n1", REGION, "RSN-N1", "RU-5"))
                .thenReturn(output);

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

            service.updateValidationJobStatus(
                    status, "RSN-N1", REGION, BUILDING, "RU-5", false, metricsScope);
            verify(metricsScope, never())
                    .emit(
                            eq(MetricNames.ValidationDuration.RackValidationDuration.name()),
                            anyDouble());
            verify(metricsScope, never())
                    .emit(
                            eq(MetricNames.ValidationDuration.DeviceValidationDuration.name()),
                            anyDouble());
        }
    }

    @Test
    void getValidationJobStatus_fetchesAndReturnsMap_fromDao() {
        String rackSerial = "RSN-10";
        String rackUnit = "RU-55";

        when(ncpClientHelper.fetchJobStatus(rackSerial, REGION))
                .thenReturn(
                        Map.of(
                                "dev1",
                                runtimeInfo(
                                        JobStatus.IN_PROGRESS, JobType.HEALTH_CHECK, null, null)));

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

        Map<String, JobStatus> result =
                service.getValidationJobStatus(
                        metricsScope, REGION, BUILDING, rackSerial, rackUnit, false);

        assertEquals(2, result.size());
        assertEquals(JobStatus.FAILED, result.get("dev1"));
        assertEquals(JobStatus.COMPLETED, result.get("dev2"));
    }

    @Test
    void getValidationFailuresByRack_wrapsDaoResult() {
        String rackSerial = "RSN-20";
        Map<String, Object> daoResult = Map.of("devX", Map.of("k", "v"));

        when(validationFailureResultDao.getValidationFailuresByRack(rackSerial))
                .thenReturn(daoResult);

        Object resultObj = service.getValidationFailuresByRack(rackSerial);
        assertEquals(Map.of(rackSerial, daoResult), resultObj);
    }
}

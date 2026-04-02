package com.oracle.pic.networking.lvv.service.resources;

import com.google.inject.Inject;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractCablingValidationResource;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.kiev.JobStatus;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResultDao;
import com.oracle.pic.networking.lvv.service.model.DeviceValidationStatus;
import com.oracle.pic.networking.lvv.service.service.CablingValidationService;
import com.oracle.pic.networking.lvv.service.utils.DownloadExcelReportBuilder;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
public class CablingValidationResource extends AbstractCablingValidationResource {

    private final CablingValidationService cablingValidationService;
    private final ValidationFailureResultDao validationFailureResultDao;
    private final ResourceModelTransformer resourceModelTransformer;

    @Inject
    protected CablingValidationResource(
            CablingValidationService cablingValidationService,
            ValidationFailureResultDao validationFailureResultDao,
            ResourceModelTransformer resourceModelTransformer) {
        this.cablingValidationService = cablingValidationService;
        this.validationFailureResultDao = validationFailureResultDao;
        this.resourceModelTransformer = resourceModelTransformer;
    }

    @Override
    public void validateCables(
            String regionName,
            String building,
            String rackSerialNumber,
            String rackNumber,
            List<String> deviceNames,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.VALIDATE_CABLES.name())) {

            // NULL Checks
            List<String> missing = new ArrayList<>();

            if (rackSerialNumber == null || rackSerialNumber.isBlank()) {
                missing.add("rackSerialNumber");
            }

            if (rackNumber == null || rackNumber.isBlank()) {
                missing.add("rackNumber");
            }

            if (regionName == null || regionName.isBlank()) {
                missing.add("regionName");
            }

            if (building == null || building.isBlank()) {
                missing.add("building");
            }

            if (!missing.isEmpty()) {
                scope.emit(MetricNames.ValidateCables.MissingParameters.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.MissingParameter,
                        "Missing or empty parameters: " + String.join(", ", missing));
            }

            log.info("Starting validations on selected devices for rack {}", rackSerialNumber);

            this.cablingValidationService.validateCablingTasks(
                    regionName, building, rackSerialNumber, rackNumber, deviceNames, scope);

            scope.recordSuccess();
        }
    }

    @Override
    public Object getValidationFailures(
            String regionName,
            String rackSerial,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.GET_VALIDATION_RESULTS.name())) {

            if (rackSerial == null || rackSerial.isEmpty()) {
                scope.emit(MetricNames.GetValidationResults.RackSerialNull.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.InvalidParameter, "Rack Serial cannot be empty");
            }

            scope.withDimension("region", GeneralUtils.getRegionInternalName(regionName));
            scope.withDimension("rackSerial", rackSerial);
            scope.emit(MetricNames.GetValidationResults.GetValidationResult.name(), 1.0);

            Object validationFailures =
                    cablingValidationService.getValidationFailuresByRack(rackSerial);

            scope.recordSuccess();

            return validationFailures;
        }
    }

    @Override
    public byte[] downloadValidationFailures(
            String rackSerial,
            String format,
            String regionName,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope =
                MetricsScope.create(
                        MetricNames.MetricScopeNames.DOWNLOAD_VALIDATION_RESULTS.name())) {

            log.info("Starting downloadValidations for rack {}", rackSerial);

            scope.withDimension("region", GeneralUtils.getRegionInternalName(regionName));

            if (format != null && !format.isBlank()) {
                String normalized = format.trim().toUpperCase(Locale.ROOT);
                if (!"XLSX".equals(normalized) && !"EXCEL".equals(normalized)) {
                    throw new RenderableException(
                            ErrorCode.InvalidParameter,
                            "Unsupported download format: " + format + ". Supported format: xlsx");
                }
            }

            scope.emit(MetricNames.ValidateCables.DownloadExcel.name(), 1.0);

            Object resultsWrapper =
                    cablingValidationService.getValidationFailuresByRack(rackSerial);

            byte[] workbookBytes =
                    DownloadExcelReportBuilder.buildWorkbook(resultsWrapper, rackSerial);
            Response response =
                    Response.ok(workbookBytes)
                            .type(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            .header(
                                    "Content-Disposition",
                                    "attachment; filename=\"validationFailureResults_"
                                            + rackSerial
                                            + ".xlsx\"")
                            .build();
            scope.recordSuccess();
            throw new WebApplicationException(response);
        }
    }

    @Override
    public List<DeviceValidationStatus> getValidationJobStatus(
            String regionName,
            String rackSerialNumber,
            String rackNumber,
            Boolean lastAttempt,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope =
                MetricsScope.create(
                        MetricNames.MetricScopeNames.GET_NCP_VALIDATION_JOB_STATUS.name())) {

            log.info("Fetching NCP Validation Job Status for rack {}", rackSerialNumber);

            scope.withDimension("region", GeneralUtils.getRegionInternalName(regionName));

            if (rackSerialNumber == null || rackSerialNumber.isEmpty()) {
                scope.emit(MetricNames.GetValidationJobStatus.RackSerialNull.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.InvalidParameter, "Rack serial cannot be empty");
            }

            if (rackNumber == null || rackNumber.isEmpty()) {
                scope.emit(MetricNames.GetValidationJobStatus.RackNumberNull.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.InvalidParameter, "Rack number cannot be empty");
            }

            scope.emit(MetricNames.GetValidationJobStatus.GetJobStatus.name(), 1.0);

            Map<String, JobStatus> jobStatuses =
                    this.cablingValidationService.getValidationJobStatus(
                            scope, regionName, rackSerialNumber, rackNumber, lastAttempt);

            scope.recordSuccess();
            return resourceModelTransformer.toModel(jobStatuses);
        }
    }
}

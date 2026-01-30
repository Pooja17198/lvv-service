package com.oracle.pic.networking.lvv.service.resources;

import com.google.inject.Inject;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractCablingValidationResource;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.kiev.JobStatus;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResultDao;
import com.oracle.pic.networking.lvv.service.model.DeviceValidationStatus;
import com.oracle.pic.networking.lvv.service.model.ValidationFailureDisplayDTO;
import com.oracle.pic.networking.lvv.service.service.CablingValidationService;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
public class CablingValidationResource extends AbstractCablingValidationResource {

    private final CablingValidationService cablingValidationService;
    private final ValidationFailureResultDao validationFailureResultDao;
    private final ResourceModelTransformer resourceModelTransformer;

    @Context
    @Getter(AccessLevel.PRIVATE)
    private HttpServletResponse httpServletResponse;

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
    public List<ValidationFailureDisplayDTO> getValidationFailures(
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

            List<ValidationFailureResult> rawResults =
                    validationFailureResultDao.getValidationFailuresByRack(rackSerial, true);

            List<ValidationFailureDisplayDTO> dtoList =
                    rawResults.stream()
                            .map(r -> resourceModelTransformer.toModel(r, false))
                            .toList();

            scope.recordSuccess();
            return dtoList;
        }
    }

    @Override
    public void downloadValidationFailures(
            String rackSerial,
            String regionName,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope =
                MetricsScope.create(
                        MetricNames.MetricScopeNames.DOWNLOAD_VALIDATION_RESULTS.name())) {

            scope.withDimension("region", GeneralUtils.getRegionInternalName(regionName));
            scope.emit(MetricNames.ValidateCables.DownloadCsv.name(), 1.0);

            List<ValidationFailureResult> rawResults =
                    validationFailureResultDao.getValidationFailuresByRack(rackSerial, true);

            List<ValidationFailureDisplayDTO> results =
                    rawResults.stream()
                            .map(r -> resourceModelTransformer.toModel(r, true))
                            .toList();

            StringWriter writer = new StringWriter();
            try {
                StatefulBeanToCsv<ValidationFailureDisplayDTO> beanToCsv =
                        new StatefulBeanToCsvBuilder<ValidationFailureDisplayDTO>(writer).build();
                beanToCsv.write(results);
            } catch (com.opencsv.exceptions.CsvDataTypeMismatchException
                    | com.opencsv.exceptions.CsvRequiredFieldEmptyException e) {
                throw new WebApplicationException("Failed to generate CSV", e);
            }

            Response response =
                    Response.ok(writer.toString())
                            .header(
                                    "Content-Disposition",
                                    "attachment; filename=\"validationFailureResults_"
                                            + rackSerial
                                            + ".csv\"")
                            .type("text/csv")
                            .build();
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

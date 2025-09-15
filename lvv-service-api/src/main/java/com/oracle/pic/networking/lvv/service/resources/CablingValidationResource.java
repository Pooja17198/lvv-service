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
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResult;
import com.oracle.pic.networking.lvv.service.kiev.ValidationFailureResultDao;
import com.oracle.pic.networking.lvv.service.model.ValidationFailureDisplayDTO;
import com.oracle.pic.networking.lvv.service.service.CablingValidationService;
import com.oracle.pic.networking.lvv.service.utils.GeneralUtils;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
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
    public String validateCables(
            String building,
            String rackSerialNumber,
            List<String> deviceNames,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.VALIDATE_CABLES.name())) {

            // NULL Checks
            List<String> missing = new ArrayList<>();

            if (building == null || building.isBlank()) {
                missing.add("building");
            }

            if (rackSerialNumber == null || rackSerialNumber.isBlank()) {
                missing.add("rackSerialNumber");
            }

            if (!missing.isEmpty()) {
                scope.emit(MetricNames.ValidateCables.MissingParameters.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.MissingParameter,
                        "Missing or empty parameters: " + String.join(", ", missing));
            }

            scope.withDimension("buildingName", building);
            scope.withDimension("rackSerialNumber", rackSerialNumber);

            String jobId =
                    this.cablingValidationService.validateCablingTasks(
                            building, rackSerialNumber, deviceNames, scope);

            scope.recordSuccess();
            return jobId;
        }
    }

    @Override
    public List<ValidationFailureDisplayDTO> getValidationFailures(
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

            scope.withDimension("rackSerial", rackSerial);
            scope.emit(MetricNames.GetValidationResults.GetValidationResult.name(), 1.0);

            List<ValidationFailureResult> rawResults =
                    validationFailureResultDao.getValidationFailuresByRack(rackSerial, true);

            List<ValidationFailureDisplayDTO> dtoList =
                    rawResults.stream().map(resourceModelTransformer::toModel).toList();

            scope.recordSuccess();
            return dtoList;
        }
    }

    @Override
    public void downloadValidationFailures(
            String rackSerial,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope =
                MetricsScope.create(
                        MetricNames.MetricScopeNames.DOWNLOAD_VALIDATION_RESULTS.name())) {

            scope.emit(MetricNames.ValidateCables.DownloadCsv.name(), 1.0);

            List<ValidationFailureResult> rawResults =
                    validationFailureResultDao.getValidationFailuresByRack(rackSerial, true);

            List<ValidationFailureDisplayDTO> results =
                    rawResults.stream().map(resourceModelTransformer::toModel).toList();

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
    public String getValidationJobStatus(
            String jobId,
            String building,
            String rackSerialNumber,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope =
                MetricsScope.create(
                        MetricNames.MetricScopeNames.GET_NCP_VALIDATION_JOB_STATUS.name())) {

            log.info("Fetching NCP Validation Job Status for Job Id {}", jobId);

            if (jobId == null || jobId.isEmpty()) {
                scope.emit(MetricNames.GetValidationJobStatus.JobIdNull.name(), 1.0);
                throw new RenderableException(ErrorCode.InvalidParameter, "Job ID cannot be empty");
            }

            if (building == null || building.isEmpty()) {
                scope.emit(MetricNames.GetValidationJobStatus.BuildingNull.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.InvalidParameter, "Building cannot be empty");
            }

            if (rackSerialNumber == null || rackSerialNumber.isEmpty()) {
                scope.emit(MetricNames.GetValidationJobStatus.RackSerialNull.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.InvalidParameter, "Rack serial cannot be empty");
            }

            scope.withDimension("jobId", jobId);
            scope.emit(MetricNames.GetValidationJobStatus.GetJobStatus.name(), 1.0);

            // Get the region where we are fetching the Job status for using the building name
            String region = GeneralUtils.getRegionFromBuilding(building);

            String status =
                    this.cablingValidationService.getValidationJobStatus(
                            jobId, scope, region, rackSerialNumber);

            scope.recordSuccess();
            return status;
        }
    }
}

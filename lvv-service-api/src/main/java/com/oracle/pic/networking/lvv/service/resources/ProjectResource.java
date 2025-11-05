package com.oracle.pic.networking.lvv.service.resources;

import com.google.inject.Inject;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractProjectsResource;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.networking.lvv.service.model.Project;
import com.oracle.pic.networking.lvv.service.model.PutProjectRequest;
import com.oracle.pic.networking.lvv.service.service.ProjectService;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/*
* NOTE: when creating new resources, don't forget to add it to the list of resources in
* com.oracle.pic.networking.lvv.service.LvvServiceApi

* NOTE: All resource methods defined in class are configured to have the 2XX, 4xx, 5xx, and
* time metrics automatically instrumented and emitted (by setting resourcePackagePrefix in metricsConfig)
* Don't forget to update resourcePackagePrefix if you update the package.
* See https://confluence.oci.oraclecorp.com/x/ThJuBQ for details.
*
* The @ServiceName value is part of the Observability Standardization, for more details
* and guidance on the value to use for this annotation, see
* https://confluence.oci.oraclecorp.com/display/OBSRV/Observability+Standardization+Onboarding
*/

@Slf4j
@ToString
public class ProjectResource extends AbstractProjectsResource {

    private static final String SORT_BY_ENUM_TIMECREATED = "timeCreated";
    private static final String SORT_BY_ENUM_DISPLAYNAME = "displayName";
    private static final int DEFAULT_PAGE_SIZE = 100;

    private ProjectService projectService;

    @Context
    @Getter(AccessLevel.PRIVATE)
    private HttpServletResponse httpServletResponse;

    @Inject
    protected ProjectResource(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public Boolean createProject(
            PutProjectRequest value,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.ADD_PROJECT_ITEM.name())
                        .withDimension("projectId", value.getProject().getProjectId())
                        .withDimension("region", value.getProject().getRegion())) {

            log.info("Creating project {}", value);

            if (value.getProject().getBlocks().isEmpty()) {
                log.info("At least 1 block needs to be assigned while creating a project");
                scope.emit(MetricNames.AddProjectItem.BlockDetailsEmpty.name(), 1.0);
                return false;
            }

            projectService.createProject(
                    value.getProject().getProjectId(),
                    value.getProject().getVendorName(),
                    value.getProject().getRegion(),
                    value.getProject().getBuilding(),
                    value.getProject().getBlocks(),
                    scope);

            scope.recordSuccess();
            return true;
        }
    }

    @Override
    public void updateProject(
            PutProjectRequest value,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.UPDATE_PROJECT_ITEM.name())
                        .withDimension("projectId", value.getProject().getProjectId())
                        .withDimension("region", value.getProject().getRegion())) {

            log.info("Updating project {}", value);

            if (value.getProject().getBlocks().isEmpty()) {
                log.info("At least 1 block needs to be assigned while updating a project");
                scope.emit(MetricNames.UpdateProjectItem.BlockDetailsEmpty.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.InvalidParameter,
                        "At least 1 block needs to be assigned while updating a project");
            }

            projectService.updateProject(
                    value.getProject().getProjectId(),
                    value.getProject().getVendorName(),
                    value.getProject().getRegion(),
                    value.getProject().getBuilding(),
                    value.getProject().getBlocks(),
                    scope);

            scope.recordSuccess();
        }
    }

    @Override
    public void deleteProject(
            String projectId,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.DELETE_PROJECT_ITEM.name())) {

            if (projectId.isEmpty()) {
                log.error("Project ID cannot be empty");
                scope.emit(MetricNames.DeleteProjectItem.ProjectIdEmpty.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.InvalidParameter, "Project ID cannot be empty");
            }

            scope.withDimension("projectId", projectId);

            this.projectService.deleteProject(projectId, scope);
            scope.recordSuccess();
        }
    }

    @Override
    public Project getProject(
            String projectId,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.GET_PROJECT_ITEM.name())) {

            if (projectId == null || projectId.isEmpty()) {
                log.error("Project ID is empty");
                scope.emit(MetricNames.GetProjectItem.ProjectIdEmpty.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.MissingParameter, "Project ID cannot be empty");
            }

            Project project = projectService.getProject(projectId, scope);
            scope.recordSuccess();
            return project;
        }
    }

    // This method is called when a vendor logs in with his given credentials. We provide him list
    // of all projects assigned to him.
    @Override
    public List<Project> getProjectList(
            String vendorName,
            String regionName,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.GET_PROJECT_ITEM.name())) {

            List<Project> projects;

            boolean hasVendor = vendorName != null && !vendorName.isEmpty();
            boolean hasRegion = regionName != null && !regionName.isEmpty();

            if (!hasVendor && !hasRegion) {
                log.info("Fetching all the projects");
                scope.emit(MetricNames.GetProjectItem.GetAllProjects.name(), 1.0);
                projects = projectService.getProjectList();
            } else if (hasVendor && !hasRegion) {
                log.info("Fetching projects for vendor {}", vendorName);
                scope.withDimension("vendorName", vendorName);
                scope.emit(MetricNames.GetProjectItem.GetProjectsForVendor.name(), 1.0);
                projects = projectService.getProjectListByVendor(vendorName);
            } else if (!hasVendor && hasRegion) {
                log.info("Fetching projects for region {}", regionName);
                scope.withDimension("regionName", regionName);
                scope.emit(MetricNames.GetProjectItem.GetAllProjects.name(), 1.0);
                projects = projectService.getProjectListByRegion(regionName);
            } else {
                log.info("Fetching projects for vendor {} in region {}", vendorName, regionName);
                scope.withDimension("vendorName", vendorName);
                scope.withDimension("regionName", regionName);
                scope.emit(MetricNames.GetProjectItem.GetProjectsForVendor.name(), 1.0);
                projects = projectService.getProjectListByVendorAndRegion(vendorName, regionName);
            }
            scope.recordSuccess();
            return projects;
        }
    }

    // This method is called when a user logs in with his BOAT credentials to view the project list.
    // We return the list of all projects assigned to every vendor
    @Override
    public List<Project> getAllProjectList(
            String regionName,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.GET_PROJECT_ITEM.name())) {

            List<Project> projects;

            boolean hasRegion = regionName != null && !regionName.isEmpty();

            if (!hasRegion) {
                log.info("Fetching all the projects");
                scope.emit(MetricNames.GetProjectItem.GetAllProjects.name(), 1.0);
                projects = projectService.getProjectList();

            } else {
                log.info("Fetching projects for region {}", regionName);
                scope.withDimension("regionName", regionName);
                scope.emit(MetricNames.GetProjectItem.GetAllProjects.name(), 1.0);
                projects = projectService.getProjectListByRegion(regionName);
            }
            scope.recordSuccess();
            return projects;
        }
    }
}

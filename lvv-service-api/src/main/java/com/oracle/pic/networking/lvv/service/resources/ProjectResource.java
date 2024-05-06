package com.oracle.pic.networking.lvv.service.resources;

import static com.oracle.pic.networking.lvv.service.resources.ProjectResource.Metrics.CreateProject;
import static com.oracle.pic.networking.lvv.service.resources.ProjectResource.Metrics.CreateProjectFailure;
import static com.oracle.pic.networking.lvv.service.resources.ProjectResource.Metrics.GetProject;
import static com.oracle.pic.networking.lvv.service.resources.ProjectResource.Metrics.GetProjectFailure;
import static com.oracle.pic.networking.lvv.service.resources.ProjectResource.Metrics.GetProjectList;
import static com.oracle.pic.networking.lvv.service.resources.ProjectResource.Metrics.GetProjectListFailure;

import com.google.inject.Inject;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.commons.metrics.metrictypes.Timer;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractProjectsResource;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItem;
import com.oracle.pic.networking.lvv.service.model.Project;
import com.oracle.pic.networking.lvv.service.model.PutProjectRequest;
import com.oracle.pic.networking.lvv.service.service.ProjectService;
import com.oracle.pic.networking.lvv.service.utils.PaginationToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import lombok.AccessLevel;
import lombok.Getter;
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
public class ProjectResource extends AbstractProjectsResource {

    private static final String SORT_BY_ENUM_TIMECREATED = "timeCreated";
    private static final String SORT_BY_ENUM_DISPLAYNAME = "displayName";
    private static final String METRIC_SCOPE_NAME = "ServiceApi";
    private static final int DEFAULT_PAGE_SIZE = 100;

    private final ProjectService projectService;

    enum Metrics {
        CreateProject,
        CreateProjectFailure,
        GetProject,
        GetProjectFailure,
        GetProjectList,
        GetProjectListFailure
    }

    @Context
    @Getter(AccessLevel.PRIVATE)
    private HttpServletResponse httpServletResponse;

    @Inject
    protected ProjectResource(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public Project createProject(
            String projectId,
            PutProjectRequest value,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope = MetricsScope.create(METRIC_SCOPE_NAME)) {
            Timer timer = scope.timerStart("createProjectStartMillis");
            try {
                ProjectItem projectItem =
                        projectService.createUpdateProject(
                                projectId,
                                value.getProject().getVendorName(),
                                value.getProject().getBuilding(),
                                value.getProject().getBlock(),
                                value.getProject().getType());
                Project project =
                        Project.builder()
                                .projectId(projectItem.getProjectId())
                                .type(projectItem.getType())
                                .vendorName(projectItem.getVendorName())
                                .building(projectItem.getBuilding())
                                .block(projectItem.getBlock())
                                .build();
                scope.emit(CreateProject, 1);
                scope.recordSuccess();
                return project;
            } catch (Exception ex) {
                scope.emit(CreateProjectFailure, 1);
                throw ex;
            } finally {
                scope.timerStop(timer);
            }
        }
    }

    @Override
    public void deleteProject(
            String projectId,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {}

    @Override
    public Project getProject(
            String projectId,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope = MetricsScope.create(METRIC_SCOPE_NAME)) {
            Timer timer = scope.timerStart("getProjectStartMillis");
            try {
                ProjectItem projectItem = projectService.getProject(projectId);
                Project project =
                        Project.builder()
                                .projectId(projectItem.getProjectId())
                                .type(projectItem.getType())
                                .vendorName(projectItem.getVendorName())
                                .building(projectItem.getBuilding())
                                .block(projectItem.getBlock())
                                .build();
                scope.emit(GetProject, 1);
                scope.recordSuccess();
                return project;
            } catch (Exception ex) {
                scope.emit(GetProjectFailure, 1);
                throw ex;
            } finally {
                scope.timerStop(timer);
            }
        }
    }

    @Override
    public List<Project> getProjectList(
            String vendorName,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {

        try (MetricsScope scope = MetricsScope.create(METRIC_SCOPE_NAME)) {
            Timer timer = scope.timerStart("getProjectListStartMillis");
            try {
                String page = null;
                PaginationToken paginationToken = new PaginationToken();
                paginationToken.setToken(Optional.ofNullable(page));
                List<ProjectItem> projectItems =
                        projectService.getProjectListByVendor(paginationToken, vendorName);

                List<Project> result = new ArrayList<>();

                projectItems.forEach(
                        (item) -> {
                            Project project =
                                    Project.builder()
                                            .projectId(item.getProjectId())
                                            .block(item.getBlock())
                                            .building(item.getBuilding())
                                            .type(item.getType())
                                            .vendorName(item.getType())
                                            .build();
                            result.add(project);
                        });
                scope.emit(GetProjectList, result.size());
                scope.recordSuccess();
                return result;
            } catch (Exception ex) {
                scope.emit(GetProjectListFailure, 1);
                throw ex;
            } finally {
                scope.timerStop(timer);
            }
        }
    }
}

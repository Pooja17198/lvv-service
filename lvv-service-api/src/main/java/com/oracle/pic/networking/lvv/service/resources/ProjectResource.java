package com.oracle.pic.networking.lvv.service.resources;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.google.inject.Inject;
import com.oracle.pic.commons.service.metrics.context.ServiceName;
import com.oracle.pic.commons.service.model.PaginatedCollectionResponse;
import com.oracle.pic.commons.service.model.TaggedResponse;
import com.oracle.pic.networking.lvv.service.api.AbstractProjectResource;
import com.oracle.pic.networking.lvv.service.auth.AuthHelper;
import com.oracle.pic.networking.lvv.service.auth.AuthVerbs;
import com.oracle.pic.networking.lvv.service.etag.EtagMismatchException;
import com.oracle.pic.networking.lvv.service.model.CreateProjectDetails;
import com.oracle.pic.networking.lvv.service.model.Project;
import com.oracle.pic.networking.lvv.service.model.ProjectCollection;
import com.oracle.pic.networking.lvv.service.model.ProjectSummary;
import com.oracle.pic.networking.lvv.service.model.LifecycleState;
import com.oracle.pic.networking.lvv.service.model.SortOrders;
import com.oracle.pic.networking.lvv.service.model.UpdateProjectDetails;
import com.oracle.pic.networking.lvv.service.service.ProjectService;
import com.oracle.pic.networking.lvv.service.utils.RenderableExceptionsGenerator;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.permissions.annotations.AuthorizationPermission;
import com.oracle.pic.identity.authorization.permissions.annotations.VariableOperationName;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.identity.authorization.sdk.context.AuthorizationRequestContext;
import com.oracle.pic.identity.authorization.sdk.context.PrincipalContext;
import com.oracle.pic.sfw.dal.PaginatedResultSet;
import java.security.InvalidParameterException;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
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
@Path("/20180828")
@Produces({"application/json"})
@ServiceName("lvv-service")
public class ProjectResource extends AbstractProjectResource {

    private static final String SORT_BY_ENUM_TIMECREATED = "timeCreated";
    private static final String SORT_BY_ENUM_DISPLAYNAME = "displayName";
    private static final int DEFAULT_PAGE_SIZE = 100;

    private final AuthHelper authorizationHelper;
    private final ProjectService projectService;

    @Context
    @Getter(AccessLevel.PRIVATE)
    private HttpServletResponse httpServletResponse;

    @Inject
    protected ProjectResource(
            AuthHelper authorizationHelper, ProjectService projectService) {
        this.authorizationHelper = authorizationHelper;
        this.projectService = projectService;
    }

    @Override
    @POST
    @Path("/projects")
    @Produces({"application/json"})
    @AuthorizationPermission(AuthVerbs.LVV_SERVICE_CREATE)
    @VariableOperationName("createProject")
    public TaggedResponse<Project> createProject(
            CreateProjectDetails createProjectDetails,
            @HeaderParam("opc-retry-token") String opcRetryToken,
            @HeaderParam("opc-request-id") String opcRequestId,
            @PrincipalContext Principal principal,
            @AuthorizationRequestContext AuthorizationRequest authorizationRequest) {

        log.info(
                "Attempting to create resource: displayName {}, freeformTags {}, definedTags {}",
                createProjectDetails.getDisplayName(),
                createProjectDetails.getFreeformTags(),
                createProjectDetails.getDefinedTags());

        // Validate Inputs
        // TODO: Consider integrating parameter validation into the Bean validation step.
        ResourceUtils.validateRequiredParameter(
                "Compartment Id", createProjectDetails.getCompartmentId());
        ResourceUtils.validateRequiredParameter(
                "Display Name", createProjectDetails.getDisplayName());
        ResourceUtils.validateOptionalParameter("opcRetryToken", opcRetryToken);
        ResourceUtils.validateOptionalParameter("opcRequestId", opcRequestId);

        // Authorize
        authorizationHelper.authorize(
                authorizationRequest, createProjectDetails.getCompartmentId());

        final Project project = projectService.createProject(createProjectDetails);

        try {
            // TODO: When updating operations that modify the preexisting resource at the
            //  service level, select a different etag value based on the fields that might change.
            //  projectId will not change on update/modify operations and as such is not an
            //  appropriate value for an etag
            return new TaggedResponse<>(project, project.getId());
        } catch (InvalidParameterException ex) {
            throw RenderableExceptionsGenerator.generateInternalServerErrorException();
        }
    }

    @Override
    @GET
    @Path("/projects")
    @Produces({"application/json"})
    @AuthorizationPermission(AuthVerbs.LVV_SERVICE_INSPECT)
    @VariableOperationName("listProject")
    public PaginatedCollectionResponse<ProjectCollection> listProjects(
            @QueryParam("compartmentId") String compartmentId,
            @QueryParam("displayName") String displayName,
            @QueryParam("limit") Integer limit,
            @QueryParam("page") String page,
            @QueryParam("lifecycleState") LifecycleState lifecycleState,
            @QueryParam("sortOrder") SortOrders sortOrder,
            @QueryParam("sortBy") String sortBy,
            @HeaderParam("opc-request-id") String opcRequestId,
            @PrincipalContext Principal principal,
            @AuthorizationRequestContext AuthorizationRequest authorizationRequest) {
        // Log the call.
        log.info(
                "Attempting to list resources, compartmentId {}, limit {}, displayName {}, sortOrder {}, sortBy {}",
                compartmentId,
                limit,
                displayName,
                sortOrder,
                sortBy);

        // Validate Compartment Id, sortOrder, sortBy etc.
        ResourceUtils.validateRequiredParameter("compartmentId", compartmentId);
        ResourceUtils.validateOptionalParameter("displayName", displayName);
        ResourceUtils.validateOptionalParameter("opcRequestId", opcRequestId);
        ResourceUtils.validateOptionalParameter("page", page);
        ResourceUtils.validateOptionalParameter("sortBy", sortBy);
        ResourceUtils.validateOptionalPositiveIntegerParameter("limit", limit);

        // for valid authz you need to pass the correct compartment id
        authorizationHelper.authorize(authorizationRequest, compartmentId);

        sortBy = getSortBy(sortBy);

        // Query Service for result
        final PaginatedResultSet<ProjectSummary> projectQueryResults =
                projectService.queryProjects(
                        compartmentId,
                        displayName,
                        lifecycleState == null ? null : lifecycleState.getValue(),
                        limit != null ? limit : DEFAULT_PAGE_SIZE,
                        page,
                        getInternalSortOrder(sortOrder, sortBy),
                        sortBy);

        final ProjectCollection projectCollection =
                ProjectCollection.builder().items(projectQueryResults.getResults()).build();
        return projectQueryResults.hasNext()
                ? new PaginatedCollectionResponse<>(
                        projectCollection,
                        projectQueryResults.getNextPageToken().getSerializedToken())
                : new PaginatedCollectionResponse<>(projectCollection);
    }

    @Override
    @DELETE
    @Path("/projects/{projectId}")
    @Produces({"application/json"})
    @AuthorizationPermission(AuthVerbs.LVV_SERVICE_DELETE)
    @VariableOperationName("deleteProject")
    public void deleteProject(
            @PathParam("projectId") String projectId,
            @HeaderParam("if-match") String ifMatch,
            @HeaderParam("opc-request-id") String opcRequestId,
            @PrincipalContext Principal principal,
            @AuthorizationRequestContext AuthorizationRequest authorizationRequest) {
        log.info("Attempting to delete resource: resourceId {}", projectId);

        // Validate Inputs
        ResourceUtils.validateRequiredParameter("projectId", projectId);
        ResourceUtils.validateOptionalParameter("opcRequestId", opcRequestId);

        final Project project = projectService.getProject(projectId);
        ResourceUtils.validateRequiredParameter("compartmentId", project.getCompartmentId());

        // TODO: For valid AuthZ you need to pass the correct compartment id. So please implement
        // your getProject first.
        authorizationHelper.authorize(authorizationRequest, project.getCompartmentId());
        try {
            projectService.deleteProject(projectId, ifMatch);
        } catch (EtagMismatchException ex) {
            throw RenderableExceptionsGenerator.generateEtagMismatchException(
                    ex.getEtag(), projectId);
        }

        log.info("Successfully deleted resource: resourceId {}", projectId);
    }

    @Override
    @GET
    @Path("/projects/{projectId}")
    @Produces({"application/json"})
    @AuthorizationPermission(AuthVerbs.LVV_SERVICE_READ)
    @VariableOperationName("getProject")
    public TaggedResponse<Project> getProject(
            @PathParam("projectId") String projectId,
            @HeaderParam("opc-request-id") String opcRequestId,
            @PrincipalContext Principal principal,
            @AuthorizationRequestContext AuthorizationRequest authorizationRequest) {
        log.info("GET resource resourceId:{}", projectId);

        // Validate Inputs
        ResourceUtils.validateRequiredParameter("ProjectId", projectId);
        ResourceUtils.validateOptionalParameter("opcRequestId", opcRequestId);

        final Project project = projectService.getProject(projectId);
        ResourceUtils.validateRequiredParameter("compartmentId", project.getCompartmentId());

        // TODO: For valid AuthZ you need to pass the correct compartment id. So please implement
        // your getProject first.
        authorizationHelper.authorize(authorizationRequest, project.getCompartmentId());

        try {
            // TODO: When updating operations that modify the preexisting resource at the
            //  service level, select a different etag value based on the fields that might change.
            //  projectId will not change on update/modify operations and as such is not an
            //  appropriate value for an etag
            return new TaggedResponse<>(project, projectId);
        } catch (InvalidParameterException ex) {
            throw RenderableExceptionsGenerator.generateInternalServerErrorException();
        }
    }

    @Override
    @PUT
    @Path("/projects/{projectId}")
    @Produces({"application/json"})
    @AuthorizationPermission(AuthVerbs.LVV_SERVICE_UPDATE)
    @VariableOperationName("updateProject")
    public TaggedResponse<Project> updateProject(
            @PathParam("projectId") String projectId,
            UpdateProjectDetails updateProjectDetails,
            @HeaderParam("if-match") String ifMatch,
            @HeaderParam("opc-request-id") String opcRequestId,
            @PrincipalContext Principal principal,
            @AuthorizationRequestContext AuthorizationRequest authorizationRequest) {

        log.info(
                "Attempting to update resource: resourceId {}, displayName {}, freeformTags {}, definedTags {}",
                projectId,
                updateProjectDetails.getDisplayName(),
                updateProjectDetails.getFreeformTags(),
                updateProjectDetails.getDefinedTags());

        // Validate Inputs
        ResourceUtils.validateRequiredParameter("ProjectId", projectId);
        ResourceUtils.validateRequiredParameter(
                "Display Name", updateProjectDetails.getDisplayName());
        ResourceUtils.validateOptionalParameter("opcRequestId", opcRequestId);

        final Project project = projectService.getProject(projectId);
        ResourceUtils.validateRequiredParameter("compartmentId", project.getCompartmentId());

        // TODO: For valid AuthZ you need to pass the correct compartment id. So please implement
        // your getProject first.
        authorizationHelper.authorize(authorizationRequest, project.getCompartmentId());

        try {
            final Project updatedProject =
                    projectService.updateProject(
                            project.getCompartmentId(),
                            projectId,
                            updateProjectDetails,
                            ifMatch);
            // TODO: When updating operations that modify the preexisting resource at the
            //  service level, select a different etag value based on the fields that might change.
            //  projectId will not change on update/modify operations and as such is not an
            //  appropriate value for an etag
            return new TaggedResponse<>(updatedProject, projectId);
        } catch (EtagMismatchException ex) {
            throw RenderableExceptionsGenerator.generateEtagMismatchException(
                    ex.getEtag(), projectId);
        } catch (InvalidParameterException ex) {
            throw RenderableExceptionsGenerator.generateInternalServerErrorException();
        }
    }

    private String getSortBy(String sortBy) {
        if (isBlank(sortBy) || sortBy.toLowerCase().contains("time")) {
            return SORT_BY_ENUM_TIMECREATED;
        } else if (sortBy.toLowerCase().contains("display")) {
            return SORT_BY_ENUM_DISPLAYNAME;
        }

        throw RenderableExceptionsGenerator.generateInvalidParameterException("SortBy");
    }

    private SortOrders getInternalSortOrder(SortOrders sortOrder, String sortBy) {
        if (sortOrder == null) {
            return sortBy.equals(SORT_BY_ENUM_DISPLAYNAME) ? SortOrders.Asc : SortOrders.Desc;
        }

        if (sortOrder == SortOrders.Asc) {
            return SortOrders.Asc;
        } else if (sortOrder == SortOrders.Desc) {
            return SortOrders.Desc;
        }

        throw RenderableExceptionsGenerator.generateInvalidParameterException("SortOrder");
    }
}

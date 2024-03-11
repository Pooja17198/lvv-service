package com.oracle.pic.networking.lvv.service.resources;

import com.google.inject.Inject;
import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.api.AbstractProjectsResource;
import com.oracle.pic.networking.lvv.service.auth.AuthHelper;
import com.oracle.pic.networking.lvv.service.model.Project;
import com.oracle.pic.networking.lvv.service.model.PutProjectRequest;
import com.oracle.pic.networking.lvv.service.service.ProjectService;
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
    private static final int DEFAULT_PAGE_SIZE = 100;

    private final AuthHelper authorizationHelper;
    private final ProjectService projectService;

    @Context
    @Getter(AccessLevel.PRIVATE)
    private HttpServletResponse httpServletResponse;

    @Inject
    protected ProjectResource(AuthHelper authorizationHelper, ProjectService projectService) {
        this.authorizationHelper = authorizationHelper;
        this.projectService = projectService;
    }

    @Override
    public Project createProject(
            String projectId,
            PutProjectRequest value,
            String opcRequestId,
            Principal principal,
            AuthorizationRequest authorizationRequest) {
        return null;
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
        return null;
    }
}

package com.oracle.pic.networking.lvv.service.resources;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

import com.oracle.pic.identity.authentication.Principal;
import com.oracle.pic.identity.authorization.sdk.AuthorizationRequest;
import com.oracle.pic.networking.lvv.service.auth.AuthHelper;
import com.oracle.pic.networking.lvv.service.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ProjectResourceTest {
    private final String compartmentId =
            "ocid1.compartment.oc1..aaaaaaaa26mceal7cypzsefhbm2l73xtb3yreplacemereplacemereplaceme";
    private final String displayName = "projectTest";
    private final String projectId = "projectId";
    private final String ifMatch = "projectId";
    private ProjectResource resource;

    @Mock private AuthHelper mockAuthorizationHelper;

    @Mock private Principal mockPrincipal;

    @Mock private AuthorizationRequest mockAuthorizationRequest;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        resource = new ProjectResource(mockAuthorizationHelper, new ProjectService());
        doNothing()
                .when(mockAuthorizationHelper)
                .authorize(any(AuthorizationRequest.class), anyString());
    }

    @Test
    public void createProjectTest() {}

    @Test
    public void listProjectTest() {}

    @Test
    public void getProject() {}
}

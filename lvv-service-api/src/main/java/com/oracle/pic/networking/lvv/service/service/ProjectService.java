package com.oracle.pic.networking.lvv.service.service;

import com.google.inject.Inject;
import com.oracle.pic.commons.service.tagging.EtagUtils;
import com.oracle.pic.networking.lvv.service.etag.EtagMismatchException;
import com.oracle.pic.networking.lvv.service.model.CreateProjectDetails;
import com.oracle.pic.networking.lvv.service.model.Project;
import com.oracle.pic.networking.lvv.service.model.ProjectSummary;
import com.oracle.pic.networking.lvv.service.model.SortOrders;
import com.oracle.pic.networking.lvv.service.model.UpdateProjectDetails;
import com.oracle.pic.sfw.dal.PageableResultSet;
import com.oracle.pic.sfw.dal.PaginatedResultSet;
import java.util.Collections;
import java.util.Date;
import java.util.Random;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/** A dummy example class for illustration purposes. */
@Slf4j
public class ProjectService {
    private final Random rand = new Random();

    @Inject
    public ProjectService() {}

    public PaginatedResultSet<ProjectSummary> queryProjects(
            String compartmentId,
            String displayName,
            String lifecycleState,
            int limit,
            String page,
            SortOrders sortOrder,
            String sortBy) {
        // todo: implement project query logic here.
        return new PageableResultSet<>(
                Collections.singletonList(
                        getTestProjectSummaryObject(compartmentId, displayName)),
                null,
                null,
                rand.nextInt());
    }

    public Project createProject(CreateProjectDetails createProjectDetails) {
        // todo: implement project creation logic here.
        String projectId = UUID.randomUUID().toString();
        return getTestProjectObject(
                createProjectDetails.getCompartmentId(),
                createProjectDetails.getDisplayName(),
                projectId);
    }

    public Project getProject(String projectId) {
        // todo: implement service logic to retrieve project here.
        return getTestProjectObject(null, null, projectId);
    }

    public Project updateProject(
            String compartmentId,
            String projectId,
            UpdateProjectDetails updateProjectDetails,
            String ifMatch)
            throws EtagMismatchException {
        // todo: implement update functionality here
        if (!EtagUtils.etagMatches(projectId, ifMatch)) {
            throw new EtagMismatchException(ifMatch);
        }

        return getTestProjectObject(
                compartmentId, updateProjectDetails.getDisplayName(), projectId);
    }

    public void deleteProject(String projectId, String ifMatch) throws EtagMismatchException {
        if (!EtagUtils.etagMatches(projectId, ifMatch)) {
            throw new EtagMismatchException(ifMatch);
        }
        // todo: implement project deletion logic here.
    }

    // Test method to return a test Project object
    private ProjectSummary getTestProjectSummaryObject(
            String compartmentId, String displayName) {
        return ProjectSummary.builder()
                .compartmentId(compartmentId)
                .timeCreated(new Date())
                .displayName(displayName)
                .build();
    }

    // Test method to return a test ProjectSummary object
    private Project getTestProjectObject(
            String compartmentId, String displayName, String id) {
        // TODO: This is a dummy test compartmentId, pls replace with your implementation.
        if (compartmentId == null) {
            compartmentId =
                    "ocid1.compartment.oc1..aaaaaaaa26mceal7cypzsefhbm2l73xtb3yreplacemereplacemereplaceme";
        }
        return Project.builder()
                .compartmentId(compartmentId)
                .displayName(displayName)
                .id(id)
                .timeCreated(new Date())
                .build();
    }
}

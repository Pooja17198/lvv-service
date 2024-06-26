package com.oracle.pic.networking.lvv.service.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.networking.lvv.service.kiev.KievManager;
import com.oracle.pic.networking.lvv.service.kiev.ProjectItem;
import com.oracle.pic.networking.lvv.service.utils.PaginationToken;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Slf4j
@Singleton
public class ProjectService {
    private KievManager kievManager;

    @Inject
    public ProjectService(KievManager kievManager) {
        this.kievManager = kievManager;
    }

    public ProjectItem createUpdateProject(
            String projectId, String vendorName, String building, String block, String type) {
        ProjectItem item =
                ProjectItem.builder()
                        .projectId(projectId)
                        .building(building)
                        .block(block)
                        .type(type)
                        .vendorName(vendorName)
                        .build();
        try {
            kievManager.addProjectItem(item);
            return item;
        } catch (Exception exception) {
            log.error("Error occurred. Message is {}", exception.getMessage());
            log.error("Stack Trace: {}", ExceptionUtils.getStackTrace(exception));
            throw new RenderableException(
                    ErrorCode.InternalError, "Failed to Create Project Item " + item);
        }
    }

    public ProjectItem getProject(String projectId) {
        try {
            return kievManager.getProjectItem(projectId);
        } catch (Exception exception) {
            log.error("Error occurred. Message is {}", exception.getMessage());
            log.error("Stack Trace: {}", ExceptionUtils.getStackTrace(exception));
            throw new RenderableException(
                    ErrorCode.NotAuthorizedOrNotFound, "Failed to retrieve ProjectId " + projectId);
        }
    }

    public void deleteProject(String projectId) {
        try {
            kievManager.deleteProjectItem(projectId);
        } catch (Exception exception) {
            log.error("Error occurred. Message is {}", exception.getMessage());
            log.error("Stack Trace: {}", ExceptionUtils.getStackTrace(exception));
            throw new RenderableException(
                    ErrorCode.NotAuthorizedOrNotFound, "Failed to delete ProjectId " + projectId);
        }
    }

    public List<ProjectItem> getProjectListByVendor(PaginationToken page, String vendorName) {
        try {
            return kievManager.getAllProjectItem(page, vendorName);
        } catch (Exception exception) {
            log.error("Error occurred. Message is {}", exception.getMessage());
            log.error("Stack Trace: {}", ExceptionUtils.getStackTrace(exception));
            throw new RenderableException(
                    ErrorCode.InternalError, "Failed to retrieve projectList bucket");
        }
    }
}

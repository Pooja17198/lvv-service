package com.oracle.pic.networking.lvv.service.service;

import com.google.inject.Inject;
import com.oracle.pic.commons.service.tagging.EtagUtils;
import com.oracle.pic.networking.lvv.service.etag.EtagMismatchException;
import com.oracle.pic.networking.lvv.service.model.Project;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProjectService {
    private final Random rand = new Random();

    @Inject
    public ProjectService() {}

    public Project getProject(String projectId) {

        return null;
    }

    public void deleteProject(String projectId, String ifMatch) throws EtagMismatchException {
        if (!EtagUtils.etagMatches(projectId, ifMatch)) {
            throw new EtagMismatchException(ifMatch);
        }
        // todo: implement project deletion logic here.
    }
}

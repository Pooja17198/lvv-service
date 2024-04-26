package com.oracle.pic.networking.lvv.service.dependencies.ncp;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oracle.pic.networking.ncp.JobProgressClient;
import com.oracle.pic.networking.ncp.JobsClient;
import com.oracle.pic.networking.ncp.model.Job;
import com.oracle.pic.networking.ncp.model.JobRequest;
import com.oracle.pic.networking.ncp.model.UnitProgressStatus;
import com.oracle.pic.networking.ncp.requests.CreateJobRequest;
import com.oracle.pic.networking.ncp.requests.GetJobRequest;
import com.oracle.pic.networking.ncp.requests.ListLastJobUnitUpdateRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class MockNcpClients {

    private static Map<String, Job> jobMap = new HashMap<>();

    public static JobsClient getMockJobsClient() {
        JobsClient mockJobsClient = mock(JobsClient.class);
        when(mockJobsClient.getJob(GetJobRequest.builder().build())).thenAnswer(getJobAnswer());
        when(mockJobsClient.createJob(CreateJobRequest.builder().build()))
                .thenAnswer(createJobAnswer());
        return mockJobsClient;
    }

    public static JobProgressClient getMockJobProgressClient() {
        JobProgressClient mockJobProgressClient = mock(JobProgressClient.class);
        when(mockJobProgressClient.listLastJobUnitUpdate(
                        ListLastJobUnitUpdateRequest.builder().build()))
                .thenAnswer(listLastJobUnitUpdateAnswer());
        return mockJobProgressClient;
    }

    private static Answer<Job> getJobAnswer() {
        return new Answer<Job>() {
            @Override
            public Job answer(InvocationOnMock invocation) {
                String jobId = invocation.getArgument(0);

                if (jobMap.containsKey(jobId)) {
                    if (!jobMap.get(jobId).getState().equals(Job.State.Succeeded)) {
                        jobMap.put(
                                jobId,
                                Job.builder()
                                        .copy(jobMap.get(jobId))
                                        .endDate(new Date())
                                        .state(Job.State.Succeeded)
                                        .build());
                    }
                    return jobMap.get(jobId);
                }

                Job mockJob =
                        Job.builder()
                                .id(jobId)
                                .request(JobRequest.builder().build())
                                .startDate(new Date())
                                .endDate(new Date())
                                .response("Success")
                                .resultPayload("fake payload")
                                .request(JobRequest.builder().build())
                                .state(Job.State.Succeeded)
                                .build();

                jobMap.put(jobId, mockJob);

                return mockJob;
            }
        };
    }

    private static Answer<Job> createJobAnswer() {
        return new Answer<Job>() {
            @Override
            public Job answer(InvocationOnMock invocation) {
                String idempotencyToken = invocation.getArgument(0);
                JobRequest jobRequest = invocation.getArgument(1);

                Job mockJob =
                        Job.builder()
                                .id(UUID.randomUUID().toString())
                                .startDate(new Date())
                                .response(String.format("idempotencyToken=%s", idempotencyToken))
                                .resultPayload(jobRequest.getPayload())
                                .request(jobRequest)
                                .state(Job.State.Pending)
                                .build();

                jobMap.put(mockJob.getId(), mockJob);

                return mockJob;
            }
        };
    }

    private static Answer<List<UnitProgressStatus>> listLastJobUnitUpdateAnswer() {
        return new Answer<List<UnitProgressStatus>>() {
            @Override
            public List<UnitProgressStatus> answer(InvocationOnMock invocation) {
                return new ArrayList<>(Collections.singleton(UnitProgressStatus.builder().build()));
            }
        };
    }
}

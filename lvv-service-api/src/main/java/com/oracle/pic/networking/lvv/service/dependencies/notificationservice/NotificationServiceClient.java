package com.oracle.pic.networking.lvv.service.dependencies.notificationservice;

import com.google.inject.Inject;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.pic.ons.gateway.NotificationControlPlaneClient;
import com.oracle.pic.ons.gateway.NotificationDataPlaneClient;
import com.oracle.pic.ons.gateway.requests.GetTopicRequest;
import com.oracle.pic.ons.gateway.responses.GetTopicResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NotificationServiceClient {
    private final BasicAuthenticationDetailsProvider authProvider;
    private final String region; // Public region name Eg: us-phoenix-1
    @Getter private final String compartmentOcid;
    @Getter private final boolean enableNetworkMonitoringAndAlerting;

    @Inject
    public NotificationServiceClient(
            BasicAuthenticationDetailsProvider authProvider,
            String region,
            String compartmentOcid,
            boolean enableNetworkMonitoringAndAlerting) {
        this.authProvider = authProvider;
        this.region = region;
        this.compartmentOcid = compartmentOcid;
        this.enableNetworkMonitoringAndAlerting = enableNetworkMonitoringAndAlerting;
    }

    public NotificationControlPlaneClient getControlPlaneClient() {
        String cpEndpoint = String.format("https://notification.%s.oraclecloud.com", region);
        return NotificationControlPlaneClient.builder()
                .endpoint(cpEndpoint)
                .build(this.authProvider);
    }

    public String getApiEndPoint(
            NotificationControlPlaneClient controlPlaneClient, String topicOcid) {
        GetTopicResponse getTopic =
                controlPlaneClient.getTopic(GetTopicRequest.builder().topicId(topicOcid).build());
        return getTopic.getNotificationTopic().getApiEndpoint();
    }

    public NotificationDataPlaneClient getDataPlaneClient(
            NotificationControlPlaneClient controlPlaneClient, String topicOcid) {
        try {
            String apiEndPoint = getApiEndPoint(controlPlaneClient, topicOcid);
            return NotificationDataPlaneClient.builder()
                    .endpoint(apiEndPoint)
                    .build(this.authProvider);
        } catch (Exception e) {
            log.error("Failed to create NotificationDataPlaneClient for topic {}", topicOcid, e);
            return null;
        }
    }
}

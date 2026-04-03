package com.oracle.pic.networking.lvv.service.dependencies.notificationservice;

import com.google.inject.Inject;
import com.oracle.bmc.model.BmcException;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import com.oracle.pic.ons.gateway.NotificationControlPlaneClient;
import com.oracle.pic.ons.gateway.NotificationDataPlaneClient;
import com.oracle.pic.ons.gateway.model.CreateSubscriptionDetails;
import com.oracle.pic.ons.gateway.model.CreateTopicDetails;
import com.oracle.pic.ons.gateway.model.MessageDetails;
import com.oracle.pic.ons.gateway.requests.CreateSubscriptionRequest;
import com.oracle.pic.ons.gateway.requests.CreateTopicRequest;
import com.oracle.pic.ons.gateway.requests.PublishMessageRequest;
import com.oracle.pic.ons.gateway.responses.CreateSubscriptionResponse;
import com.oracle.pic.ons.gateway.responses.CreateTopicResponse;
import com.oracle.pic.ons.gateway.responses.PublishMessageResponse;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper for Oracle Notification Service (ONS) data/control plane operations with verbose logging.
 * - Resolves and uses the topic's cell apiEndpoint for data-plane operations. - Can create topics
 * (control plane), create EMAIL subscriptions (data plane), and publish messages.
 *
 * <p>Notes: - Creating topics requires IAM: manage ons-topics in the topic compartment. - Creating
 * subscriptions/publishing requires IAM: use ons-topics in the topic compartment. - Email
 * subscriptions require confirmation by the recipient before delivery begins.
 */
@Slf4j
public class NotificationServiceHelper {

    private final NotificationServiceClient notificationServiceClient;
    private final NotificationControlPlaneClient notificationControlPlaneClient;

    @Inject
    public NotificationServiceHelper(NotificationServiceClient notificationServiceClient) {
        this.notificationServiceClient =
                Objects.requireNonNull(
                        notificationServiceClient, "notificationServiceClient must not be null");

        log.info("Getting ControlPlane client");
        this.notificationControlPlaneClient = notificationServiceClient.getControlPlaneClient();
        log.info("ControlPlane client acquired");
    }

    /**
     * Create a new ONS topic (Control Plane) and return its OCID. Logs each step. Requires IAM:
     * manage ons-topics in the configured compartment.
     *
     * @param topicName unique name for the topic in the compartment
     * @param description optional description (may be null)
     * @return created topic OCID
     */
    public String createTopic(String topicName, String description) {
        if (!notificationServiceClient.isEnableNetworkMonitoringAndAlerting()) {
            log.info(
                    "Notifications disabled by config; skipping createTopic for name={}",
                    topicName);
            return null;
        }
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.NOTIFICATIONS.name())) {

            Objects.requireNonNull(topicName, "topicName must not be null");
            String compartmentOcid =
                    Objects.requireNonNull(
                            notificationServiceClient.getCompartmentOcid(),
                            "compartmentOcid must not be null");

            log.info("Creating Topic with name={}", topicName);
            CreateTopicDetails details =
                    CreateTopicDetails.builder()
                            .name(topicName)
                            .compartmentId(compartmentOcid)
                            .description(description)
                            .build();

            CreateTopicRequest request =
                    CreateTopicRequest.builder().createTopicDetails(details).build();

            try {
                log.info("Invoking ControlPlane createTopic call");
                CreateTopicResponse resp = notificationControlPlaneClient.createTopic(request);
                log.info("Received response from createTopic call");

                String topicOcid = resp.getNotificationTopic().getTopicId();
                String apiEndpoint = resp.getNotificationTopic().getApiEndpoint();
                log.info("Created topic: topicOcid={}, apiEndpoint={}", topicOcid, apiEndpoint);

                scope.emit(MetricNames.Notifications.CreateTopicSuccess.name(), 1.0);
                scope.recordSuccess();

                return topicOcid;
            } catch (RuntimeException ex) {
                scope.emit(MetricNames.Notifications.CreateTopicFailure.name(), 1.0);
                scope.recordSuccess();

                log.error("Failed to create topic (name={})", topicName, ex);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "Failed to create notification topic");
            }
        }
    }

    /**
     * Try-create EMAIL subscription (Data Plane) without listing. - If created: logs success and
     * returns subscription OCID. - If already exists: logs "subscription already exists" and
     * returns null. Requires IAM: use ons-topics in the topic compartment.
     *
     * @param emailAddress target email address to subscribe
     */
    public void ensureEmailSubscription(String topicOcid, String emailAddress) {
        if (!notificationServiceClient.isEnableNetworkMonitoringAndAlerting()) {
            log.info(
                    "Notifications disabled by config; skipping ensureEmailSubscription for topic={}, email={}",
                    topicOcid,
                    emailAddress);
            return;
        }
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.NOTIFICATIONS.name())) {

            Objects.requireNonNull(emailAddress, "emailAddress must not be null");
            Objects.requireNonNull(topicOcid, "topicOcid must not be null");
            String compartmentOcid =
                    Objects.requireNonNull(
                            notificationServiceClient.getCompartmentOcid(),
                            "compartmentOcid must not be null");

            log.info(
                    "Creating Subscription (EMAIL): topic={}, emailAddress={}",
                    topicOcid,
                    emailAddress);

            CreateSubscriptionDetails details =
                    CreateSubscriptionDetails.builder()
                            .topicId(topicOcid)
                            .compartmentId(compartmentOcid)
                            .protocol("EMAIL")
                            .endpoint(emailAddress)
                            .build();

            CreateSubscriptionRequest request =
                    CreateSubscriptionRequest.builder().createSubscriptionDetails(details).build();

            NotificationDataPlaneClient notificationDataPlaneClient =
                    notificationServiceClient.getDataPlaneClient(
                            this.notificationControlPlaneClient, topicOcid);
            if (notificationDataPlaneClient == null) {
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "Failed to create dataplane client for creating email subscription");
            }

            try {
                log.info("Invoking DataPlane createSubscription call");
                CreateSubscriptionResponse resp =
                        notificationDataPlaneClient.createSubscription(request);
                log.info("Received response from createSubscription call");

                String subId = resp.getSubscription().getId();
                log.info(
                        "Created EMAIL subscription (pending confirmation) subscription id={}",
                        subId);

                scope.emit(MetricNames.Notifications.CreateSubscriptionSuccess.name(), 1.0);
            } catch (Exception ex) {
                if (alreadyExists(ex)) {
                    scope.emit(
                            MetricNames.Notifications.CreateSubscriptionAlreadyExists.name(), 1.0);
                    scope.recordSuccess();

                    log.info(
                            "Subscription already exists: topicId={}, email={}",
                            topicOcid,
                            emailAddress);
                    return;
                }

                scope.emit(MetricNames.Notifications.CreateSubscriptionFailure.name(), 1.0);
                scope.recordSuccess();
                log.error(
                        "Failed to create subscription: topicOcid={}, email={}",
                        topicOcid,
                        emailAddress,
                        ex);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "Failed to create email subscription");
            }
        }
    }

    /** Heuristic to detect "already exists" from OCI/BMC exceptions, so we can log idempotently. */
    private boolean alreadyExists(Throwable exception) {
        if (exception instanceof BmcException) {
            BmcException be = (BmcException) exception;
            int status = be.getStatusCode();

            log.info("BmcException: status={}", status);

            // Conflict -> duplicate subscription for (topic, endpoint)
            return status == 409;
        } else {
            log.info("Not a BmcException: class={}", exception.getClass().getName());
        }
        return false;
    }

    /**
     * Publish an email message (subject/body) to the configured topic (Data Plane). Body should be
     * <= 64 KB.
     */
    public void publish(String topicOcid, String subject, String body) {
        if (!notificationServiceClient.isEnableNetworkMonitoringAndAlerting()) {
            log.info(
                    "Notifications disabled by config; skipping publish for topic={}, subject={}",
                    topicOcid,
                    subject);
            return;
        }
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.NOTIFICATIONS.name())) {

            Objects.requireNonNull(subject, "Subject must not be null");
            Objects.requireNonNull(body, "Body must not be null");

            MessageDetails details = MessageDetails.builder().title(subject).body(body).build();

            String opcRequestId = UUID.randomUUID().toString();
            log.info(
                    "Building PublishMessageRequest: topic={}, opcRequestId={}",
                    topicOcid,
                    opcRequestId);
            PublishMessageRequest request =
                    PublishMessageRequest.builder()
                            .topicId(topicOcid)
                            .messageDetails(details)
                            .messageType(PublishMessageRequest.MessageType.RawText)
                            .opcRequestId(opcRequestId)
                            .build();

            NotificationDataPlaneClient notificationDataPlaneClient =
                    notificationServiceClient.getDataPlaneClient(
                            this.notificationControlPlaneClient, topicOcid);
            if (notificationDataPlaneClient == null) {
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "Failed to create dataplane client for publishing message");
            }

            try {
                log.info("Invoking DataPlane publishMessage call");
                PublishMessageResponse resp = notificationDataPlaneClient.publishMessage(request);
                log.info("Received response from publishMessage call");
                log.info("Publish result: {}", resp.getPublishResult());
                log.info(
                        "ONS publish invoked for topic {}, opcRequestId={}",
                        topicOcid,
                        resp.getOpcRequestId());
                scope.emit(MetricNames.Notifications.PublishMessageSuccess.name(), 1.0);
                scope.recordSuccess();
            } catch (Exception e) {
                scope.emit(MetricNames.Notifications.PublishMessageFailure.name(), 1.0);
                scope.recordSuccess();
                log.error("Exception during publishMessage", e);
                throw new RenderableException(
                        ErrorCode.ExternalServerInvalidResponse,
                        "Failed to publish message to notification topic");
            }
        }
    }
}

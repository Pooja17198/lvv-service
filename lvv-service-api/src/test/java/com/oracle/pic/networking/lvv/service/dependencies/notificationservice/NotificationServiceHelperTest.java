package com.oracle.pic.networking.lvv.service.dependencies.notificationservice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.oracle.pic.ons.gateway.NotificationControlPlaneClient;
import com.oracle.pic.ons.gateway.NotificationDataPlaneClient;
import com.oracle.pic.ons.gateway.model.CreateSubscriptionDetails;
import com.oracle.pic.ons.gateway.model.CreateTopicDetails;
import com.oracle.pic.ons.gateway.model.MessageDetails;
import com.oracle.pic.ons.gateway.model.NotificationTopic;
import com.oracle.pic.ons.gateway.model.Subscription;
import com.oracle.pic.ons.gateway.requests.CreateSubscriptionRequest;
import com.oracle.pic.ons.gateway.requests.CreateTopicRequest;
import com.oracle.pic.ons.gateway.requests.PublishMessageRequest;
import com.oracle.pic.ons.gateway.responses.CreateSubscriptionResponse;
import com.oracle.pic.ons.gateway.responses.CreateTopicResponse;
import com.oracle.pic.ons.gateway.responses.PublishMessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceHelperTest {

    @Mock NotificationServiceClient notificationServiceClient;
    @Mock NotificationControlPlaneClient controlPlaneClient;
    @Mock NotificationDataPlaneClient dataPlaneClient;

    NotificationServiceHelper helper;

    @BeforeEach
    void setUp() {
        lenient()
                .when(notificationServiceClient.getControlPlaneClient())
                .thenReturn(controlPlaneClient);
        lenient()
                .when(notificationServiceClient.getCompartmentOcid())
                .thenReturn("ocid1.compartment.oc1..aaaa");
        helper = new NotificationServiceHelper(notificationServiceClient);
    }

    @Test
    void createTopic_buildsRequestAndReturnsTopicId() {
        NotificationTopic topic = mock(NotificationTopic.class);
        when(topic.getTopicId()).thenReturn("ocid1.topic.created");
        CreateTopicResponse createResp = mock(CreateTopicResponse.class);
        when(createResp.getNotificationTopic()).thenReturn(topic);
        ArgumentCaptor<CreateTopicRequest> reqCap =
                ArgumentCaptor.forClass(CreateTopicRequest.class);
        when(controlPlaneClient.createTopic(reqCap.capture())).thenReturn(createResp);

        String id = helper.createTopic("my-topic", "desc");

        assertEquals("ocid1.topic.created", id);
        CreateTopicDetails details = reqCap.getValue().getCreateTopicDetails();
        assertNotNull(details);
        assertEquals("my-topic", details.getName());
        assertEquals("ocid1.compartment.oc1..aaaa", details.getCompartmentId());
        assertEquals("desc", details.getDescription());
    }

    @Test
    void ensureEmailSubscription_success_invokesCreateSubscription() throws Exception {
        when(notificationServiceClient.getDataPlaneClient(eq(controlPlaneClient), anyString()))
                .thenReturn(dataPlaneClient);
        Subscription sub = mock(Subscription.class);
        when(sub.getId()).thenReturn("ocid1.subscription.created");
        CreateSubscriptionResponse resp = mock(CreateSubscriptionResponse.class);
        when(resp.getSubscription()).thenReturn(sub);
        ArgumentCaptor<CreateSubscriptionRequest> reqCap =
                ArgumentCaptor.forClass(CreateSubscriptionRequest.class);
        when(dataPlaneClient.createSubscription(reqCap.capture())).thenReturn(resp);

        helper.ensureEmailSubscription("ocid1.topic", "user@example.com");

        CreateSubscriptionDetails details = reqCap.getValue().getCreateSubscriptionDetails();
        assertNotNull(details);
        assertEquals("EMAIL", details.getProtocol());
        assertEquals("user@example.com", details.getEndpoint());
        assertEquals("ocid1.topic", details.getTopicId());
        assertEquals("ocid1.compartment.oc1..aaaa", details.getCompartmentId());
    }

    @Test
    void ensureEmailSubscription_throws_whenDataPlaneClientIsNull() throws Exception {
        when(notificationServiceClient.getDataPlaneClient(eq(controlPlaneClient), anyString()))
                .thenReturn(null);

        assertThrows(
                com.oracle.pic.commons.exceptions.server.RenderableException.class,
                () -> helper.ensureEmailSubscription("ocid1.topic", "user@example.com"));

        verifyNoInteractions(dataPlaneClient);
    }

    @Test
    void publish_success_invokesDataPlaneWithRawText() throws Exception {
        when(notificationServiceClient.getDataPlaneClient(eq(controlPlaneClient), anyString()))
                .thenReturn(dataPlaneClient);
        PublishMessageResponse resp = mock(PublishMessageResponse.class);
        ArgumentCaptor<PublishMessageRequest> reqCap =
                ArgumentCaptor.forClass(PublishMessageRequest.class);
        when(dataPlaneClient.publishMessage(reqCap.capture())).thenReturn(resp);

        helper.publish("ocid1.topic", "Subject", "Body text");

        PublishMessageRequest sent = reqCap.getValue();
        assertEquals("ocid1.topic", sent.getTopicId());
        MessageDetails md = sent.getMessageDetails();
        assertNotNull(md);
        assertEquals("Subject", md.getTitle());
        assertEquals("Body text", md.getBody());
        assertEquals(PublishMessageRequest.MessageType.RawText, sent.getMessageType());
    }

    @Test
    void publish_throws_whenDataPlaneClientIsNull() throws Exception {
        when(notificationServiceClient.getDataPlaneClient(eq(controlPlaneClient), anyString()))
                .thenReturn(null);

        assertThrows(
                com.oracle.pic.commons.exceptions.server.RenderableException.class,
                () -> helper.publish("ocid1.topic", "Subject", "Body text"));

        verifyNoInteractions(dataPlaneClient);
    }
}

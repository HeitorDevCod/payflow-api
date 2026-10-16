package com.payflow.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false, length = 80)
    private String requestId;

    @Column(nullable = false, length = 60)
    private String topic;

    @Column(name = "resource_id", nullable = false, length = 60)
    private String resourceId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected WebhookEvent() {
    }

    public WebhookEvent(String requestId, String topic, String resourceId) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.topic = topic;
        this.resourceId = resourceId;
        this.receivedAt = Instant.now();
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTopic() {
        return topic;
    }

    public String getResourceId() {
        return resourceId;
    }
}

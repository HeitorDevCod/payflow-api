package com.payflow.subscription;

import com.payflow.payment.gateway.GatewaySubscription;
import com.payflow.payment.gateway.GatewaySubscriptionRequest.FrequencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 120)
    private String reason;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private int frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_type", nullable = false, length = 10)
    private FrequencyType frequencyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GatewaySubscription.Status status;

    @Column(name = "provider_id", length = 60)
    private String providerId;

    @Column(name = "next_payment_date")
    private Instant nextPaymentDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Subscription() {
    }

    public Subscription(UUID customerId, String reason, BigDecimal amount, int frequency, FrequencyType frequencyType) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.reason = reason;
        this.amount = amount;
        this.frequency = frequency;
        this.frequencyType = frequencyType;
        this.status = GatewaySubscription.Status.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void sync(GatewaySubscription remote) {
        if (remote.providerId() != null) {
            this.providerId = remote.providerId();
        }
        if (remote.status() != null) {
            this.status = remote.status();
        }
        if (remote.nextPaymentDate() != null) {
            this.nextPaymentDate = remote.nextPaymentDate();
        }
        this.updatedAt = Instant.now();
    }

    public boolean belongsTo(UUID customerId) {
        return this.customerId.equals(customerId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getFrequency() {
        return frequency;
    }

    public FrequencyType getFrequencyType() {
        return frequencyType;
    }

    public GatewaySubscription.Status getStatus() {
        return status;
    }

    public String getProviderId() {
        return providerId;
    }

    public Instant getNextPaymentDate() {
        return nextPaymentDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

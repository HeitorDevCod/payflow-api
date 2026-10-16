package com.payflow.payment.gateway;

import java.math.BigDecimal;
import java.time.Instant;

public record GatewaySubscription(
        String providerId,
        Status status,
        String externalReference,
        BigDecimal amount,
        Instant nextPaymentDate
) {

    public enum Status {
        PENDING,
        AUTHORIZED,
        PAUSED,
        CANCELED
    }
}

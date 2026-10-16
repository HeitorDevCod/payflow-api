package com.payflow.payment.gateway;

import java.math.BigDecimal;

public record GatewaySubscriptionRequest(
        String externalReference,
        String reason,
        String payerEmail,
        String cardToken,
        BigDecimal amount,
        int frequency,
        FrequencyType frequencyType
) {

    public enum FrequencyType {
        DAYS,
        MONTHS
    }
}

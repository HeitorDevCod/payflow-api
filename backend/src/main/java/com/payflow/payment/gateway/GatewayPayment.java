package com.payflow.payment.gateway;

import com.payflow.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record GatewayPayment(
        String providerId,
        PaymentStatus status,
        String statusDetail,
        BigDecimal amount,
        String currency,
        String externalReference,
        Instant expiresAt,
        String pixQrCode,
        String pixQrCodeBase64,
        String ticketUrl,
        String barcode
) {
}

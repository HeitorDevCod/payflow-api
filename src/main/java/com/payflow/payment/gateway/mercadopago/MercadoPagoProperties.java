package com.payflow.payment.gateway.mercadopago;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "mercadopago")
public record MercadoPagoProperties(
        @NotBlank String baseUrl,
        String accessToken,
        String webhookSecret,
        String notificationUrl,
        @NotNull Duration webhookTolerance,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    public boolean hasAccessToken() {
        return accessToken != null && !accessToken.isBlank();
    }

    public boolean hasWebhookSecret() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }
}

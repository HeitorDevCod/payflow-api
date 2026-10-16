package com.payflow.payment.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "payments")
public record PaymentProperties(
        @NotNull Duration pixExpiration,
        @NotNull Duration boletoExpiration,
        @Min(1) @Max(24) int maxInstallments,
        @NotNull @DecimalMin("0.01") BigDecimal minInstallmentAmount,
        @Valid @NotNull Reconciliation reconciliation
) {

    public record Reconciliation(boolean enabled, @NotNull Duration interval, @NotNull Duration staleAfter) {
    }
}

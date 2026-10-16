package com.payflow.payment.api;

import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentMethod;
import com.payflow.payment.domain.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PixPaymentRequest(@NotNull UUID orderId) {
    }

    public record CardPaymentRequest(
            @NotNull UUID orderId,
            @NotBlank @Size(max = 64) String token,
            @NotBlank @Pattern(regexp = "[a-z_]{2,30}", message = "identificador da bandeira inválido") String paymentMethodId,
            Long issuerId,
            @Min(1) @Max(24) int installments
    ) {
    }

    public record BoletoPaymentRequest(
            @NotNull UUID orderId,
            @Valid @NotNull AddressRequest address
    ) {
    }

    public record AddressRequest(
            @NotBlank @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP inválido") String zipCode,
            @NotBlank @Size(max = 120) String street,
            @NotBlank @Size(max = 10) String number,
            @NotBlank @Size(max = 80) String neighborhood,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "UF inválida") String state
    ) {

        public String normalizedZipCode() {
            return zipCode.replace("-", "");
        }
    }

    public record PaymentResponse(
            UUID id,
            UUID orderId,
            PaymentMethod method,
            PaymentStatus status,
            String statusDetail,
            BigDecimal amount,
            int installments,
            String providerId,
            PixData pix,
            BoletoData boleto,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {

        public static PaymentResponse from(Payment payment) {
            PixData pix = payment.getMethod() == PaymentMethod.PIX && payment.getPixQrCode() != null
                    ? new PixData(payment.getPixQrCode(), payment.getPixQrCodeBase64(), payment.getTicketUrl())
                    : null;
            BoletoData boleto = payment.getMethod() == PaymentMethod.BOLETO && payment.getTicketUrl() != null
                    ? new BoletoData(payment.getTicketUrl(), payment.getBarcode())
                    : null;
            return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getMethod(),
                    payment.getStatus(), payment.getStatusDetail(), payment.getAmount(), payment.getInstallments(),
                    payment.getProviderId(), pix, boleto, payment.getExpiresAt(),
                    payment.getCreatedAt(), payment.getUpdatedAt());
        }
    }

    public record PixData(String qrCode, String qrCodeBase64, String ticketUrl) {
    }

    public record BoletoData(String url, String digitableLine) {
    }
}

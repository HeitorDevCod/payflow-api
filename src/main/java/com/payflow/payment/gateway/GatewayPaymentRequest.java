package com.payflow.payment.gateway;

import com.payflow.payment.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

public record GatewayPaymentRequest(
        String idempotencyKey,
        String externalReference,
        BigDecimal amount,
        String description,
        PaymentMethod method,
        Payer payer,
        Instant expiresAt,
        Card card
) {

    public record Payer(String email, String firstName, String lastName, String cpf, Address address) {
    }

    public record Address(String zipCode, String street, String number, String neighborhood,
                          String city, String state) {
    }

    public record Card(String token, String paymentMethodId, Long issuerId, int installments) {
    }
}

package com.payflow.payment.gateway.mercadopago;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Contratos JSON da API do Mercado Pago. Os nomes seguem camelCase e são
 * convertidos para snake_case pelo ObjectMapper dedicado do cliente.
 */
final class MercadoPagoApi {

    private MercadoPagoApi() {
    }

    record PaymentRequest(
            BigDecimal transactionAmount,
            String description,
            String paymentMethodId,
            String token,
            Integer installments,
            Long issuerId,
            String externalReference,
            String notificationUrl,
            String dateOfExpiration,
            Payer payer
    ) {
    }

    record Payer(String email, String firstName, String lastName, Identification identification, Address address) {
    }

    record Identification(String type, String number) {
    }

    record Address(String zipCode, String streetName, String streetNumber, String neighborhood,
                   String city, String federalUnit) {
    }

    record PaymentResponse(
            Long id,
            String status,
            String statusDetail,
            BigDecimal transactionAmount,
            String currencyId,
            String externalReference,
            OffsetDateTime dateOfExpiration,
            PointOfInteraction pointOfInteraction,
            TransactionDetails transactionDetails,
            Barcode barcode
    ) {
    }

    record PointOfInteraction(TransactionData transactionData) {
    }

    record TransactionData(String qrCode, String qrCodeBase64, String ticketUrl) {
    }

    record TransactionDetails(String externalResourceUrl, String digitableLine) {
    }

    record Barcode(String content) {
    }

    record SearchResponse(List<PaymentResponse> results) {
    }

    record StatusUpdate(String status) {
    }

    record PreapprovalRequest(
            String reason,
            String externalReference,
            String payerEmail,
            String cardTokenId,
            AutoRecurring autoRecurring,
            String status
    ) {
    }

    record AutoRecurring(Integer frequency, String frequencyType, BigDecimal transactionAmount, String currencyId) {
    }

    record PreapprovalResponse(
            String id,
            String status,
            String externalReference,
            OffsetDateTime nextPaymentDate,
            AutoRecurring autoRecurring
    ) {
    }

    record ErrorResponse(String message, String error, Integer status) {
    }
}

package com.payflow.payment.gateway.mercadopago;

import com.payflow.payment.domain.PaymentMethod;
import com.payflow.payment.gateway.GatewayPayment;
import com.payflow.payment.gateway.GatewayPaymentRequest;
import com.payflow.payment.gateway.GatewaySubscription;
import com.payflow.payment.gateway.GatewaySubscriptionRequest;
import com.payflow.payment.gateway.PaymentGateway;
import com.payflow.payment.gateway.mercadopago.MercadoPagoApi.Address;
import com.payflow.payment.gateway.mercadopago.MercadoPagoApi.AutoRecurring;
import com.payflow.payment.gateway.mercadopago.MercadoPagoApi.Identification;
import com.payflow.payment.gateway.mercadopago.MercadoPagoApi.Payer;
import com.payflow.payment.gateway.mercadopago.MercadoPagoApi.PaymentRequest;
import com.payflow.payment.gateway.mercadopago.MercadoPagoApi.PaymentResponse;
import com.payflow.payment.gateway.mercadopago.MercadoPagoApi.PreapprovalRequest;
import com.payflow.payment.gateway.mercadopago.MercadoPagoApi.PreapprovalResponse;
import com.payflow.payment.gateway.mercadopago.MercadoPagoApi.SearchResponse;
import com.payflow.payment.gateway.mercadopago.MercadoPagoApi.StatusUpdate;
import com.payflow.shared.exception.GatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class MercadoPagoClient implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);

    static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final String BOLETO_METHOD_ID = "bolbradesco";
    private static final String PIX_METHOD_ID = "pix";
    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter MP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final RestClient http;
    private final MercadoPagoProperties properties;

    public MercadoPagoClient(RestClient http, MercadoPagoProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    @Override
    public GatewayPayment createPayment(GatewayPaymentRequest request) {
        PaymentRequest body = toPaymentRequest(request);
        PaymentResponse response = call("criar pagamento", () -> http.post()
                .uri("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .header(IDEMPOTENCY_HEADER, request.idempotencyKey())
                .body(body)
                .retrieve()
                .body(PaymentResponse.class));
        return toGatewayPayment(response);
    }

    @Override
    public GatewayPayment fetchPayment(String providerId) {
        PaymentResponse response = call("consultar pagamento", () -> http.get()
                .uri("/v1/payments/{id}", providerId)
                .retrieve()
                .body(PaymentResponse.class));
        return toGatewayPayment(response);
    }

    @Override
    public Optional<GatewayPayment> findByExternalReference(String externalReference) {
        SearchResponse response = call("buscar pagamento", () -> http.get()
                .uri(uri -> uri.path("/v1/payments/search")
                        .queryParam("external_reference", externalReference)
                        .queryParam("sort", "date_created")
                        .queryParam("criteria", "desc")
                        .build())
                .retrieve()
                .body(SearchResponse.class));
        List<PaymentResponse> results = response == null || response.results() == null ? List.of() : response.results();
        return results.stream().findFirst().map(MercadoPagoClient::toGatewayPayment);
    }

    @Override
    public GatewayPayment cancelPayment(String providerId) {
        PaymentResponse response = call("cancelar pagamento", () -> http.put()
                .uri("/v1/payments/{id}", providerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new StatusUpdate("cancelled"))
                .retrieve()
                .body(PaymentResponse.class));
        return toGatewayPayment(response);
    }

    @Override
    public void refundPayment(String providerId, String idempotencyKey) {
        call("estornar pagamento", () -> http.post()
                .uri("/v1/payments/{id}/refunds", providerId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(IDEMPOTENCY_HEADER, idempotencyKey)
                .body(Map.of())
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public GatewaySubscription createSubscription(GatewaySubscriptionRequest request) {
        PreapprovalRequest body = new PreapprovalRequest(
                request.reason(),
                request.externalReference(),
                request.payerEmail(),
                request.cardToken(),
                new AutoRecurring(request.frequency(),
                        request.frequencyType() == GatewaySubscriptionRequest.FrequencyType.DAYS ? "days" : "months",
                        request.amount(), "BRL"),
                "authorized");
        PreapprovalResponse response = call("criar assinatura", () -> http.post()
                .uri("/preapproval")
                .contentType(MediaType.APPLICATION_JSON)
                .header(IDEMPOTENCY_HEADER, request.externalReference())
                .body(body)
                .retrieve()
                .body(PreapprovalResponse.class));
        return toGatewaySubscription(response);
    }

    @Override
    public GatewaySubscription fetchSubscription(String providerId) {
        PreapprovalResponse response = call("consultar assinatura", () -> http.get()
                .uri("/preapproval/{id}", providerId)
                .retrieve()
                .body(PreapprovalResponse.class));
        return toGatewaySubscription(response);
    }

    @Override
    public GatewaySubscription updateSubscriptionStatus(String providerId, GatewaySubscription.Status status) {
        PreapprovalResponse response = call("atualizar assinatura", () -> http.put()
                .uri("/preapproval/{id}", providerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new StatusUpdate(MercadoPagoStatusMapper.subscription(status)))
                .retrieve()
                .body(PreapprovalResponse.class));
        return toGatewaySubscription(response);
    }

    private <T> T call(String operation, Supplier<T> request) {
        if (!properties.hasAccessToken()) {
            throw new GatewayException("Mercado Pago não configurado", HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        try {
            T result = request.get();
            if (result == null) {
                throw new GatewayException("Resposta vazia do Mercado Pago ao " + operation, HttpStatus.BAD_GATEWAY.value());
            }
            return result;
        } catch (RestClientResponseException ex) {
            String providerMessage = extractMessage(ex);
            log.warn("Mercado Pago recusou '{}' com status {}: {}", operation, ex.getStatusCode().value(), providerMessage);
            throw new GatewayException("Falha ao " + operation + ": " + providerMessage, ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw new GatewayException("Mercado Pago indisponível ao " + operation, HttpStatus.GATEWAY_TIMEOUT.value(), ex);
        }
    }

    private static String extractMessage(RestClientResponseException ex) {
        try {
            MercadoPagoApi.ErrorResponse error = ex.getResponseBodyAs(MercadoPagoApi.ErrorResponse.class);
            if (error != null && error.message() != null) {
                return error.message();
            }
        } catch (RuntimeException ignored) {
            // corpo fora do formato esperado; cai para o texto bruto
        }
        String raw = ex.getResponseBodyAsString();
        return raw.isBlank() ? ex.getStatusText() : raw;
    }

    private PaymentRequest toPaymentRequest(GatewayPaymentRequest request) {
        GatewayPaymentRequest.Payer payer = request.payer();
        Address address = payer.address() == null ? null : new Address(
                payer.address().zipCode(), payer.address().street(), payer.address().number(),
                payer.address().neighborhood(), payer.address().city(), payer.address().state());
        Payer mpPayer = new Payer(payer.email(), payer.firstName(), payer.lastName(),
                new Identification("CPF", payer.cpf()), address);

        String methodId;
        String token = null;
        Long issuerId = null;
        int installments = 1;
        if (request.method() == PaymentMethod.CREDIT_CARD) {
            methodId = request.card().paymentMethodId();
            token = request.card().token();
            issuerId = request.card().issuerId();
            installments = request.card().installments();
        } else {
            methodId = request.method() == PaymentMethod.PIX ? PIX_METHOD_ID : BOLETO_METHOD_ID;
        }

        return new PaymentRequest(
                request.amount(),
                request.description(),
                methodId,
                token,
                installments,
                issuerId,
                request.externalReference(),
                blankToNull(properties.notificationUrl()),
                formatDate(request.expiresAt()),
                mpPayer);
    }

    private static GatewayPayment toGatewayPayment(PaymentResponse response) {
        String qrCode = null;
        String qrCodeBase64 = null;
        String ticketUrl = null;
        if (response.pointOfInteraction() != null && response.pointOfInteraction().transactionData() != null) {
            var data = response.pointOfInteraction().transactionData();
            qrCode = data.qrCode();
            qrCodeBase64 = data.qrCodeBase64();
            ticketUrl = data.ticketUrl();
        }
        String barcode = null;
        if (response.transactionDetails() != null) {
            if (response.transactionDetails().externalResourceUrl() != null) {
                ticketUrl = response.transactionDetails().externalResourceUrl();
            }
            barcode = response.transactionDetails().digitableLine();
        }
        if (barcode == null && response.barcode() != null) {
            barcode = response.barcode().content();
        }
        return new GatewayPayment(
                response.id() == null ? null : String.valueOf(response.id()),
                MercadoPagoStatusMapper.payment(response.status()),
                response.statusDetail(),
                response.transactionAmount(),
                response.currencyId(),
                response.externalReference(),
                response.dateOfExpiration() == null ? null : response.dateOfExpiration().toInstant(),
                qrCode,
                qrCodeBase64,
                ticketUrl,
                barcode);
    }

    private static GatewaySubscription toGatewaySubscription(PreapprovalResponse response) {
        return new GatewaySubscription(
                response.id(),
                MercadoPagoStatusMapper.subscription(response.status()),
                response.externalReference(),
                response.autoRecurring() == null ? null : response.autoRecurring().transactionAmount(),
                response.nextPaymentDate() == null ? null : response.nextPaymentDate().toInstant());
    }

    static String formatDate(Instant instant) {
        return instant == null ? null : MP_DATE.format(instant.atZone(BRAZIL));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

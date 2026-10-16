package com.payflow.payment.gateway.mercadopago;

import com.payflow.payment.domain.PaymentMethod;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.gateway.GatewayPayment;
import com.payflow.payment.gateway.GatewayPaymentRequest;
import com.payflow.payment.gateway.GatewaySubscription;
import com.payflow.payment.gateway.GatewaySubscriptionRequest;
import com.payflow.shared.exception.GatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MercadoPagoClientTest {

    private static final String BASE = "https://api.mercadopago.test";

    private MockRestServiceServer server;
    private MercadoPagoClient client;

    @BeforeEach
    void setUp() {
        var properties = new MercadoPagoProperties(BASE, "TEST-token", null, "secret", "https://example.com/webhooks",
                Duration.ofMinutes(5), Duration.ofSeconds(1), Duration.ofSeconds(1));
        RestClient.Builder builder = MercadoPagoConfig.configure(RestClient.builder(), properties);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MercadoPagoClient(builder.build(), properties);
    }

    @Test
    void createsPixPaymentAndParsesQrCode() {
        server.expect(requestTo(BASE + "/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-token"))
                .andExpect(header("X-Idempotency-Key", "idem-key-001"))
                .andExpect(jsonPath("$.payment_method_id").value("pix"))
                .andExpect(jsonPath("$.transaction_amount").value(150.0))
                .andExpect(jsonPath("$.external_reference").value("local-id"))
                .andExpect(jsonPath("$.notification_url").value("https://example.com/webhooks"))
                .andExpect(jsonPath("$.payer.identification.type").value("CPF"))
                .andExpect(jsonPath("$.payer.identification.number").value("52998224725"))
                .andExpect(jsonPath("$.date_of_expiration").value("2026-10-16T09:30:00.000-03:00"))
                .andRespond(withSuccess("""
                        {
                          "id": 1234567890,
                          "status": "pending",
                          "status_detail": "pending_waiting_transfer",
                          "transaction_amount": 150.00,
                          "currency_id": "BRL",
                          "external_reference": "local-id",
                          "date_of_expiration": "2026-10-16T09:30:00.000-03:00",
                          "point_of_interaction": {
                            "transaction_data": {
                              "qr_code": "00020126...",
                              "qr_code_base64": "iVBORw0KGgo=",
                              "ticket_url": "https://mpago.la/pix"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        GatewayPayment payment = client.createPayment(pixRequest());

        assertThat(payment.providerId()).isEqualTo("1234567890");
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.amount()).isEqualByComparingTo("150.00");
        assertThat(payment.pixQrCode()).isEqualTo("00020126...");
        assertThat(payment.ticketUrl()).isEqualTo("https://mpago.la/pix");
        assertThat(payment.expiresAt()).isEqualTo(Instant.parse("2026-10-16T12:30:00Z"));
        server.verify();
    }

    @Test
    void parsesBoletoData() {
        server.expect(requestTo(BASE + "/v1/payments/42"))
                .andRespond(withSuccess("""
                        {
                          "id": 42,
                          "status": "pending",
                          "transaction_amount": 99.9,
                          "external_reference": "ref",
                          "transaction_details": {
                            "external_resource_url": "https://boleto.example/42.pdf",
                            "digitable_line": "23793.38128 60000.000003 00000.000400 1 99990000009990"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        GatewayPayment payment = client.fetchPayment("42");

        assertThat(payment.ticketUrl()).isEqualTo("https://boleto.example/42.pdf");
        assertThat(payment.barcode()).startsWith("23793");
    }

    @Test
    void searchesByExternalReference() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/payments/search")))
                .andExpect(queryParam("external_reference", "abc"))
                .andRespond(withSuccess("""
                        {"results": [{"id": 7, "status": "approved", "transaction_amount": 10, "external_reference": "abc"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findByExternalReference("abc"))
                .get()
                .extracting(GatewayPayment::status)
                .isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void translatesProviderErrors() {
        server.expect(requestTo(BASE + "/v1/payments"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"invalid card token\",\"error\":\"bad_request\",\"status\":400}"));

        assertThatThrownBy(() -> client.createPayment(pixRequest()))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("invalid card token")
                .satisfies(ex -> assertThat(((GatewayException) ex).isClientError()).isTrue());
    }

    @Test
    void serverErrorsAreNotClientErrors() {
        server.expect(requestTo(BASE + "/v1/payments/1")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.fetchPayment("1"))
                .isInstanceOf(GatewayException.class)
                .satisfies(ex -> assertThat(((GatewayException) ex).isClientError()).isFalse());
    }

    @Test
    void createsSubscription() {
        server.expect(requestTo(BASE + "/preapproval"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.auto_recurring.frequency_type").value("months"))
                .andExpect(jsonPath("$.auto_recurring.currency_id").value("BRL"))
                .andExpect(jsonPath("$.card_token_id").value("card-token"))
                .andExpect(jsonPath("$.status").value("authorized"))
                .andRespond(withSuccess("""
                        {
                          "id": "2c9380848",
                          "status": "authorized",
                          "external_reference": "sub-1",
                          "next_payment_date": "2026-11-16T10:00:00.000-03:00",
                          "auto_recurring": {"frequency": 1, "frequency_type": "months", "transaction_amount": 39.9, "currency_id": "BRL"}
                        }
                        """, MediaType.APPLICATION_JSON));

        GatewaySubscription subscription = client.createSubscription(new GatewaySubscriptionRequest(
                "sub-1", "Plano Pro", "cliente@example.com", "card-token", new BigDecimal("39.90"),
                1, GatewaySubscriptionRequest.FrequencyType.MONTHS));

        assertThat(subscription.status()).isEqualTo(GatewaySubscription.Status.AUTHORIZED);
        assertThat(subscription.amount()).isEqualByComparingTo("39.90");
        assertThat(subscription.nextPaymentDate()).isEqualTo(Instant.parse("2026-11-16T13:00:00Z"));
    }

    @Test
    void refusesToCallWithoutAccessToken() {
        var properties = new MercadoPagoProperties(BASE, "", null, "secret", null,
                Duration.ofMinutes(5), Duration.ofSeconds(1), Duration.ofSeconds(1));
        var unconfigured = new MercadoPagoClient(RestClient.create(), properties);

        assertThatThrownBy(() -> unconfigured.fetchPayment("1"))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("não configurado");
    }

    private static GatewayPaymentRequest pixRequest() {
        var payer = new GatewayPaymentRequest.Payer("cliente@example.com", "Maria", "Silva", "52998224725", null);
        return new GatewayPaymentRequest("idem-key-001", "local-id", new BigDecimal("150.00"), "Pedido 1",
                PaymentMethod.PIX, payer, Instant.parse("2026-10-16T12:30:00Z"), null);
    }
}

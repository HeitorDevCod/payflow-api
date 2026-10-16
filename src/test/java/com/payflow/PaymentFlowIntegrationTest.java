package com.payflow;

import com.jayway.jsonpath.JsonPath;
import com.payflow.payment.domain.PaymentMethod;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.gateway.GatewayPayment;
import com.payflow.payment.gateway.GatewayPaymentRequest;
import com.payflow.payment.gateway.PaymentGateway;
import com.payflow.shared.exception.GatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentFlowIntegrationTest {

    private static final String MENTORING = "749990f6-6ccd-40b3-ab75-e8061a3fee9f";
    private static final String STICKERS = "9e4a3053-2c23-4579-9374-d74ef7b7745f";
    private static final String WEBHOOK_SECRET = "webhook-test-secret";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PaymentGateway gateway;

    private String token;

    @BeforeEach
    void authenticate() throws Exception {
        String email = "cliente-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Maria Silva","email":"%s","password":"senha-forte-1","document":"529.982.247-25"}
                        """.formatted(email)))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"senha-forte-1"}
                        """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = JsonPath.read(body, "$.accessToken");
    }

    @Test
    void pixPaymentIsConfirmedOnlyAfterSignedWebhook() throws Exception {
        String orderId = createOrder(2);
        when(gateway.createPayment(any())).thenAnswer(inv -> remote(inv.getArgument(0), "1001", PaymentStatus.PENDING, null));

        String created = pay("/payments/pix", "pix-key-0001", "{\"orderId\":\"%s\"}".formatted(orderId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(300.0))
                .andExpect(jsonPath("$.pix.qrCode").value("000201-pix"))
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(created, "$.id");

        pay("/payments/pix", "pix-key-0001", "{\"orderId\":\"%s\"}".formatted(orderId))
                .andExpect(jsonPath("$.id").value(paymentId));
        verify(gateway, times(1)).createPayment(any());

        pay("/payments/pix", "pix-key-0002", "{\"orderId\":\"%s\"}".formatted(orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment_in_progress"));

        when(gateway.fetchPayment("1001")).thenReturn(new GatewayPayment("1001", PaymentStatus.APPROVED, "accredited",
                new BigDecimal("300.00"), "BRL", paymentId, null, null, null, null, null));

        webhook("1001", "req-" + paymentId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processed"));
        webhook("1001", "req-" + paymentId).andExpect(jsonPath("$.status").value("duplicate"));

        mvc.perform(get("/orders/" + orderId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(get("/payments/" + paymentId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void divergentAmountFromProviderSendsOrderToReview() throws Exception {
        String orderId = createOrder(1);
        when(gateway.createPayment(any())).thenAnswer(inv -> remote(inv.getArgument(0), "2001",
                PaymentStatus.APPROVED, new BigDecimal("1.00")));

        pay("/payments/card", "card-key-0001", cardBody(orderId, 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(get("/orders/" + orderId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("PAYMENT_REVIEW"));
    }

    @Test
    void rejectedCardMarksPaymentFailedAndAllowsRetry() throws Exception {
        String orderId = createOrder(1);
        when(gateway.createPayment(any())).thenThrow(new GatewayException("Falha ao criar pagamento: invalid card token", 400));

        pay("/payments/card", "card-key-0002", cardBody(orderId, 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("payment_provider_error"));

        doAnswer(inv -> remote(inv.getArgument(0), "3001", PaymentStatus.APPROVED, null))
                .when(gateway).createPayment(any());

        pay("/payments/card", "card-key-0003", cardBody(orderId, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.installments").value(3));

        mvc.perform(get("/orders/" + orderId + "/payments").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].status").value("FAILED"));
    }

    @Test
    void validatesPaymentRequestsBeforeCallingProvider() throws Exception {
        String orderId = createOrder(1);

        pay("/payments/card", "short", cardBody(orderId, 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("invalid_idempotency_key"));

        pay("/payments/card", "card-key-0004", cardBody(orderId, 13))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("invalid_installments"));

        String cheapOrderId = createOrder(STICKERS, 1);
        pay("/payments/card", "card-key-0005", cardBody(cheapOrderId, 12))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("installment_too_small"));

        pay("/payments/boleto", "boleto-key-01", """
                {"orderId":"%s","address":{"zipCode":"123","street":"Rua A","number":"1","neighborhood":"Centro","city":"Santos","state":"SP"}}
                """.formatted(orderId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['address.zipCode']").exists());

        mvc.perform(post("/payments/pix").header("Idempotency-Key", "anon-key-01")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"%s\"}".formatted(orderId)))
                .andExpect(status().isUnauthorized());

        verify(gateway, times(0)).createPayment(any());
    }

    @Test
    void unsignedWebhookIsRejected() throws Exception {
        mvc.perform(post("/webhooks/mercadopago").param("data.id", "1").param("type", "payment")
                        .header("x-request-id", "abc")
                        .header("x-signature", "ts=" + Instant.now().getEpochSecond() + ",v1=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verify(gateway, times(0)).fetchPayment(any());
    }

    private String createOrder(int quantity) throws Exception {
        return createOrder(MENTORING, quantity);
    }

    private String createOrder(String productId, int quantity) throws Exception {
        String body = mvc.perform(post("/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"%s\",\"quantity\":%d}]}".formatted(productId, quantity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions pay(String path, String idempotencyKey, String body) throws Exception {
        return mvc.perform(post(path)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions webhook(String dataId, String requestId) throws Exception {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String signature = hmac("id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";");
        return mvc.perform(post("/webhooks/mercadopago")
                .param("data.id", dataId)
                .param("type", "payment")
                .header("x-request-id", requestId)
                .header("x-signature", "ts=" + ts + ",v1=" + signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"payment.updated\",\"type\":\"payment\",\"data\":{\"id\":\"" + dataId + "\"}}"));
    }

    private static String cardBody(String orderId, int installments) {
        return """
                {"orderId":"%s","token":"card-token-123","paymentMethodId":"master","installments":%d}
                """.formatted(orderId, installments);
    }

    private static GatewayPayment remote(GatewayPaymentRequest request, String providerId, PaymentStatus status,
                                         BigDecimal amountOverride) {
        BigDecimal amount = amountOverride != null ? amountOverride : request.amount();
        String qr = request.method() == PaymentMethod.PIX ? "000201-pix" : null;
        return new GatewayPayment(providerId, status, status.name().toLowerCase(), amount, "BRL",
                request.externalReference(), request.expiresAt(), qr, qr == null ? null : "base64", null, null);
    }

    private static String hmac(String manifest) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }
}

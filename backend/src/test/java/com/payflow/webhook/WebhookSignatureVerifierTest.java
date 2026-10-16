package com.payflow.webhook;

import com.payflow.payment.gateway.mercadopago.MercadoPagoProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private static final String SECRET = "super-secret";
    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(
            properties(SECRET), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsSignatureBuiltFromTheDocumentedManifest() throws Exception {
        String ts = String.valueOf(NOW.getEpochSecond());
        String v1 = hmac("id:123456;request-id:req-1;ts:" + ts + ";");

        var result = verifier.verify("ts=" + ts + ",v1=" + v1, "req-1", "123456");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void acceptsMillisecondTimestamps() throws Exception {
        String ts = String.valueOf(NOW.toEpochMilli());
        String v1 = hmac("id:abc123;request-id:req-2;ts:" + ts + ";");

        assertThat(verifier.verify("ts=" + ts + ", v1=" + v1, "req-2", "ABC123").valid()).isTrue();
    }

    @Test
    void rejectsTamperedResourceId() throws Exception {
        String ts = String.valueOf(NOW.getEpochSecond());
        String v1 = hmac("id:123456;request-id:req-1;ts:" + ts + ";");

        var result = verifier.verify("ts=" + ts + ",v1=" + v1, "req-1", "999999");

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("assinatura não confere");
    }

    @Test
    void rejectsReplayOutsideTolerance() throws Exception {
        String ts = String.valueOf(NOW.minus(Duration.ofMinutes(30)).getEpochSecond());
        String v1 = hmac("id:1;request-id:r;ts:" + ts + ";");

        assertThat(verifier.verify("ts=" + ts + ",v1=" + v1, "r", "1").valid()).isFalse();
    }

    @Test
    void rejectsMissingOrMalformedHeader() {
        assertThat(verifier.verify(null, "r", "1").valid()).isFalse();
        assertThat(verifier.verify("v1=abc", "r", "1").valid()).isFalse();
        assertThat(verifier.verify("ts=abc,v1=abc", "r", "1").valid()).isFalse();
    }

    @Test
    void rejectsEverythingWhenSecretIsNotConfigured() {
        var unconfigured = new WebhookSignatureVerifier(properties(""), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(unconfigured.verify("ts=1,v1=abc", "r", "1").valid()).isFalse();
    }

    @Test
    void manifestOmitsMissingParts() {
        assertThat(WebhookSignatureVerifier.manifest(null, null, "10")).isEqualTo("ts:10;");
        assertThat(WebhookSignatureVerifier.manifest("ABC", "r1", "10")).isEqualTo("id:abc;request-id:r1;ts:10;");
    }

    private static String hmac(String manifest) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }

    private static MercadoPagoProperties properties(String secret) {
        return new MercadoPagoProperties("https://api.mercadopago.test", "token", null, secret, null,
                Duration.ofMinutes(5), Duration.ofSeconds(1), Duration.ofSeconds(1));
    }
}

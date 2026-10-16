package com.payflow.webhook;

import com.payflow.payment.gateway.mercadopago.MercadoPagoProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Valida o cabeçalho {@code x-signature} enviado pelo Mercado Pago.
 * <p>
 * O provedor assina o manifesto {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} com HMAC-SHA256
 * usando a chave secreta configurada no painel. Partes ausentes são omitidas do manifesto.
 */
@Component
public class WebhookSignatureVerifier {

    private static final String HMAC = "HmacSHA256";

    private final MercadoPagoProperties properties;
    private final Clock clock;

    public WebhookSignatureVerifier(MercadoPagoProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Result verify(String signatureHeader, String requestId, String dataId) {
        if (!properties.hasWebhookSecret()) {
            return Result.invalid("segredo do webhook não configurado");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return Result.invalid("cabeçalho x-signature ausente");
        }

        String ts = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            switch (pair[0].trim()) {
                case "ts" -> ts = pair[1].trim();
                case "v1" -> v1 = pair[1].trim();
                default -> {
                }
            }
        }
        if (ts == null || v1 == null) {
            return Result.invalid("x-signature sem ts ou v1");
        }

        Instant sentAt;
        try {
            long raw = Long.parseLong(ts);
            sentAt = raw > 100_000_000_000L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
        } catch (NumberFormatException ex) {
            return Result.invalid("timestamp inválido");
        }
        Duration skew = Duration.between(sentAt, clock.instant()).abs();
        if (skew.compareTo(properties.webhookTolerance()) > 0) {
            return Result.invalid("timestamp fora da janela de tolerância");
        }

        String expected = sign(manifest(dataId, requestId, ts));
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        return matches ? Result.ok() : Result.invalid("assinatura não confere");
    }

    static String manifest(String dataId, String requestId, String ts) {
        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            String id = dataId.chars().allMatch(Character::isLetterOrDigit) ? dataId.toLowerCase(Locale.ROOT) : dataId;
            manifest.append("id:").append(id).append(';');
        }
        if (requestId != null && !requestId.isBlank()) {
            manifest.append("request-id:").append(requestId).append(';');
        }
        manifest.append("ts:").append(ts).append(';');
        return manifest.toString();
    }

    String sign(String manifest) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), HMAC));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 indisponível", ex);
        }
    }

    public record Result(boolean valid, String reason) {

        static Result ok() {
            return new Result(true, null);
        }

        static Result invalid(String reason) {
            return new Result(false, reason);
        }
    }
}

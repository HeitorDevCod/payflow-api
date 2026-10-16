package com.payflow.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Webhooks")
@RestController
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookSignatureVerifier verifier;
    private final WebhookService webhookService;

    public WebhookController(WebhookSignatureVerifier verifier, WebhookService webhookService) {
        this.verifier = verifier;
        this.webhookService = webhookService;
    }

    @Operation(summary = "Recebe notificações do Mercado Pago (pagamentos e assinaturas)")
    @PostMapping("/webhooks/mercadopago")
    public ResponseEntity<Map<String, String>> mercadoPago(
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestParam(name = "data.id", required = false) String queryDataId,
            @RequestParam(name = "type", required = false) String queryType,
            @RequestBody(required = false) JsonNode body) {

        String dataId = queryDataId != null ? queryDataId : text(body, "data", "id");
        String type = queryType != null ? queryType : text(body, "type", null);

        WebhookSignatureVerifier.Result verification = verifier.verify(signature, requestId, queryDataId);
        if (!verification.valid()) {
            log.warn("Webhook rejeitado (request-id {}): {}", requestId, verification.reason());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "rejected"));
        }
        if (dataId == null || dataId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "missing_resource"));
        }

        WebhookService.Outcome outcome = webhookService.handle(requestId, type, dataId);
        return ResponseEntity.ok(Map.of("status", outcome.name().toLowerCase()));
    }

    private static String text(JsonNode body, String field, String nested) {
        if (body == null || !body.hasNonNull(field)) {
            return null;
        }
        JsonNode node = body.get(field);
        if (nested != null) {
            node = node.get(nested);
        }
        return node == null || node.isNull() ? null : node.asText();
    }
}

package com.payflow.payment.api;

import com.payflow.auth.security.AuthUser;
import com.payflow.payment.api.PaymentDtos.BoletoPaymentRequest;
import com.payflow.payment.api.PaymentDtos.CardPaymentRequest;
import com.payflow.payment.api.PaymentDtos.PaymentResponse;
import com.payflow.payment.api.PaymentDtos.PixPaymentRequest;
import com.payflow.payment.application.PaymentService;
import com.payflow.payment.domain.PaymentAudit;
import com.payflow.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Pagamentos")
@RestController
public class PaymentController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Gera uma cobrança Pix (QR Code e copia e cola)")
    @PostMapping("/payments/pix")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse pix(@AuthenticationPrincipal AuthUser user,
                               @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
                               @Valid @RequestBody PixPaymentRequest request) {
        return paymentService.payWithPix(user, idempotencyKey, request);
    }

    @Operation(summary = "Paga com cartão de crédito tokenizado pelo SDK do Mercado Pago")
    @PostMapping("/payments/card")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse card(@AuthenticationPrincipal AuthUser user,
                                @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
                                @Valid @RequestBody CardPaymentRequest request) {
        return paymentService.payWithCard(user, idempotencyKey, request);
    }

    @Operation(summary = "Emite um boleto bancário")
    @PostMapping("/payments/boleto")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse boleto(@AuthenticationPrincipal AuthUser user,
                                  @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
                                  @Valid @RequestBody BoletoPaymentRequest request) {
        return paymentService.payWithBoleto(user, idempotencyKey, request);
    }

    @GetMapping("/payments/{id}")
    public PaymentResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return paymentService.get(user, id);
    }

    @Operation(summary = "Consulta o status atualizado diretamente no provedor")
    @PostMapping("/payments/{id}/refresh")
    public PaymentResponse refresh(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return paymentService.refresh(user, id);
    }

    @Operation(summary = "Cancela um Pix ou boleto ainda não pago")
    @PostMapping("/payments/{id}/cancel")
    public PaymentResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return paymentService.cancel(user, id);
    }

    @Operation(summary = "Estorna um pagamento aprovado")
    @PostMapping("/payments/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentResponse refund(@PathVariable UUID id) {
        return paymentService.refund(id);
    }

    @Operation(summary = "Trilha de auditoria das mudanças de status")
    @GetMapping("/payments/{id}/history")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditEntry> history(@PathVariable UUID id) {
        return paymentService.history(id).stream().map(AuditEntry::from).toList();
    }

    @GetMapping("/orders/{orderId}/payments")
    public List<PaymentResponse> byOrder(@AuthenticationPrincipal AuthUser user, @PathVariable UUID orderId) {
        return paymentService.listByOrder(user, orderId);
    }

    public record AuditEntry(PaymentStatus from, PaymentStatus to, PaymentAudit.Source source, String note,
                             Instant at) {

        static AuditEntry from(PaymentAudit audit) {
            return new AuditEntry(audit.getFromStatus(), audit.getToStatus(), audit.getSource(), audit.getNote(),
                    audit.getCreatedAt());
        }
    }
}

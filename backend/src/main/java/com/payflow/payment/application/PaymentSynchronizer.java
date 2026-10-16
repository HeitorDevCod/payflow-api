package com.payflow.payment.application;

import com.payflow.order.domain.Order;
import com.payflow.order.domain.OrderRepository;
import com.payflow.order.domain.OrderStatus;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAudit;
import com.payflow.payment.domain.PaymentAudit.Source;
import com.payflow.payment.domain.PaymentAuditRepository;
import com.payflow.payment.domain.PaymentRepository;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.gateway.GatewayPayment;
import com.payflow.payment.gateway.PaymentGateway;
import com.payflow.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * Aplica ao estado local o que o provedor informa sobre um pagamento.
 * Toda confirmação passa por aqui, venha da resposta síncrona, do webhook ou da reconciliação,
 * e nada é aprovado sem conferir valor, moeda e referência externa.
 */
@Component
public class PaymentSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(PaymentSynchronizer.class);
    private static final String CURRENCY = "BRL";

    private final PaymentRepository payments;
    private final PaymentAuditRepository audits;
    private final OrderRepository orders;
    private final PaymentGateway gateway;
    private final TransactionTemplate tx;

    public PaymentSynchronizer(PaymentRepository payments, PaymentAuditRepository audits, OrderRepository orders,
                               PaymentGateway gateway, PlatformTransactionManager transactionManager) {
        this.payments = payments;
        this.audits = audits;
        this.orders = orders;
        this.gateway = gateway;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public Payment apply(UUID paymentId, GatewayPayment remote, Source source) {
        return tx.execute(status -> {
            Payment payment = payments.findById(paymentId)
                    .orElseThrow(() -> BusinessException.notFound("Pagamento"));
            return applyTo(payment, remote, source);
        });
    }

    /**
     * Busca o estado atual direto no provedor, sem confiar no conteúdo da notificação recebida.
     */
    public Optional<Payment> syncByProviderId(String providerId, Source source) {
        GatewayPayment remote = gateway.fetchPayment(providerId);
        Optional<Payment> local = payments.findByProviderId(providerId)
                .or(() -> parseUuid(remote.externalReference()).flatMap(payments::findById));
        if (local.isEmpty()) {
            log.warn("Pagamento {} do provedor não corresponde a nenhum registro local", providerId);
            return Optional.empty();
        }
        return Optional.of(apply(local.get().getId(), remote, source));
    }

    public Payment markFailed(UUID paymentId, String reason, Source source) {
        return tx.execute(status -> {
            Payment payment = payments.findById(paymentId)
                    .orElseThrow(() -> BusinessException.notFound("Pagamento"));
            PaymentStatus from = payment.getStatus();
            if (!from.canTransitionTo(PaymentStatus.FAILED)) {
                return payment;
            }
            if (payment.changeStatus(PaymentStatus.FAILED, truncate(reason, 80))) {
                audits.save(new PaymentAudit(payment.getId(), from, PaymentStatus.FAILED, source, reason));
                orders.findByIdForUpdate(payment.getOrderId()).ifPresent(Order::reopenForPayment);
            }
            return payment;
        });
    }

    private Payment applyTo(Payment payment, GatewayPayment remote, Source source) {
        if (remote.providerId() != null) {
            payment.linkProvider(remote.providerId());
        }

        Optional<String> divergence = findDivergence(payment, remote);
        if (divergence.isPresent()) {
            log.error("Divergência no pagamento {} ({}): {}", payment.getId(), remote.providerId(), divergence.get());
            audits.save(new PaymentAudit(payment.getId(), payment.getStatus(), payment.getStatus(), source,
                    "bloqueado: " + divergence.get()));
            if (remote.status() == PaymentStatus.APPROVED) {
                orders.findByIdForUpdate(payment.getOrderId()).ifPresent(Order::markForReview);
            }
            return payment;
        }

        payment.updateExpiration(remote.expiresAt());
        if (remote.pixQrCode() != null) {
            payment.attachPixData(remote.pixQrCode(), remote.pixQrCodeBase64(), remote.ticketUrl());
        } else if (remote.ticketUrl() != null || remote.barcode() != null) {
            payment.attachBoletoData(remote.ticketUrl(), remote.barcode());
        }

        PaymentStatus from = payment.getStatus();
        if (!from.canTransitionTo(remote.status())) {
            log.info("Ignorando transição {} -> {} do pagamento {} (notificação fora de ordem)",
                    from, remote.status(), payment.getId());
            return payment;
        }
        if (payment.changeStatus(remote.status(), truncate(remote.statusDetail(), 80))) {
            audits.save(new PaymentAudit(payment.getId(), from, remote.status(), source, remote.statusDetail()));
            updateOrder(payment);
        }
        return payment;
    }

    private void updateOrder(Payment payment) {
        Order order = orders.findByIdForUpdate(payment.getOrderId())
                .orElseThrow(() -> BusinessException.notFound("Pedido"));
        switch (payment.getStatus()) {
            case APPROVED -> {
                if (order.getStatus() == OrderStatus.CANCELED || order.getStatus() == OrderStatus.REFUNDED) {
                    log.error("Pagamento {} aprovado para o pedido {} em status {}: requer estorno manual",
                            payment.getId(), order.getId(), order.getStatus());
                } else {
                    order.markPaid();
                }
            }
            case REFUNDED, CHARGED_BACK -> order.markRefunded();
            case REJECTED, CANCELED, FAILED -> order.reopenForPayment();
            default -> {
            }
        }
    }

    static Optional<String> findDivergence(Payment payment, GatewayPayment remote) {
        if (remote.externalReference() == null || !remote.externalReference().equals(payment.getId().toString())) {
            return Optional.of("referência externa diferente (" + remote.externalReference() + ")");
        }
        if (remote.amount() == null || remote.amount().compareTo(payment.getAmount()) != 0) {
            return Optional.of("valor " + remote.amount() + " difere do esperado " + payment.getAmount());
        }
        if (remote.currency() != null && !CURRENCY.equalsIgnoreCase(remote.currency())) {
            return Optional.of("moeda inesperada " + remote.currency());
        }
        return Optional.empty();
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}

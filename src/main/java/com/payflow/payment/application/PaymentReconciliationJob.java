package com.payflow.payment.application;

import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAudit.Source;
import com.payflow.payment.domain.PaymentRepository;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.gateway.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Rede de segurança para webhooks perdidos: revisa pagamentos que estão parados
 * aguardando confirmação e consulta o provedor.
 */
@Component
@ConditionalOnProperty(prefix = "payments.reconciliation", name = "enabled", havingValue = "true")
public class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);
    private static final int BATCH_SIZE = 100;

    private final PaymentRepository payments;
    private final PaymentSynchronizer synchronizer;
    private final PaymentGateway gateway;
    private final PaymentProperties properties;

    public PaymentReconciliationJob(PaymentRepository payments, PaymentSynchronizer synchronizer,
                                    PaymentGateway gateway, PaymentProperties properties) {
        this.payments = payments;
        this.synchronizer = synchronizer;
        this.gateway = gateway;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${payments.reconciliation.interval}",
            initialDelayString = "${payments.reconciliation.interval}")
    public void run() {
        Instant staleBefore = Instant.now().minus(properties.reconciliation().staleAfter());
        List<Payment> stale = payments.findByStatusInAndUpdatedAtBefore(
                PaymentStatus.AWAITING_CONFIRMATION, staleBefore, PageRequest.of(0, BATCH_SIZE));
        if (stale.isEmpty()) {
            return;
        }
        log.info("Reconciliando {} pagamentos pendentes", stale.size());
        for (Payment payment : stale) {
            try {
                reconcile(payment, staleBefore);
            } catch (RuntimeException ex) {
                log.warn("Falha ao reconciliar o pagamento {}: {}", payment.getId(), ex.getMessage());
            }
        }
    }

    void reconcile(Payment payment, Instant staleBefore) {
        if (payment.getProviderId() != null) {
            synchronizer.syncByProviderId(payment.getProviderId(), Source.RECONCILIATION);
            return;
        }
        var remote = gateway.findByExternalReference(payment.getId().toString());
        if (remote.isPresent()) {
            synchronizer.apply(payment.getId(), remote.get(), Source.RECONCILIATION);
        } else if (payment.getCreatedAt().isBefore(staleBefore)) {
            synchronizer.markFailed(payment.getId(), "not_submitted", Source.RECONCILIATION);
        }
    }
}

package com.payflow.webhook;

import com.payflow.payment.application.PaymentSynchronizer;
import com.payflow.payment.domain.PaymentAudit.Source;
import com.payflow.subscription.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final PaymentSynchronizer paymentSynchronizer;
    private final SubscriptionService subscriptionService;
    private final WebhookEventRepository events;

    public WebhookService(PaymentSynchronizer paymentSynchronizer, SubscriptionService subscriptionService,
                          WebhookEventRepository events) {
        this.paymentSynchronizer = paymentSynchronizer;
        this.subscriptionService = subscriptionService;
        this.events = events;
    }

    public Outcome handle(String requestId, String topic, String resourceId) {
        if (requestId != null && events.existsByRequestId(requestId)) {
            log.debug("Notificação {} já processada", requestId);
            return Outcome.DUPLICATE;
        }

        String normalized = topic == null ? "" : topic.toLowerCase(Locale.ROOT);
        Outcome outcome = switch (normalized) {
            case "payment" -> {
                paymentSynchronizer.syncByProviderId(resourceId, Source.WEBHOOK);
                yield Outcome.PROCESSED;
            }
            case "subscription_preapproval", "preapproval" -> {
                subscriptionService.syncByProviderId(resourceId);
                yield Outcome.PROCESSED;
            }
            default -> {
                log.info("Tópico de webhook ignorado: {}", topic);
                yield Outcome.IGNORED;
            }
        };

        if (requestId != null) {
            try {
                events.save(new WebhookEvent(requestId, normalized, resourceId));
            } catch (DataIntegrityViolationException ex) {
                log.debug("Notificação {} registrada em paralelo", requestId);
            }
        }
        return outcome;
    }

    public enum Outcome {
        PROCESSED,
        IGNORED,
        DUPLICATE
    }
}

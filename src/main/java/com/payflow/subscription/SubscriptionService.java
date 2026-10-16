package com.payflow.subscription;

import com.payflow.auth.domain.UserRepository;
import com.payflow.auth.security.AuthUser;
import com.payflow.payment.gateway.GatewaySubscription;
import com.payflow.payment.gateway.GatewaySubscription.Status;
import com.payflow.payment.gateway.GatewaySubscriptionRequest;
import com.payflow.payment.gateway.GatewaySubscriptionRequest.FrequencyType;
import com.payflow.payment.gateway.PaymentGateway;
import com.payflow.shared.exception.BusinessException;
import com.payflow.shared.exception.GatewayException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private static final Map<Status, Set<Status>> ALLOWED = Map.of(
            Status.PENDING, EnumSet.of(Status.AUTHORIZED, Status.CANCELED),
            Status.AUTHORIZED, EnumSet.of(Status.PAUSED, Status.CANCELED),
            Status.PAUSED, EnumSet.of(Status.AUTHORIZED, Status.CANCELED),
            Status.CANCELED, EnumSet.noneOf(Status.class));

    private final SubscriptionRepository subscriptions;
    private final UserRepository users;
    private final PaymentGateway gateway;
    private final TransactionTemplate tx;

    public SubscriptionService(SubscriptionRepository subscriptions, UserRepository users, PaymentGateway gateway,
                               PlatformTransactionManager transactionManager) {
        this.subscriptions = subscriptions;
        this.users = users;
        this.gateway = gateway;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public SubscriptionResponse create(AuthUser user, CreateSubscriptionRequest request) {
        validateFrequency(request.frequency(), request.frequencyType());
        String email = users.findById(user.id())
                .orElseThrow(() -> BusinessException.notFound("Usuário"))
                .getEmail();

        Subscription subscription = tx.execute(status -> subscriptions.save(new Subscription(
                user.id(), request.reason().trim(), request.amount(), request.frequency(), request.frequencyType())));

        GatewaySubscription remote;
        try {
            remote = gateway.createSubscription(new GatewaySubscriptionRequest(
                    subscription.getId().toString(), subscription.getReason(), email, request.cardToken(),
                    subscription.getAmount(), subscription.getFrequency(), subscription.getFrequencyType()));
        } catch (GatewayException ex) {
            if (ex.isClientError()) {
                tx.executeWithoutResult(status -> subscriptions.findById(subscription.getId())
                        .ifPresent(s -> s.sync(new GatewaySubscription(null, Status.CANCELED, null, null, null))));
            }
            throw ex;
        }

        if (remote.amount() != null && remote.amount().compareTo(subscription.getAmount()) != 0) {
            log.error("Assinatura {} criada com valor divergente: {}", subscription.getId(), remote.amount());
            gateway.updateSubscriptionStatus(remote.providerId(), Status.CANCELED);
            throw BusinessException.unprocessable("subscription_amount_mismatch",
                    "O provedor registrou um valor diferente do solicitado; a assinatura foi cancelada");
        }
        return SubscriptionResponse.from(apply(subscription.getId(), remote));
    }

    public List<SubscriptionResponse> list(AuthUser user) {
        return subscriptions.findByCustomerIdOrderByCreatedAtDesc(user.id()).stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    public SubscriptionResponse get(AuthUser user, UUID id) {
        return SubscriptionResponse.from(findVisible(user, id));
    }

    public SubscriptionResponse pause(AuthUser user, UUID id) {
        return changeStatus(user, id, Status.PAUSED);
    }

    public SubscriptionResponse resume(AuthUser user, UUID id) {
        return changeStatus(user, id, Status.AUTHORIZED);
    }

    public SubscriptionResponse cancel(AuthUser user, UUID id) {
        return changeStatus(user, id, Status.CANCELED);
    }

    public Optional<SubscriptionResponse> syncByProviderId(String providerId) {
        GatewaySubscription remote = gateway.fetchSubscription(providerId);
        Optional<Subscription> local = subscriptions.findByProviderId(providerId)
                .or(() -> parseUuid(remote.externalReference()).flatMap(subscriptions::findById));
        if (local.isEmpty()) {
            log.warn("Assinatura {} do provedor sem correspondente local", providerId);
            return Optional.empty();
        }
        return Optional.of(SubscriptionResponse.from(apply(local.get().getId(), remote)));
    }

    private SubscriptionResponse changeStatus(AuthUser user, UUID id, Status target) {
        Subscription subscription = findVisible(user, id);
        if (!ALLOWED.get(subscription.getStatus()).contains(target)) {
            throw BusinessException.unprocessable("invalid_subscription_transition",
                    "Não é possível passar a assinatura de " + subscription.getStatus() + " para " + target);
        }
        if (subscription.getProviderId() == null) {
            throw BusinessException.conflict("subscription_not_confirmed", "Assinatura ainda não confirmada pelo provedor");
        }
        GatewaySubscription remote = gateway.updateSubscriptionStatus(subscription.getProviderId(), target);
        return SubscriptionResponse.from(apply(subscription.getId(), remote));
    }

    private Subscription apply(UUID id, GatewaySubscription remote) {
        return tx.execute(status -> {
            Subscription subscription = subscriptions.findById(id)
                    .orElseThrow(() -> BusinessException.notFound("Assinatura"));
            subscription.sync(remote);
            return subscription;
        });
    }

    private Subscription findVisible(AuthUser user, UUID id) {
        return subscriptions.findById(id)
                .filter(s -> user.isAdmin() || s.belongsTo(user.id()))
                .orElseThrow(() -> BusinessException.notFound("Assinatura"));
    }

    private static void validateFrequency(int frequency, FrequencyType type) {
        boolean valid = switch (type) {
            case MONTHS -> frequency >= 1 && frequency <= 12;
            case DAYS -> frequency >= 7 && frequency <= 365;
        };
        if (!valid) {
            throw BusinessException.unprocessable("invalid_frequency",
                    "Frequência deve ser de 1 a 12 meses ou de 7 a 365 dias");
        }
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record CreateSubscriptionRequest(
            @NotBlank @Size(max = 120) String reason,
            @NotNull @DecimalMin("1.00") @DecimalMax("50000.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Min(1) @Max(365) int frequency,
            @NotNull FrequencyType frequencyType,
            @NotBlank @Size(max = 64) String cardToken
    ) {
    }

    public record SubscriptionResponse(UUID id, String reason, BigDecimal amount, int frequency,
                                       FrequencyType frequencyType, Status status, String providerId,
                                       Instant nextPaymentDate, Instant createdAt) {

        static SubscriptionResponse from(Subscription s) {
            return new SubscriptionResponse(s.getId(), s.getReason(), s.getAmount(), s.getFrequency(),
                    s.getFrequencyType(), s.getStatus(), s.getProviderId(), s.getNextPaymentDate(), s.getCreatedAt());
        }
    }
}

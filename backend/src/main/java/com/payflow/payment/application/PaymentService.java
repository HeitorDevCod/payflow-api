package com.payflow.payment.application;

import com.payflow.auth.domain.User;
import com.payflow.auth.domain.UserRepository;
import com.payflow.auth.security.AuthUser;
import com.payflow.order.domain.Order;
import com.payflow.order.domain.OrderRepository;
import com.payflow.payment.api.PaymentDtos.AddressRequest;
import com.payflow.payment.api.PaymentDtos.BoletoPaymentRequest;
import com.payflow.payment.api.PaymentDtos.CardPaymentRequest;
import com.payflow.payment.api.PaymentDtos.PaymentResponse;
import com.payflow.payment.api.PaymentDtos.PixPaymentRequest;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAudit;
import com.payflow.payment.domain.PaymentAudit.Source;
import com.payflow.payment.domain.PaymentAuditRepository;
import com.payflow.payment.domain.PaymentMethod;
import com.payflow.payment.domain.PaymentRepository;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.gateway.GatewayPayment;
import com.payflow.payment.gateway.GatewayPaymentRequest;
import com.payflow.payment.gateway.PaymentGateway;
import com.payflow.shared.exception.BusinessException;
import com.payflow.shared.exception.GatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final PaymentRepository payments;
    private final PaymentAuditRepository audits;
    private final OrderRepository orders;
    private final UserRepository users;
    private final PaymentGateway gateway;
    private final PaymentSynchronizer synchronizer;
    private final PaymentProperties properties;
    private final TransactionTemplate tx;

    public PaymentService(PaymentRepository payments, PaymentAuditRepository audits, OrderRepository orders,
                          UserRepository users, PaymentGateway gateway, PaymentSynchronizer synchronizer,
                          PaymentProperties properties, PlatformTransactionManager transactionManager) {
        this.payments = payments;
        this.audits = audits;
        this.orders = orders;
        this.users = users;
        this.gateway = gateway;
        this.synchronizer = synchronizer;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public PaymentResponse payWithPix(AuthUser user, String idempotencyKey, PixPaymentRequest request) {
        return create(new Checkout(user, idempotencyKey, request.orderId(), PaymentMethod.PIX, 1, null, null));
    }

    public PaymentResponse payWithCard(AuthUser user, String idempotencyKey, CardPaymentRequest request) {
        var card = new GatewayPaymentRequest.Card(request.token(), request.paymentMethodId(),
                request.issuerId(), request.installments());
        return create(new Checkout(user, idempotencyKey, request.orderId(), PaymentMethod.CREDIT_CARD,
                request.installments(), card, null));
    }

    public PaymentResponse payWithBoleto(AuthUser user, String idempotencyKey, BoletoPaymentRequest request) {
        return create(new Checkout(user, idempotencyKey, request.orderId(), PaymentMethod.BOLETO, 1, null,
                request.address()));
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(AuthUser user, UUID paymentId) {
        return PaymentResponse.from(findVisible(user, paymentId));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listByOrder(AuthUser user, UUID orderId) {
        Order order = orders.findById(orderId)
                .filter(o -> user.isAdmin() || o.getCustomerId().equals(user.id()))
                .orElseThrow(() -> BusinessException.notFound("Pedido"));
        return payments.findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentAudit> history(UUID paymentId) {
        payments.findById(paymentId).orElseThrow(() -> BusinessException.notFound("Pagamento"));
        return audits.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }

    public PaymentResponse refresh(AuthUser user, UUID paymentId) {
        Payment payment = tx.execute(status -> findVisible(user, paymentId));
        if (payment.getProviderId() == null) {
            return PaymentResponse.from(payment);
        }
        return synchronizer.syncByProviderId(payment.getProviderId(), Source.API)
                .map(PaymentResponse::from)
                .orElseGet(() -> PaymentResponse.from(payment));
    }

    public PaymentResponse cancel(AuthUser user, UUID paymentId) {
        Payment payment = tx.execute(status -> findVisible(user, paymentId));
        if (!PaymentStatus.AWAITING_CONFIRMATION.contains(payment.getStatus())) {
            throw BusinessException.unprocessable("payment_not_cancelable",
                    "Somente pagamentos pendentes podem ser cancelados");
        }
        if (payment.getProviderId() == null) {
            throw BusinessException.conflict("payment_not_confirmed",
                    "Pagamento ainda não confirmado pelo provedor; tente novamente em instantes");
        }
        GatewayPayment remote = gateway.cancelPayment(payment.getProviderId());
        return PaymentResponse.from(synchronizer.apply(payment.getId(), remote, Source.API));
    }

    public PaymentResponse refund(UUID paymentId) {
        Payment payment = payments.findById(paymentId).orElseThrow(() -> BusinessException.notFound("Pagamento"));
        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw BusinessException.unprocessable("payment_not_refundable", "Somente pagamentos aprovados podem ser estornados");
        }
        gateway.refundPayment(payment.getProviderId(), "refund-" + payment.getId());
        return synchronizer.syncByProviderId(payment.getProviderId(), Source.ADMIN)
                .map(PaymentResponse::from)
                .orElseThrow(() -> BusinessException.notFound("Pagamento"));
    }

    private PaymentResponse create(Checkout checkout) {
        String key = requireValidKey(checkout.idempotencyKey());

        var existing = payments.findByCustomerIdAndIdempotencyKey(checkout.user().id(), key);
        if (existing.isPresent()) {
            return replay(existing.get(), checkout);
        }

        UUID paymentId;
        try {
            paymentId = tx.execute(status -> reserve(checkout, key));
        } catch (DataIntegrityViolationException ex) {
            Payment concurrent = payments.findByCustomerIdAndIdempotencyKey(checkout.user().id(), key)
                    .orElseThrow(() -> ex);
            return replay(concurrent, checkout);
        }
        return submit(paymentId, checkout);
    }

    /**
     * Mesma chave de idempotência devolve o mesmo pagamento. Se o envio anterior não chegou
     * a ser confirmado pelo provedor, reenvia com a mesma chave (o provedor deduplica).
     */
    private PaymentResponse replay(Payment payment, Checkout checkout) {
        if (!payment.getOrderId().equals(checkout.orderId()) || payment.getMethod() != checkout.method()) {
            throw BusinessException.conflict("idempotency_key_reused",
                    "Chave de idempotência já utilizada em outra requisição");
        }
        if (payment.getStatus() == PaymentStatus.PENDING && payment.getProviderId() == null) {
            return submit(payment.getId(), checkout);
        }
        return PaymentResponse.from(payment);
    }

    private UUID reserve(Checkout checkout, String key) {
        Order order = orders.findByIdForUpdate(checkout.orderId())
                .filter(o -> o.getCustomerId().equals(checkout.user().id()))
                .orElseThrow(() -> BusinessException.notFound("Pedido"));
        order.requirePayable();

        if (payments.existsByOrderIdAndStatusIn(order.getId(), PaymentStatus.ACTIVE)) {
            throw BusinessException.conflict("payment_in_progress",
                    "Já existe um pagamento em andamento para este pedido");
        }
        validateInstallments(checkout, order.getTotal());

        Instant expiresAt = switch (checkout.method()) {
            case PIX -> Instant.now().plus(properties.pixExpiration());
            case BOLETO -> Instant.now().plus(properties.boletoExpiration());
            case CREDIT_CARD -> null;
        };

        Payment payment = new Payment(order.getId(), checkout.user().id(), checkout.method(), order.getTotal(),
                checkout.installments(), key, expiresAt);
        payments.saveAndFlush(payment);
        audits.save(new PaymentAudit(payment.getId(), null, PaymentStatus.PENDING, Source.API,
                "criado via " + checkout.method()));
        return payment.getId();
    }

    private PaymentResponse submit(UUID paymentId, Checkout checkout) {
        GatewayPaymentRequest request = tx.execute(status -> buildGatewayRequest(paymentId, checkout));
        GatewayPayment remote;
        try {
            remote = gateway.createPayment(request);
        } catch (GatewayException ex) {
            if (ex.isClientError()) {
                synchronizer.markFailed(paymentId, ex.getMessage(), Source.API);
            } else {
                log.warn("Envio do pagamento {} sem confirmação do provedor; a reconciliação vai verificar", paymentId);
            }
            throw ex;
        }
        return PaymentResponse.from(synchronizer.apply(paymentId, remote, Source.API));
    }

    private GatewayPaymentRequest buildGatewayRequest(UUID paymentId, Checkout checkout) {
        Payment payment = payments.findById(paymentId).orElseThrow(() -> BusinessException.notFound("Pagamento"));
        User customer = users.findById(payment.getCustomerId())
                .orElseThrow(() -> BusinessException.notFound("Usuário"));

        GatewayPaymentRequest.Address address = null;
        if (checkout.address() != null) {
            AddressRequest a = checkout.address();
            address = new GatewayPaymentRequest.Address(a.normalizedZipCode(), a.street(), a.number(),
                    a.neighborhood(), a.city(), a.state());
        }
        var payer = new GatewayPaymentRequest.Payer(customer.getEmail(), customer.getFirstName(),
                customer.getLastName(), customer.getDocument(), address);

        return new GatewayPaymentRequest(
                payment.getIdempotencyKey(),
                payment.getId().toString(),
                payment.getAmount(),
                "Pedido " + payment.getOrderId(),
                payment.getMethod(),
                payer,
                payment.getExpiresAt(),
                checkout.card());
    }

    private void validateInstallments(Checkout checkout, BigDecimal total) {
        int installments = checkout.installments();
        if (checkout.method() != PaymentMethod.CREDIT_CARD) {
            if (installments != 1) {
                throw BusinessException.unprocessable("invalid_installments", "Parcelamento disponível apenas no cartão");
            }
            return;
        }
        if (installments < 1 || installments > properties.maxInstallments()) {
            throw BusinessException.unprocessable("invalid_installments",
                    "Número de parcelas deve estar entre 1 e " + properties.maxInstallments());
        }
        BigDecimal perInstallment = total.divide(BigDecimal.valueOf(installments), 2, RoundingMode.DOWN);
        if (perInstallment.compareTo(properties.minInstallmentAmount()) < 0) {
            throw BusinessException.unprocessable("installment_too_small",
                    "Valor mínimo por parcela é " + properties.minInstallmentAmount());
        }
    }

    private Payment findVisible(AuthUser user, UUID paymentId) {
        return payments.findById(paymentId)
                .filter(p -> user.isAdmin() || p.belongsTo(user.id()))
                .orElseThrow(() -> BusinessException.notFound("Pagamento"));
    }

    private static String requireValidKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw BusinessException.unprocessable("invalid_idempotency_key",
                    "Idempotency-Key deve ter de 8 a 64 caracteres alfanuméricos, '-' ou '_'");
        }
        return key;
    }

    private record Checkout(AuthUser user, String idempotencyKey, UUID orderId, PaymentMethod method,
                            int installments, GatewayPaymentRequest.Card card, AddressRequest address) {
    }
}

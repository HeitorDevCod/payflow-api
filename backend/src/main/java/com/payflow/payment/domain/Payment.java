package com.payflow.payment.domain;

import com.payflow.shared.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "status_detail", length = 80)
    private String statusDetail;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private int installments;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "provider_id", length = 40)
    private String providerId;

    @Column(name = "pix_qr_code", columnDefinition = "TEXT")
    private String pixQrCode;

    @Column(name = "pix_qr_code_base64", columnDefinition = "TEXT")
    private String pixQrCodeBase64;

    @Column(name = "ticket_url", length = 500)
    private String ticketUrl;

    @Column(length = 80)
    private String barcode;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Payment() {
    }

    public Payment(UUID orderId, UUID customerId, PaymentMethod method, BigDecimal amount,
                   int installments, String idempotencyKey, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.customerId = customerId;
        this.method = method;
        this.status = PaymentStatus.PENDING;
        this.amount = amount;
        this.installments = installments;
        this.idempotencyKey = idempotencyKey;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Aplica uma mudança de status respeitando a máquina de estados.
     *
     * @return {@code true} se o status mudou
     */
    public boolean changeStatus(PaymentStatus target, String detail) {
        if (!status.canTransitionTo(target)) {
            throw BusinessException.conflict("invalid_payment_transition",
                    "Transição de pagamento inválida: " + status + " -> " + target);
        }
        boolean changed = status != target;
        status = target;
        if (detail != null) {
            statusDetail = detail;
        }
        updatedAt = Instant.now();
        return changed;
    }

    public void linkProvider(String providerId) {
        if (this.providerId != null && !this.providerId.equals(providerId)) {
            throw BusinessException.conflict("provider_mismatch", "Pagamento já vinculado a outra transação");
        }
        this.providerId = providerId;
        updatedAt = Instant.now();
    }

    public void attachPixData(String qrCode, String qrCodeBase64, String ticketUrl) {
        this.pixQrCode = qrCode;
        this.pixQrCodeBase64 = qrCodeBase64;
        this.ticketUrl = ticketUrl;
    }

    public void attachBoletoData(String ticketUrl, String barcode) {
        this.ticketUrl = ticketUrl;
        this.barcode = barcode;
    }

    public void updateExpiration(Instant expiresAt) {
        if (expiresAt != null) {
            this.expiresAt = expiresAt;
        }
    }

    public boolean belongsTo(UUID customerId) {
        return this.customerId.equals(customerId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getInstallments() {
        return installments;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getPixQrCode() {
        return pixQrCode;
    }

    public String getPixQrCodeBase64() {
        return pixQrCodeBase64;
    }

    public String getTicketUrl() {
        return ticketUrl;
    }

    public String getBarcode() {
        return barcode;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

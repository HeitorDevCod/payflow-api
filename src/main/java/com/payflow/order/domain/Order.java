package com.payflow.order.domain;

import com.payflow.shared.exception.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity(name = "CustomerOrder")
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Order() {
    }

    public Order(UUID customerId) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.status = OrderStatus.AWAITING_PAYMENT;
        this.total = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void addItem(UUID productId, String name, BigDecimal unitPrice, int quantity) {
        items.add(new OrderItem(this, productId, name, unitPrice, quantity));
        total = items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);
        touch();
    }

    public void requirePayable() {
        if (status != OrderStatus.AWAITING_PAYMENT) {
            throw BusinessException.unprocessable("order_not_payable",
                    "Pedido não está aguardando pagamento (status atual: " + status + ")");
        }
    }

    public void markPaid() {
        if (status == OrderStatus.PAID) {
            return;
        }
        if (status != OrderStatus.AWAITING_PAYMENT && status != OrderStatus.PAYMENT_REVIEW) {
            throw BusinessException.conflict("invalid_order_transition", "Pedido não pode ser marcado como pago");
        }
        status = OrderStatus.PAID;
        touch();
    }

    public void markForReview() {
        if (status == OrderStatus.AWAITING_PAYMENT) {
            status = OrderStatus.PAYMENT_REVIEW;
            touch();
        }
    }

    public void markRefunded() {
        if (status == OrderStatus.PAID) {
            status = OrderStatus.REFUNDED;
            touch();
        }
    }

    public void reopenForPayment() {
        if (status == OrderStatus.PAYMENT_REVIEW) {
            status = OrderStatus.AWAITING_PAYMENT;
            touch();
        }
    }

    public void cancel() {
        if (status != OrderStatus.AWAITING_PAYMENT) {
            throw BusinessException.unprocessable("order_not_cancelable", "Somente pedidos aguardando pagamento podem ser cancelados");
        }
        status = OrderStatus.CANCELED;
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

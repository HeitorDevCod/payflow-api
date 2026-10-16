package com.payflow.order.api;

import com.payflow.order.domain.Order;
import com.payflow.order.domain.OrderItem;
import com.payflow.order.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CreateOrderRequest(
            @NotEmpty @Size(max = 50) List<@Valid ItemRequest> items
    ) {
    }

    public record ItemRequest(
            @NotNull UUID productId,
            @Min(1) @Max(100) int quantity
    ) {
    }

    public record OrderResponse(UUID id, OrderStatus status, BigDecimal total, List<ItemResponse> items,
                                Instant createdAt, Instant updatedAt) {

        public static OrderResponse from(Order order) {
            return new OrderResponse(order.getId(), order.getStatus(), order.getTotal(),
                    order.getItems().stream().map(ItemResponse::from).toList(),
                    order.getCreatedAt(), order.getUpdatedAt());
        }
    }

    public record ItemResponse(UUID productId, String name, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {

        static ItemResponse from(OrderItem item) {
            return new ItemResponse(item.getProductId(), item.getName(), item.getUnitPrice(),
                    item.getQuantity(), item.subtotal());
        }
    }
}

package com.payflow.order.application;

import com.payflow.auth.security.AuthUser;
import com.payflow.catalog.Product;
import com.payflow.catalog.ProductRepository;
import com.payflow.order.api.OrderDtos.CreateOrderRequest;
import com.payflow.order.api.OrderDtos.ItemRequest;
import com.payflow.order.api.OrderDtos.OrderResponse;
import com.payflow.order.domain.Order;
import com.payflow.order.domain.OrderRepository;
import com.payflow.payment.domain.PaymentRepository;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.shared.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final ProductRepository products;
    private final PaymentRepository payments;

    public OrderService(OrderRepository orders, ProductRepository products, PaymentRepository payments) {
        this.orders = orders;
        this.products = products;
        this.payments = payments;
    }

    @Transactional
    public OrderResponse create(AuthUser user, CreateOrderRequest request) {
        Set<UUID> seen = new HashSet<>();
        for (ItemRequest item : request.items()) {
            if (!seen.add(item.productId())) {
                throw BusinessException.unprocessable("duplicated_item", "Produto repetido no pedido: " + item.productId());
            }
        }

        Map<UUID, Product> catalog = products.findAllById(seen).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Order order = new Order(user.id());
        for (ItemRequest item : request.items()) {
            Product product = catalog.get(item.productId());
            if (product == null || !product.isActive()) {
                throw BusinessException.unprocessable("product_unavailable", "Produto indisponível: " + item.productId());
            }
            order.addItem(product.getId(), product.getName(), product.getPrice(), item.quantity());
        }
        return OrderResponse.from(orders.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(AuthUser user, UUID orderId) {
        return OrderResponse.from(findOwned(user, orderId));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(AuthUser user, Pageable pageable) {
        return orders.findByCustomerIdOrderByCreatedAtDesc(user.id(), pageable).map(OrderResponse::from);
    }

    @Transactional
    public OrderResponse cancel(AuthUser user, UUID orderId) {
        Order order = findOwned(user, orderId);
        if (payments.existsByOrderIdAndStatusIn(order.getId(), PaymentStatus.ACTIVE)) {
            throw BusinessException.conflict("payment_in_progress",
                    "Cancele o pagamento pendente antes de cancelar o pedido");
        }
        order.cancel();
        return OrderResponse.from(order);
    }

    private Order findOwned(AuthUser user, UUID orderId) {
        return orders.findById(orderId)
                .filter(order -> user.isAdmin() || order.getCustomerId().equals(user.id()))
                .orElseThrow(() -> BusinessException.notFound("Pedido"));
    }
}

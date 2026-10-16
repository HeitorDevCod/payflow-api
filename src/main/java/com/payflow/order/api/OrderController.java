package com.payflow.order.api;

import com.payflow.auth.security.AuthUser;
import com.payflow.order.api.OrderDtos.CreateOrderRequest;
import com.payflow.order.api.OrderDtos.OrderResponse;
import com.payflow.order.application.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Pedidos")
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(user, request);
    }

    @GetMapping
    public Page<OrderResponse> list(@AuthenticationPrincipal AuthUser user, @PageableDefault(size = 20) Pageable pageable) {
        return orderService.list(user, pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return orderService.get(user, id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return orderService.cancel(user, id);
    }
}

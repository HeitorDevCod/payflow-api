package com.payflow.subscription;

import com.payflow.auth.security.AuthUser;
import com.payflow.subscription.SubscriptionService.CreateSubscriptionRequest;
import com.payflow.subscription.SubscriptionService.SubscriptionResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Assinaturas")
@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse create(@AuthenticationPrincipal AuthUser user,
                                       @Valid @RequestBody CreateSubscriptionRequest request) {
        return service.create(user, request);
    }

    @GetMapping
    public List<SubscriptionResponse> list(@AuthenticationPrincipal AuthUser user) {
        return service.list(user);
    }

    @GetMapping("/{id}")
    public SubscriptionResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return service.get(user, id);
    }

    @PostMapping("/{id}/pause")
    public SubscriptionResponse pause(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return service.pause(user, id);
    }

    @PostMapping("/{id}/resume")
    public SubscriptionResponse resume(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return service.resume(user, id);
    }

    @PostMapping("/{id}/cancel")
    public SubscriptionResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return service.cancel(user, id);
    }
}

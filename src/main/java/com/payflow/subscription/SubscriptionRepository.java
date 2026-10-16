package com.payflow.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByProviderId(String providerId);

    List<Subscription> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}

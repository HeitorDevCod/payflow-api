package com.payflow.payment.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    Optional<Payment> findByProviderId(String providerId);

    boolean existsByOrderIdAndStatusIn(UUID orderId, Collection<PaymentStatus> statuses);

    List<Payment> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<Payment> findByStatusInAndUpdatedAtBefore(Collection<PaymentStatus> statuses, Instant before, Pageable pageable);
}

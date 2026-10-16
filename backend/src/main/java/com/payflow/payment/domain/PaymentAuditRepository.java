package com.payflow.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentAuditRepository extends JpaRepository<PaymentAudit, UUID> {

    List<PaymentAudit> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);
}

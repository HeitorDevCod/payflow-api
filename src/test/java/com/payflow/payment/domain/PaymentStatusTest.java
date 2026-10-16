package com.payflow.payment.domain;

import com.payflow.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStatusTest {

    @Test
    void pendingCanMoveAnywhere() {
        for (PaymentStatus target : PaymentStatus.values()) {
            assertThat(PaymentStatus.PENDING.canTransitionTo(target)).isTrue();
        }
    }

    @Test
    void approvedOnlyMovesToRefundOrChargeback() {
        assertThat(PaymentStatus.APPROVED.canTransitionTo(PaymentStatus.REFUNDED)).isTrue();
        assertThat(PaymentStatus.APPROVED.canTransitionTo(PaymentStatus.CHARGED_BACK)).isTrue();
        assertThat(PaymentStatus.APPROVED.canTransitionTo(PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStatus.APPROVED.canTransitionTo(PaymentStatus.REJECTED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"REJECTED", "CANCELED", "REFUNDED", "CHARGED_BACK", "FAILED"})
    void finalStatusesAreTerminal(PaymentStatus status) {
        assertThat(status.isFinal()).isTrue();
        assertThat(status.canTransitionTo(PaymentStatus.APPROVED)).isFalse();
        assertThat(status.canTransitionTo(status)).isTrue();
    }

    @Test
    void paymentRejectsInvalidTransition() {
        Payment payment = new Payment(UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.PIX,
                new BigDecimal("10.00"), 1, "key-12345678", null);
        payment.changeStatus(PaymentStatus.REJECTED, "cc_rejected_other_reason");

        assertThatThrownBy(() -> payment.changeStatus(PaymentStatus.APPROVED, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("REJECTED -> APPROVED");
    }

    @Test
    void changeStatusReportsWhetherSomethingChanged() {
        Payment payment = new Payment(UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.PIX,
                new BigDecimal("10.00"), 1, "key-12345678", null);

        assertThat(payment.changeStatus(PaymentStatus.PENDING, null)).isFalse();
        assertThat(payment.changeStatus(PaymentStatus.APPROVED, "accredited")).isTrue();
        assertThat(payment.getStatusDetail()).isEqualTo("accredited");
    }
}

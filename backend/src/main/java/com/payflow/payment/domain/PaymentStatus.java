package com.payflow.payment.domain;

import java.util.EnumSet;
import java.util.Set;

public enum PaymentStatus {
    PENDING,
    IN_PROCESS,
    APPROVED,
    REJECTED,
    CANCELED,
    REFUNDED,
    CHARGED_BACK,
    FAILED;

    public static final Set<PaymentStatus> ACTIVE = EnumSet.of(PENDING, IN_PROCESS, APPROVED);
    public static final Set<PaymentStatus> AWAITING_CONFIRMATION = EnumSet.of(PENDING, IN_PROCESS);

    public boolean isFinal() {
        return this == REJECTED || this == CANCELED || this == REFUNDED || this == CHARGED_BACK || this == FAILED;
    }

    public boolean canTransitionTo(PaymentStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> true;
            case IN_PROCESS -> target != FAILED;
            case APPROVED -> target == REFUNDED || target == CHARGED_BACK;
            case REJECTED, CANCELED, REFUNDED, CHARGED_BACK, FAILED -> false;
        };
    }
}

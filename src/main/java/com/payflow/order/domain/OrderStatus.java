package com.payflow.order.domain;

public enum OrderStatus {
    AWAITING_PAYMENT,
    PAYMENT_REVIEW,
    PAID,
    REFUNDED,
    CANCELED
}

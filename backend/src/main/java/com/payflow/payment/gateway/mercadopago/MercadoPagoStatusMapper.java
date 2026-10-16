package com.payflow.payment.gateway.mercadopago;

import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.gateway.GatewaySubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

final class MercadoPagoStatusMapper {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoStatusMapper.class);

    private MercadoPagoStatusMapper() {
    }

    static PaymentStatus payment(String status) {
        if (status == null) {
            return PaymentStatus.PENDING;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "approved" -> PaymentStatus.APPROVED;
            case "pending" -> PaymentStatus.PENDING;
            case "authorized", "in_process", "in_mediation" -> PaymentStatus.IN_PROCESS;
            case "rejected" -> PaymentStatus.REJECTED;
            case "cancelled" -> PaymentStatus.CANCELED;
            case "refunded" -> PaymentStatus.REFUNDED;
            case "charged_back" -> PaymentStatus.CHARGED_BACK;
            default -> {
                log.warn("Status de pagamento desconhecido no Mercado Pago: {}", status);
                yield PaymentStatus.IN_PROCESS;
            }
        };
    }

    static GatewaySubscription.Status subscription(String status) {
        if (status == null) {
            return GatewaySubscription.Status.PENDING;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "authorized" -> GatewaySubscription.Status.AUTHORIZED;
            case "paused" -> GatewaySubscription.Status.PAUSED;
            case "cancelled" -> GatewaySubscription.Status.CANCELED;
            default -> GatewaySubscription.Status.PENDING;
        };
    }

    static String subscription(GatewaySubscription.Status status) {
        return switch (status) {
            case AUTHORIZED -> "authorized";
            case PAUSED -> "paused";
            case CANCELED -> "cancelled";
            case PENDING -> "pending";
        };
    }
}

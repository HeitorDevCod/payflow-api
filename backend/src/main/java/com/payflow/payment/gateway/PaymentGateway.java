package com.payflow.payment.gateway;

import java.util.Optional;

/**
 * Porta de saída para o provedor de pagamentos. A implementação padrão é o Mercado Pago,
 * mas o domínio não depende de nenhum detalhe dele.
 */
public interface PaymentGateway {

    GatewayPayment createPayment(GatewayPaymentRequest request);

    GatewayPayment fetchPayment(String providerId);

    Optional<GatewayPayment> findByExternalReference(String externalReference);

    GatewayPayment cancelPayment(String providerId);

    void refundPayment(String providerId, String idempotencyKey);

    GatewaySubscription createSubscription(GatewaySubscriptionRequest request);

    GatewaySubscription fetchSubscription(String providerId);

    GatewaySubscription updateSubscriptionStatus(String providerId, GatewaySubscription.Status status);
}

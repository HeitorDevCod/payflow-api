package com.payflow.config;

import com.payflow.payment.gateway.mercadopago.MercadoPagoProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Configuração")
@RestController
public class PublicConfigController {

    private final MercadoPagoProperties mercadoPago;

    public PublicConfigController(MercadoPagoProperties mercadoPago) {
        this.mercadoPago = mercadoPago;
    }

    @Operation(summary = "Configuração pública usada pelo front-end (chave pública do Mercado Pago)")
    @GetMapping("/config")
    public PublicConfig config() {
        String key = mercadoPago.publicKey();
        return new PublicConfig(key == null || key.isBlank() ? null : key, false);
    }

    public record PublicConfig(String mercadoPagoPublicKey, boolean demo) {
    }
}

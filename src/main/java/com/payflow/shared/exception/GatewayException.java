package com.payflow.shared.exception;

public class GatewayException extends RuntimeException {

    private final int providerStatus;

    public GatewayException(String message, int providerStatus, Throwable cause) {
        super(message, cause);
        this.providerStatus = providerStatus;
    }

    public GatewayException(String message, int providerStatus) {
        this(message, providerStatus, null);
    }

    public int getProviderStatus() {
        return providerStatus;
    }

    public boolean isClientError() {
        return providerStatus >= 400 && providerStatus < 500;
    }
}

package com.javadsamastery.service.exception;

public class AIProviderUnavailableException extends RuntimeException {
    private final boolean isTimeout;

    public AIProviderUnavailableException(String message, boolean isTimeout) {
        super(message);
        this.isTimeout = isTimeout;
    }

    public boolean isTimeout() {
        return isTimeout;
    }
}

package com.javadsamastery.dto;

public record ErrorResponse(
    String error,
    String message,
    boolean retryable,
    long timestamp
) {
    public ErrorResponse(String error, String message, boolean retryable) {
        this(error, message, retryable, System.currentTimeMillis());
    }
}

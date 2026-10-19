package com.javadsamastery.service.exception;

public class StaleSessionVersionException extends RuntimeException {
    public StaleSessionVersionException(String message) {
        super(message);
    }
}

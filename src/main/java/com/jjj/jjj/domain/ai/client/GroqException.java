package com.jjj.jjj.domain.ai.client;

public class GroqException extends RuntimeException {

    public GroqException(String message) {
        super(message);
    }

    public GroqException(String message, Throwable cause) {
        super(message, cause);
    }
}

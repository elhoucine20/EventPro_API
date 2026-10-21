package com.eventPro.eventPro.exception;

public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String email) {
        super("email deja utiliser "+email);
    }
}

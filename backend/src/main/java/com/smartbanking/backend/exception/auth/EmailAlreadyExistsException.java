package com.smartbanking.backend.exception.auth;

public class EmailAlreadyExistsException extends RuntimeException {
    private final String email;

    public EmailAlreadyExistsException(String email) {
        super("Email " + email + " already exists.");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}

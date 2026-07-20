package com.ledgersaas.backend.exception;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String email) {
        super("Bu e-posta adresi zaten kayıtlı: " + email);
    }
}

package com.ledgersaas.backend.exception;

public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException(Long subscriptionId) {
        super("Abonelik bulunamadı. id=" + subscriptionId);
    }

    public SubscriptionNotFoundException(String message) {
        super(message);
    }
}

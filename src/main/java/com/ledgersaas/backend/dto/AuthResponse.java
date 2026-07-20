package com.ledgersaas.backend.dto;

public record AuthResponse(
        String token,
        String tokenType,
        String email,
        String subscriptionAuthority) {

    public static AuthResponse bearer(String token, String email, String subscriptionAuthority) {
        return new AuthResponse(token, "Bearer", email, subscriptionAuthority);
    }
}

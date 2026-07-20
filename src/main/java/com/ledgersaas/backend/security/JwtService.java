package com.ledgersaas.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JwtService {

    public static final String CLAIM_AUTHORITIES = "authorities";

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${JWT_SECRET_KEY}") String secretKey,
            @Value("${JWT_EXPIRATION_MS:3600000}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Kullanıcının e-postası ve abonelik statüsünden türetilen yetki
     * (örn. SUBSCRIBER_ACTIVE) ile imzalı bir JWT üretir.
     */
    public String generateToken(String email, String subscriptionAuthority) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_AUTHORITIES, List.of(subscriptionAuthority))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Geçersiz JWT: {}", e.getMessage());
            return false;
        }
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractAuthorities(String token) {
        List<String> authorities = parseClaims(token).get(CLAIM_AUTHORITIES, List.class);
        return authorities != null ? authorities : List.of();
    }

    public Claims extractAllClaims(String token) {
        return parseClaims(token);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

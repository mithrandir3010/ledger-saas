package com.ledgersaas.backend.service;

import com.ledgersaas.backend.dto.AuthResponse;
import com.ledgersaas.backend.dto.LoginRequest;
import com.ledgersaas.backend.dto.RegisterRequest;
import com.ledgersaas.backend.exception.UserAlreadyExistsException;
import com.ledgersaas.backend.model.entity.User;
import com.ledgersaas.backend.model.enums.SubscriptionStatus;
import com.ledgersaas.backend.repository.SubscriptionRepository;
import com.ledgersaas.backend.repository.UserRepository;
import com.ledgersaas.backend.security.JwtService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    public static final String AUTHORITY_SUBSCRIBER_ACTIVE = "SUBSCRIBER_ACTIVE";
    public static final String AUTHORITY_SUBSCRIBER_PAST_DUE = "SUBSCRIBER_PAST_DUE";
    public static final String AUTHORITY_SUBSCRIBER_FREE = "SUBSCRIBER_FREE";

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .build();
        userRepository.save(user);

        log.info("Yeni kullanıcı kaydedildi: {}", user.getEmail());

        String authority = AUTHORITY_SUBSCRIBER_FREE;
        String token = jwtService.generateToken(user.getEmail(), authority);
        return AuthResponse.bearer(token, user.getEmail(), authority);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-posta veya şifre hatalı"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-posta veya şifre hatalı");
        }

        String authority = resolveSubscriptionAuthority(user);
        String token = jwtService.generateToken(user.getEmail(), authority);
        return AuthResponse.bearer(token, user.getEmail(), authority);
    }

    /**
     * Kullanıcının güncel abonelik statüsünü token'a gömülecek yetkiye çevirir.
     * ACTIVE/TRIALING -> SUBSCRIBER_ACTIVE, PAST_DUE -> SUBSCRIBER_PAST_DUE,
     * abonelik yoksa -> SUBSCRIBER_FREE.
     */
    private String resolveSubscriptionAuthority(User user) {
        return subscriptionRepository
                .findFirstByUserIdAndStatusIn(
                        user.getId(),
                        List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING, SubscriptionStatus.PAST_DUE))
                .map(subscription -> switch (subscription.getStatus()) {
                    case ACTIVE, TRIALING -> AUTHORITY_SUBSCRIBER_ACTIVE;
                    case PAST_DUE -> AUTHORITY_SUBSCRIBER_PAST_DUE;
                    default -> AUTHORITY_SUBSCRIBER_FREE;
                })
                .orElse(AUTHORITY_SUBSCRIBER_FREE);
    }
}

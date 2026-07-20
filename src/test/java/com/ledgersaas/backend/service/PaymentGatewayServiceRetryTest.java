package com.ledgersaas.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgersaas.backend.exception.TransientPaymentException;
import com.ledgersaas.backend.model.entity.Subscription;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @Retryable / @Recover davranışı Spring AOP proxy'si üzerinden çalıştığı
 * için bu test gerçek bir (minimal) Spring context ile koşar; saf Mockito
 * birim testi retry mekanizmasını tetikleyemezdi.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PaymentGatewayServiceRetryTest.RetryTestConfig.class)
@TestPropertySource(properties = "payment.retry.delay-ms=50")
class PaymentGatewayServiceRetryTest {

    private static final AtomicInteger ATTEMPTS = new AtomicInteger();
    private static volatile int failuresBeforeSuccess;

    /**
     * Rastgele simülasyon yerine senaryo bazlı davranan gateway:
     * ilk N çağrıda TransientPaymentException fırlatır, sonrasında başarılı olur.
     */
    static class ScriptedPaymentGatewayService extends PaymentGatewayService {
        @Override
        protected boolean callExternalGateway(Subscription subscription) {
            int attempt = ATTEMPTS.incrementAndGet();
            if (attempt <= failuresBeforeSuccess) {
                throw new TransientPaymentException("Simüle edilmiş ağ hatası, deneme #" + attempt);
            }
            return true;
        }
    }

    @Configuration
    @EnableRetry
    static class RetryTestConfig {
        @Bean
        PaymentGatewayService paymentGatewayService() {
            return new ScriptedPaymentGatewayService();
        }
    }

    @Autowired
    private PaymentGatewayService paymentGatewayService;

    private final Subscription subscription = Subscription.builder().id(42L).build();

    @BeforeEach
    void resetScript() {
        ATTEMPTS.set(0);
    }

    @Test
    @DisplayName("İlk deneme ağ hatası, ikinci deneme başarılı: retry devreye girip true dönmeli")
    void chargeSubscription_whenFirstAttemptFails_shouldRetryAndSucceed() {
        failuresBeforeSuccess = 1;

        boolean result = paymentGatewayService.chargeSubscription(subscription);

        assertThat(result).isTrue();
        assertThat(ATTEMPTS.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("3 deneme de ağ hatası: @Recover devreye girip false dönmeli (PAST_DUE akışına devir)")
    void chargeSubscription_whenAllAttemptsFail_shouldRecoverWithFalse() {
        failuresBeforeSuccess = Integer.MAX_VALUE;

        boolean result = paymentGatewayService.chargeSubscription(subscription);

        assertThat(result).isFalse();
        assertThat(ATTEMPTS.get()).isEqualTo(3);
    }
}

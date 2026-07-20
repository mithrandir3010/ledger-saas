package com.ledgersaas.backend.service;

import com.ledgersaas.backend.exception.TransientPaymentException;
import com.ledgersaas.backend.model.entity.Subscription;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Harici ödeme geçidi entegrasyonunun simülasyonu.
 *
 * Retry mekanizmasının çalışabilmesi için bu mantık SubscriptionScheduler
 * içinden ayrı bir bean'e çıkarılmıştır: Spring AOP proxy'si self-invocation
 * (aynı bean içi çağrı) durumunda devreye girmez, @Retryable ancak dışarıdan
 * yapılan bean çağrılarında çalışır.
 */
@Service
@Slf4j
public class PaymentGatewayService {

    /**
     * Aboneliği tahsil etmeyi dener. Geçici ağ/timeout hatalarında
     * (TransientPaymentException) 2'şer saniye arayla en fazla 3 kez denenir.
     *
     * @return true: tahsilat başarılı, false: ödeme kalıcı olarak reddedildi
     */
    @Retryable(
            retryFor = TransientPaymentException.class,
            maxAttempts = 3,
            backoff = @Backoff(delayExpression = "${payment.retry.delay-ms:2000}"))
    public boolean chargeSubscription(Subscription subscription) {
        log.debug("Ödeme geçidi tahsilat denemesi. subscriptionId={}", subscription.getId());
        return callExternalGateway(subscription);
    }

    /**
     * 3 deneme de geçici hata ile sonuçlanırsa devreye girer; süreç
     * güvenli şekilde PAST_DUE (dunning) akışına devredilir.
     */
    @Recover
    public boolean recoverFromTransientFailure(TransientPaymentException ex, Subscription subscription) {
        log.error("Ödeme, art arda 3 denemenin ardından da tamamlanamadı; süreç PAST_DUE akışına devrediliyor. "
                + "subscriptionId={}, sonHata={}", subscription.getId(), ex.getMessage());
        return false;
    }

    /**
     * Harici ödeme geçidi çağrısının simülasyonu: %20 geçici ağ hatası,
     * kalan durumların ~%90'ı başarılı tahsilat.
     * Testlerde deterministik senaryolar için override edilebilir.
     */
    protected boolean callExternalGateway(Subscription subscription) {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 20) {
            log.warn("Ödeme geçidine ulaşılamadı (geçici ağ hatası simülasyonu). subscriptionId={}",
                    subscription.getId());
            throw new TransientPaymentException("Ödeme geçidi zaman aşımı (simülasyon)");
        }
        return roll < 92;
    }
}

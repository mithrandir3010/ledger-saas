package com.ledgersaas.backend.exception;

/**
 * Ödeme geçidine erişimde yaşanan geçici (ağ / timeout) hataları temsil eder.
 * Spring Retry bu hata tipinde otomatik yeniden dener; kalıcı red (kart
 * limiti vb.) durumları exception değil, false dönüşü ile modellenir.
 */
public class TransientPaymentException extends RuntimeException {

    public TransientPaymentException(String message) {
        super(message);
    }
}

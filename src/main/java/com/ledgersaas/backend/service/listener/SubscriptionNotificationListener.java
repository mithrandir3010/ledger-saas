package com.ledgersaas.backend.service.listener;

import com.ledgersaas.backend.config.MdcLogFilter;
import com.ledgersaas.backend.model.event.PaymentFailureEvent;
import com.ledgersaas.backend.model.event.PaymentSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SubscriptionNotificationListener {

    @Async("notificationExecutor")
    @EventListener
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        log.info("[thread: {}] [MDC reqId: {}] Kullanıcıya {} tutarındaki PAID faturası e-posta ile gönderildi. "
                        + "subscriptionId={}, userEmail={}",
                Thread.currentThread().getName(),
                MDC.get(MdcLogFilter.MDC_REQUEST_ID_KEY),
                event.invoice().getAmount(),
                event.subscription().getId(),
                event.subscription().getUser().getEmail());
    }

    @Async("notificationExecutor")
    @EventListener
    public void handlePaymentFailure(PaymentFailureEvent event) {
        log.warn("[thread: {}] [MDC reqId: {}] Kullanıcıya ödeme başarısızlık uyarısı ve aksiyon maili gönderildi. "
                        + "subscriptionId={}, userEmail={}, neden={}",
                Thread.currentThread().getName(),
                MDC.get(MdcLogFilter.MDC_REQUEST_ID_KEY),
                event.subscription().getId(),
                event.subscription().getUser().getEmail(),
                event.failureReason());
    }
}

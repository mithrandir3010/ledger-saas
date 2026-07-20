package com.ledgersaas.backend.scheduler;

import com.ledgersaas.backend.model.entity.Invoice;
import com.ledgersaas.backend.model.entity.Subscription;
import com.ledgersaas.backend.model.enums.BillingInterval;
import com.ledgersaas.backend.model.enums.InvoiceStatus;
import com.ledgersaas.backend.model.enums.SubscriptionStatus;
import com.ledgersaas.backend.model.event.PaymentFailureEvent;
import com.ledgersaas.backend.model.event.PaymentSuccessEvent;
import com.ledgersaas.backend.repository.InvoiceRepository;
import com.ledgersaas.backend.repository.SubscriptionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {

    private static final String PAYMENT_FAILURE_REASON = "Ödeme sağlayıcı işlemi reddetti (simülasyon)";

    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "0 0 0 * * ?")
    @SchedulerLock(
            name = "SubscriptionScheduler_checkExpiredSubscriptions",
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT1M")
    @Transactional
    public void checkExpiredSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> expiredSubscriptions =
                subscriptionRepository.findAllByStatusAndCurrentPeriodEndBefore(SubscriptionStatus.ACTIVE, now);

        if (expiredSubscriptions.isEmpty()) {
            log.info("Süresi dolan abonelik bulunamadı. Kontrol zamanı: {}", now);
            return;
        }

        log.info("Süresi dolan {} abonelik bulundu, işleniyor...", expiredSubscriptions.size());

        List<Invoice> newInvoices = new ArrayList<>();
        List<Object> pendingEvents = new ArrayList<>();

        for (Subscription subscription : expiredSubscriptions) {
            if (Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())) {
                subscription.setStatus(SubscriptionStatus.EXPIRED);
                log.info("Abonelik dönem sonunda iptal edildi ve EXPIRED durumuna alındı. id={}", subscription.getId());
                continue;
            }

            if (simulatePayment(subscription)) {
                subscription.setCurrentPeriodStart(now);
                subscription.setCurrentPeriodEnd(calculateNextPeriodEnd(now, subscription));
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                Invoice invoice = buildInvoice(subscription, InvoiceStatus.PAID, now);
                newInvoices.add(invoice);
                pendingEvents.add(new PaymentSuccessEvent(subscription, invoice));
                // user.getEmail() erisimi LAZY proxy'yi transaction icindeyken
                // initialize eder; asenkron listener bu sayede guvenle okuyabilir.
                log.info("Abonelik yenilendi, PAID faturası kesildi. id={}, userEmail={}, yeni dönem sonu={}",
                        subscription.getId(), subscription.getUser().getEmail(), subscription.getCurrentPeriodEnd());
            } else {
                subscription.setStatus(SubscriptionStatus.PAST_DUE);
                Invoice invoice = buildInvoice(subscription, InvoiceStatus.FAILED, now);
                newInvoices.add(invoice);
                pendingEvents.add(new PaymentFailureEvent(subscription, invoice, PAYMENT_FAILURE_REASON));
                log.warn("Ödeme başarısız; abonelik PAST_DUE durumuna alındı, FAILED faturası oluşturuldu. "
                        + "subscriptionId={}, userEmail={}", subscription.getId(), subscription.getUser().getEmail());
            }
        }

        subscriptionRepository.saveAll(expiredSubscriptions);
        invoiceRepository.saveAll(newInvoices);
        pendingEvents.forEach(eventPublisher::publishEvent);

        log.info("Abonelik kontrolü tamamlandı. Güncellenen abonelik: {}, kesilen fatura: {}, yayınlanan event: {}",
                expiredSubscriptions.size(), newInvoices.size(), pendingEvents.size());
    }

    /**
     * Harici ödeme geçidini simüle eder; %90 ihtimalle başarılı döner.
     * Birim testlerde deterministik stub'lanabilmesi için package-private.
     */
    boolean simulatePayment(Subscription subscription) {
        return ThreadLocalRandom.current().nextInt(100) < 90;
    }

    private LocalDateTime calculateNextPeriodEnd(LocalDateTime start, Subscription subscription) {
        BillingInterval interval = subscription.getPlan().getBillingInterval();
        return interval == BillingInterval.YEARLY ? start.plusYears(1) : start.plusMonths(1);
    }

    private Invoice buildInvoice(Subscription subscription, InvoiceStatus status, LocalDateTime billingDate) {
        return Invoice.builder()
                .subscription(subscription)
                .user(subscription.getUser())
                .amount(subscription.getPlan().getPrice())
                .status(status)
                .billingDate(billingDate)
                .build();
    }
}

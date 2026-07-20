package com.ledgersaas.backend.scheduler;

import com.ledgersaas.backend.model.entity.Invoice;
import com.ledgersaas.backend.model.entity.Subscription;
import com.ledgersaas.backend.model.enums.BillingInterval;
import com.ledgersaas.backend.model.enums.InvoiceStatus;
import com.ledgersaas.backend.model.enums.SubscriptionStatus;
import com.ledgersaas.backend.repository.InvoiceRepository;
import com.ledgersaas.backend.repository.SubscriptionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;

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
                newInvoices.add(buildInvoice(subscription, InvoiceStatus.PAID, now));
                log.info("Abonelik yenilendi, PAID faturası kesildi. id={}, yeni dönem sonu={}",
                        subscription.getId(), subscription.getCurrentPeriodEnd());
            } else {
                subscription.setStatus(SubscriptionStatus.PAST_DUE);
                newInvoices.add(buildInvoice(subscription, InvoiceStatus.FAILED, now));
                log.warn("Ödeme başarısız; abonelik PAST_DUE durumuna alındı, FAILED faturası oluşturuldu. "
                        + "Kullanıcıya bilgilendirme maili gönderilecek. subscriptionId={}", subscription.getId());
            }
        }

        subscriptionRepository.saveAll(expiredSubscriptions);
        invoiceRepository.saveAll(newInvoices);
        log.info("Abonelik kontrolü tamamlandı. Güncellenen abonelik: {}, kesilen fatura: {}",
                expiredSubscriptions.size(), newInvoices.size());
    }

    /**
     * Harici ödeme geçidini simüle eder; %90 ihtimalle başarılı döner.
     */
    private boolean simulatePayment(Subscription subscription) {
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

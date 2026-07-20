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
import com.ledgersaas.backend.service.PaymentGatewayService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    /** PAST_DUE aboneligin EXPIRED'a cekilmeden once tahsil edilmeye calisilacagi pencere (gun). */
    private static final int DUNNING_WINDOW_DAYS = 3;

    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentGatewayService paymentGatewayService;

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
        List<Subscription> pastDueSubscriptions =
                subscriptionRepository.findAllByStatus(SubscriptionStatus.PAST_DUE);

        if (expiredSubscriptions.isEmpty() && pastDueSubscriptions.isEmpty()) {
            log.info("İşlenecek abonelik bulunamadı (süresi dolan veya PAST_DUE yok). Kontrol zamanı: {}", now);
            return;
        }

        log.info("Abonelik kontrolü başladı. Süresi dolan: {}, dunning takibindeki (PAST_DUE): {}",
                expiredSubscriptions.size(), pastDueSubscriptions.size());

        List<Invoice> newInvoices = new ArrayList<>();
        List<Object> pendingEvents = new ArrayList<>();

        processRenewals(expiredSubscriptions, now, newInvoices, pendingEvents);
        processDunning(pastDueSubscriptions, now, newInvoices, pendingEvents);

        List<Subscription> processedSubscriptions = new ArrayList<>(expiredSubscriptions);
        processedSubscriptions.addAll(pastDueSubscriptions);
        subscriptionRepository.saveAll(processedSubscriptions);
        invoiceRepository.saveAll(newInvoices);
        pendingEvents.forEach(eventPublisher::publishEvent);

        log.info("Abonelik kontrolü tamamlandı. İşlenen abonelik: {}, kesilen fatura: {}, yayınlanan event: {}",
                processedSubscriptions.size(), newInvoices.size(), pendingEvents.size());
    }

    /**
     * Dönemi dolan ACTIVE aboneliklerin yenileme/iptal akışı.
     */
    private void processRenewals(
            List<Subscription> expiredSubscriptions,
            LocalDateTime now,
            List<Invoice> newInvoices,
            List<Object> pendingEvents) {

        for (Subscription subscription : expiredSubscriptions) {
            if (Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())) {
                subscription.setStatus(SubscriptionStatus.EXPIRED);
                log.info("Abonelik dönem sonunda iptal edildi ve EXPIRED durumuna alındı. id={}", subscription.getId());
                continue;
            }

            if (paymentGatewayService.chargeSubscription(subscription)) {
                renewSubscription(subscription, now);
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
    }

    /**
     * Dunning süreci: PAST_DUE aboneliklerde son FAILED faturanın yaşına göre
     * ya gün aşırı yeniden tahsilat denenir ya da pencere dolduysa abonelik
     * EXPIRED'a çekilir.
     *
     * NOT: Dunning denemesi başarısız olduğunda bilinçli olarak yeni FAILED
     * fatura kesilmez; kesilseydi "en son FAILED fatura" tarihi her denemede
     * sıfırlanır ve abonelik hiçbir zaman EXPIRED olamazdı.
     */
    private void processDunning(
            List<Subscription> pastDueSubscriptions,
            LocalDateTime now,
            List<Invoice> newInvoices,
            List<Object> pendingEvents) {

        for (Subscription subscription : pastDueSubscriptions) {
            Optional<Invoice> lastFailedInvoice = invoiceRepository
                    .findFirstBySubscriptionIdAndStatusOrderByCreatedAtDesc(
                            subscription.getId(), InvoiceStatus.FAILED);

            if (lastFailedInvoice.isEmpty()) {
                log.warn("PAST_DUE abonelik için FAILED fatura kaydı bulunamadı; dunning kararı verilemiyor, "
                        + "kayıt atlandı. subscriptionId={}", subscription.getId());
                continue;
            }

            long daysSinceFailure = ChronoUnit.DAYS.between(lastFailedInvoice.get().getCreatedAt(), now);

            if (daysSinceFailure > DUNNING_WINDOW_DAYS) {
                subscription.setStatus(SubscriptionStatus.EXPIRED);
                log.info("Dunning penceresi doldu ({} gün); abonelik EXPIRED durumuna çekildi ve sistem erişimi "
                        + "kapatıldı. subscriptionId={}, userEmail={}",
                        daysSinceFailure, subscription.getId(), subscription.getUser().getEmail());
            } else if (daysSinceFailure % 2 == 1) {
                // Gun asiri deneme penceresi: 1. ve 3. gunlerde tahsilat denenir
                if (paymentGatewayService.chargeSubscription(subscription)) {
                    renewSubscription(subscription, now);
                    Invoice invoice = buildInvoice(subscription, InvoiceStatus.PAID, now);
                    newInvoices.add(invoice);
                    pendingEvents.add(new PaymentSuccessEvent(subscription, invoice));
                    log.info("Dunning tahsilatı başarılı; abonelik yeniden ACTIVE. subscriptionId={}, userEmail={}",
                            subscription.getId(), subscription.getUser().getEmail());
                } else {
                    log.warn("Dunning tahsilat denemesi başarısız; abonelik PAST_DUE kalmaya devam ediyor. "
                            + "subscriptionId={}, denemeGünü={}", subscription.getId(), daysSinceFailure);
                }
            } else {
                log.info("Dunning bekleme günü; tahsilat denemesi yapılmadı. subscriptionId={}, geçenGün={}",
                        subscription.getId(), daysSinceFailure);
            }
        }
    }

    private void renewSubscription(Subscription subscription, LocalDateTime now) {
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(calculateNextPeriodEnd(now, subscription));
        subscription.setStatus(SubscriptionStatus.ACTIVE);
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

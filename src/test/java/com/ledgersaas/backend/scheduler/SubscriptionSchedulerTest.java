package com.ledgersaas.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ledgersaas.backend.model.entity.Invoice;
import com.ledgersaas.backend.model.entity.Plan;
import com.ledgersaas.backend.model.entity.Subscription;
import com.ledgersaas.backend.model.entity.User;
import com.ledgersaas.backend.model.enums.BillingInterval;
import com.ledgersaas.backend.model.enums.InvoiceStatus;
import com.ledgersaas.backend.model.enums.SubscriptionStatus;
import com.ledgersaas.backend.model.event.PaymentFailureEvent;
import com.ledgersaas.backend.model.event.PaymentSuccessEvent;
import com.ledgersaas.backend.repository.InvoiceRepository;
import com.ledgersaas.backend.repository.SubscriptionRepository;
import com.ledgersaas.backend.service.PaymentGatewayService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SubscriptionSchedulerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PaymentGatewayService paymentGatewayService;

    @InjectMocks
    private SubscriptionScheduler scheduler;

    @Captor
    private ArgumentCaptor<List<Invoice>> invoiceCaptor;

    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    private Subscription buildSubscription(boolean cancelAtPeriodEnd, BillingInterval interval, BigDecimal price) {
        User user = User.builder()
                .id(1L)
                .email("test@ledgersaas.com")
                .fullName("Test User")
                .build();

        Plan plan = Plan.builder()
                .id(1L)
                .name("Pro Plan")
                .price(price)
                .billingInterval(interval)
                .build();

        return Subscription.builder()
                .id(10L)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusMonths(1))
                .currentPeriodEnd(LocalDateTime.now().minusDays(1))
                .cancelAtPeriodEnd(cancelAtPeriodEnd)
                .build();
    }

    private Invoice buildFailedInvoice(Subscription subscription, LocalDateTime createdAt) {
        Invoice invoice = Invoice.builder()
                .id(99L)
                .subscription(subscription)
                .user(subscription.getUser())
                .amount(subscription.getPlan().getPrice())
                .status(InvoiceStatus.FAILED)
                .billingDate(createdAt)
                .build();
        invoice.setCreatedAt(createdAt);
        return invoice;
    }

    private void stubExpired(List<Subscription> subscriptions) {
        when(subscriptionRepository.findAllByStatusAndCurrentPeriodEndBefore(
                eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(subscriptions);
    }

    private void stubPastDue(List<Subscription> subscriptions) {
        when(subscriptionRepository.findAllByStatus(SubscriptionStatus.PAST_DUE))
                .thenReturn(subscriptions);
    }

    // -----------------------------------------------------------------
    // Yenileme (renewal) senaryolari
    // -----------------------------------------------------------------

    @Test
    @DisplayName("cancelAtPeriodEnd=true olan abonelik EXPIRED yapılmalı, fatura ve event üretilmemeli")
    void checkExpiredSubscriptions_whenCancelAtPeriodEnd_shouldExpireWithoutInvoice() {
        Subscription subscription =
                buildSubscription(true, BillingInterval.MONTHLY, new BigDecimal("29.99"));
        stubExpired(List.of(subscription));
        stubPastDue(List.of());

        scheduler.checkExpiredSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        verify(paymentGatewayService, never()).chargeSubscription(any());
        verify(subscriptionRepository).saveAll(List.of(subscription));
        verify(invoiceRepository).saveAll(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue()).isEmpty();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Ödeme başarılı + MONTHLY plan: dönem 1 ay uzatılmalı, PAID fatura ve PaymentSuccessEvent üretilmeli")
    void checkExpiredSubscriptions_whenPaymentSucceedsMonthly_shouldRenewOneMonthAndCreatePaidInvoice() {
        BigDecimal price = new BigDecimal("29.99");
        Subscription subscription = buildSubscription(false, BillingInterval.MONTHLY, price);
        stubExpired(List.of(subscription));
        stubPastDue(List.of());
        when(paymentGatewayService.chargeSubscription(subscription)).thenReturn(true);

        scheduler.checkExpiredSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodEnd())
                .isEqualTo(subscription.getCurrentPeriodStart().plusMonths(1));

        verify(subscriptionRepository).saveAll(List.of(subscription));
        verify(invoiceRepository).saveAll(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue()).hasSize(1);

        Invoice invoice = invoiceCaptor.getValue().get(0);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getAmount()).isEqualByComparingTo(price);
        assertThat(invoice.getSubscription()).isSameAs(subscription);
        assertThat(invoice.getUser()).isSameAs(subscription.getUser());
        assertThat(invoice.getBillingDate()).isEqualTo(subscription.getCurrentPeriodStart());

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PaymentSuccessEvent.class);
        PaymentSuccessEvent event = (PaymentSuccessEvent) eventCaptor.getValue();
        assertThat(event.subscription()).isSameAs(subscription);
        assertThat(event.invoice()).isSameAs(invoice);
    }

    @Test
    @DisplayName("Ödeme başarılı + YEARLY plan: dönem 1 yıl uzatılmalı, PAID fatura kesilmeli")
    void checkExpiredSubscriptions_whenPaymentSucceedsYearly_shouldRenewOneYearAndCreatePaidInvoice() {
        Subscription subscription =
                buildSubscription(false, BillingInterval.YEARLY, new BigDecimal("299.99"));
        stubExpired(List.of(subscription));
        stubPastDue(List.of());
        when(paymentGatewayService.chargeSubscription(subscription)).thenReturn(true);

        scheduler.checkExpiredSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodEnd())
                .isEqualTo(subscription.getCurrentPeriodStart().plusYears(1));

        verify(invoiceRepository).saveAll(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue())
                .singleElement()
                .extracting(Invoice::getStatus)
                .isEqualTo(InvoiceStatus.PAID);

        verify(eventPublisher).publishEvent(any(PaymentSuccessEvent.class));
    }

    @Test
    @DisplayName("Ödeme başarısız: abonelik PAST_DUE olmalı, FAILED fatura ve PaymentFailureEvent üretilmeli")
    void checkExpiredSubscriptions_whenPaymentFails_shouldMarkPastDueAndCreateFailedInvoice() {
        LocalDateTime originalPeriodEnd = LocalDateTime.now().minusDays(1);
        Subscription subscription =
                buildSubscription(false, BillingInterval.MONTHLY, new BigDecimal("29.99"));
        subscription.setCurrentPeriodEnd(originalPeriodEnd);
        stubExpired(List.of(subscription));
        stubPastDue(List.of());
        when(paymentGatewayService.chargeSubscription(subscription)).thenReturn(false);

        scheduler.checkExpiredSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(originalPeriodEnd);

        verify(invoiceRepository).saveAll(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue())
                .singleElement()
                .extracting(Invoice::getStatus)
                .isEqualTo(InvoiceStatus.FAILED);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PaymentFailureEvent.class);
        PaymentFailureEvent event = (PaymentFailureEvent) eventCaptor.getValue();
        assertThat(event.subscription()).isSameAs(subscription);
        assertThat(event.failureReason()).isNotBlank();
    }

    @Test
    @DisplayName("İşlenecek abonelik yoksa hiçbir kayıt işlemi ve event yayını yapılmamalı")
    void checkExpiredSubscriptions_whenNoSubscriptions_shouldDoNothing() {
        stubExpired(List.of());
        stubPastDue(List.of());

        scheduler.checkExpiredSubscriptions();

        verify(subscriptionRepository, never()).saveAll(any());
        verify(invoiceRepository, never()).saveAll(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // -----------------------------------------------------------------
    // Dunning senaryolari
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Dunning: son FAILED fatura 3 günden eski olan PAST_DUE abonelik EXPIRED yapılmalı")
    void checkExpiredSubscriptions_whenPastDueOlderThanDunningWindow_shouldExpire() {
        Subscription subscription =
                buildSubscription(false, BillingInterval.MONTHLY, new BigDecimal("29.99"));
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        stubExpired(List.of());
        stubPastDue(List.of(subscription));
        when(invoiceRepository.findFirstBySubscriptionIdAndStatusOrderByCreatedAtDesc(
                subscription.getId(), InvoiceStatus.FAILED))
                .thenReturn(Optional.of(buildFailedInvoice(subscription, LocalDateTime.now().minusDays(4))));

        scheduler.checkExpiredSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        verify(paymentGatewayService, never()).chargeSubscription(any());
        verify(subscriptionRepository).saveAll(List.of(subscription));
        verify(invoiceRepository).saveAll(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue()).isEmpty();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Dunning: pencere içindeki (1 gün) PAST_DUE abonelikte tahsilat başarılıysa abonelik yeniden ACTIVE olmalı")
    void checkExpiredSubscriptions_whenDunningRetrySucceeds_shouldReactivateSubscription() {
        BigDecimal price = new BigDecimal("29.99");
        Subscription subscription = buildSubscription(false, BillingInterval.MONTHLY, price);
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        stubExpired(List.of());
        stubPastDue(List.of(subscription));
        when(invoiceRepository.findFirstBySubscriptionIdAndStatusOrderByCreatedAtDesc(
                subscription.getId(), InvoiceStatus.FAILED))
                .thenReturn(Optional.of(buildFailedInvoice(subscription, LocalDateTime.now().minusDays(1))));
        when(paymentGatewayService.chargeSubscription(subscription)).thenReturn(true);

        scheduler.checkExpiredSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodEnd())
                .isEqualTo(subscription.getCurrentPeriodStart().plusMonths(1));

        verify(invoiceRepository).saveAll(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue())
                .singleElement()
                .extracting(Invoice::getStatus)
                .isEqualTo(InvoiceStatus.PAID);
        verify(eventPublisher).publishEvent(any(PaymentSuccessEvent.class));
    }

    @Test
    @DisplayName("Dunning: bekleme gününde (2 gün, çift) tahsilat denenmemeli ve abonelik PAST_DUE kalmalı")
    void checkExpiredSubscriptions_whenDunningWaitDay_shouldNotCharge() {
        Subscription subscription =
                buildSubscription(false, BillingInterval.MONTHLY, new BigDecimal("29.99"));
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        stubExpired(List.of());
        stubPastDue(List.of(subscription));
        when(invoiceRepository.findFirstBySubscriptionIdAndStatusOrderByCreatedAtDesc(
                subscription.getId(), InvoiceStatus.FAILED))
                .thenReturn(Optional.of(buildFailedInvoice(subscription, LocalDateTime.now().minusDays(2))));

        scheduler.checkExpiredSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        verify(paymentGatewayService, never()).chargeSubscription(any());
        verify(invoiceRepository).saveAll(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue()).isEmpty();
        verify(eventPublisher, never()).publishEvent(any());
    }
}

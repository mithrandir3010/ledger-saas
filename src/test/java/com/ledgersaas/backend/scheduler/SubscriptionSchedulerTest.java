package com.ledgersaas.backend.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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
import com.ledgersaas.backend.repository.InvoiceRepository;
import com.ledgersaas.backend.repository.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionSchedulerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Spy
    @InjectMocks
    private SubscriptionScheduler scheduler;

    @Captor
    private ArgumentCaptor<List<Invoice>> invoiceCaptor;

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

    @Test
    @DisplayName("cancelAtPeriodEnd=true olan abonelik EXPIRED yapılmalı, fatura kesilmemeli")
    void checkExpiredSubscriptions_whenCancelAtPeriodEnd_shouldExpireWithoutInvoice() {
        Subscription subscription =
                buildSubscription(true, BillingInterval.MONTHLY, new BigDecimal("29.99"));
        when(subscriptionRepository.findAllByStatusAndCurrentPeriodEndBefore(
                eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(subscription));

        scheduler.checkExpiredSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        verify(scheduler, never()).simulatePayment(any());
        verify(subscriptionRepository).saveAll(List.of(subscription));
        verify(invoiceRepository).saveAll(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("Ödeme başarılı + MONTHLY plan: dönem 1 ay uzatılmalı, PAID fatura kesilmeli")
    void checkExpiredSubscriptions_whenPaymentSucceedsMonthly_shouldRenewOneMonthAndCreatePaidInvoice() {
        BigDecimal price = new BigDecimal("29.99");
        Subscription subscription = buildSubscription(false, BillingInterval.MONTHLY, price);
        when(subscriptionRepository.findAllByStatusAndCurrentPeriodEndBefore(
                eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(subscription));
        doReturn(true).when(scheduler).simulatePayment(subscription);

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
    }

    @Test
    @DisplayName("Ödeme başarılı + YEARLY plan: dönem 1 yıl uzatılmalı, PAID fatura kesilmeli")
    void checkExpiredSubscriptions_whenPaymentSucceedsYearly_shouldRenewOneYearAndCreatePaidInvoice() {
        Subscription subscription =
                buildSubscription(false, BillingInterval.YEARLY, new BigDecimal("299.99"));
        when(subscriptionRepository.findAllByStatusAndCurrentPeriodEndBefore(
                eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(subscription));
        doReturn(true).when(scheduler).simulatePayment(subscription);

        scheduler.checkExpiredSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodEnd())
                .isEqualTo(subscription.getCurrentPeriodStart().plusYears(1));

        verify(invoiceRepository).saveAll(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue())
                .singleElement()
                .extracting(Invoice::getStatus)
                .isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    @DisplayName("Ödeme başarısız: abonelik PAST_DUE olmalı, FAILED fatura kesilmeli")
    void checkExpiredSubscriptions_whenPaymentFails_shouldMarkPastDueAndCreateFailedInvoice() {
        LocalDateTime originalPeriodEnd = LocalDateTime.now().minusDays(1);
        Subscription subscription =
                buildSubscription(false, BillingInterval.MONTHLY, new BigDecimal("29.99"));
        subscription.setCurrentPeriodEnd(originalPeriodEnd);
        when(subscriptionRepository.findAllByStatusAndCurrentPeriodEndBefore(
                eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(subscription));
        doReturn(false).when(scheduler).simulatePayment(subscription);

        scheduler.checkExpiredSubscriptions();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(originalPeriodEnd);

        verify(invoiceRepository).saveAll(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue())
                .singleElement()
                .extracting(Invoice::getStatus)
                .isEqualTo(InvoiceStatus.FAILED);
    }

    @Test
    @DisplayName("Süresi dolan abonelik yoksa hiçbir kayıt işlemi yapılmamalı")
    void checkExpiredSubscriptions_whenNoExpiredSubscriptions_shouldDoNothing() {
        when(subscriptionRepository.findAllByStatusAndCurrentPeriodEndBefore(
                eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.checkExpiredSubscriptions();

        verify(subscriptionRepository, never()).saveAll(any());
        verify(invoiceRepository, never()).saveAll(any());
    }
}

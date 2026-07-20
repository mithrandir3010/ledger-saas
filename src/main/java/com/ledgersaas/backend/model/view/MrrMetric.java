package com.ledgersaas.backend.model.view;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

/**
 * view_mrr_metrics veritabani view'inin salt okunur JPA karsiligi.
 * Subselect uzerinden esler; insert/update/delete uretilmez.
 */
@Entity
@Immutable
@Subselect("SELECT * FROM view_mrr_metrics")
@Synchronize({"subscriptions", "plans", "invoices"})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MrrMetric {

    @Id
    private Long id;

    @Column(name = "total_mrr")
    private BigDecimal totalMrr;

    @Column(name = "paying_subscription_count")
    private Long payingSubscriptionCount;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;
}

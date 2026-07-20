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
 * view_arpu_metrics veritabani view'inin salt okunur JPA karsiligi.
 */
@Entity
@Immutable
@Subselect("SELECT * FROM view_arpu_metrics")
@Synchronize({"subscriptions", "plans", "invoices", "users"})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArpuMetric {

    @Id
    private Long id;

    @Column(name = "arpu")
    private BigDecimal arpu;

    @Column(name = "active_user_count")
    private Long activeUserCount;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;
}

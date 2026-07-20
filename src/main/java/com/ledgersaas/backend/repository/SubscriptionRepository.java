package com.ledgersaas.backend.repository;

import com.ledgersaas.backend.model.entity.Subscription;
import com.ledgersaas.backend.model.enums.SubscriptionStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findAllByStatusAndCurrentPeriodEndBefore(SubscriptionStatus status, LocalDateTime dateTime);
}

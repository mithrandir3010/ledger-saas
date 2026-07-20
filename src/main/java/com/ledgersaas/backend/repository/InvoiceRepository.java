package com.ledgersaas.backend.repository;

import com.ledgersaas.backend.model.entity.Invoice;
import com.ledgersaas.backend.model.enums.InvoiceStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findFirstBySubscriptionIdAndStatusOrderByCreatedAtDesc(
            Long subscriptionId, InvoiceStatus status);
}

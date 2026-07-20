package com.ledgersaas.backend.repository;

import com.ledgersaas.backend.model.view.ArpuMetric;
import com.ledgersaas.backend.model.view.MrrMetric;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Finansal raporlama view'larina salt okunur erisim saglar.
 * Kasitli olarak JpaRepository yerine Repository'den turetilmistir;
 * boylece save/delete gibi yazma operasyonlari hic aciga cikmaz.
 */
@org.springframework.stereotype.Repository
public interface FinancialReportRepository extends Repository<MrrMetric, Long> {

    @Query("SELECT m FROM MrrMetric m")
    Optional<MrrMetric> fetchMrrMetric();

    @Query("SELECT a FROM ArpuMetric a")
    Optional<ArpuMetric> fetchArpuMetric();
}

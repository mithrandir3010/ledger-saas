package com.ledgersaas.backend.controller;

import com.ledgersaas.backend.model.view.ArpuMetric;
import com.ledgersaas.backend.model.view.MrrMetric;
import com.ledgersaas.backend.repository.FinancialReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Finansal metrik endpoint'leri. SecurityConfig geregi yalnizca
 * authenticated kullanicilara aciktir; admin rol modeli geldiginde
 * @PreAuthorize("hasAuthority('ADMIN')") ile daraltilacak.
 */
@RestController
@RequestMapping("/api/v1/admin/metrics")
@RequiredArgsConstructor
public class FinancialReportController {

    private final FinancialReportRepository financialReportRepository;

    @GetMapping("/mrr")
    public ResponseEntity<MrrMetric> getMrr() {
        return ResponseEntity.of(financialReportRepository.fetchMrrMetric());
    }

    @GetMapping("/arpu")
    public ResponseEntity<ArpuMetric> getArpu() {
        return ResponseEntity.of(financialReportRepository.fetchArpuMetric());
    }
}

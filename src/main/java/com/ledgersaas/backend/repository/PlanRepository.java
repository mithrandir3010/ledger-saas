package com.ledgersaas.backend.repository;

import com.ledgersaas.backend.model.entity.Plan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    List<Plan> findAllByIsActiveTrue();
}

package com.trustlayer.repository;

import com.trustlayer.entity.RiskIndicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RiskIndicatorRepository extends JpaRepository<RiskIndicator, Long> {

    List<RiskIndicator> findByScanResult_IdOrderByIdAsc(Long scanResultId);

    List<RiskIndicator> findByScanResult_IdInOrderByIdAsc(Collection<Long> scanResultIds);

    List<RiskIndicator> findByTransaction_IdOrderByIdAsc(Long transactionId);
}

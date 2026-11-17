package com.trustlayer.repository;

import com.trustlayer.entity.ScanResult;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.entity.enums.ScanType;
import com.trustlayer.repository.projection.RiskClassificationCount;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScanResultRepository extends JpaRepository<ScanResult, Long> {

    Optional<ScanResult> findByScan_Id(Long scanId);

    List<ScanResult> findByScan_IdIn(Collection<Long> scanIds);

    @Query("""
            select count(result) from ScanResult result
            where result.scan.user.id = :userId
              and result.scan.scanType = :scanType
              and result.riskClassification in :riskClassifications
            """)
    long countReviewRecommendedByUserIdAndScanType(
            @Param("userId") Long userId,
            @Param("scanType") ScanType scanType,
            @Param("riskClassifications") Collection<RiskClassification> riskClassifications
    );

    @Query("""
            select result.riskClassification as riskClassification, count(result) as total
            from ScanResult result
            where result.scan.user.id = :userId
            group by result.riskClassification
            """)
    List<RiskClassificationCount> countRiskDistributionByUserId(@Param("userId") Long userId);
}

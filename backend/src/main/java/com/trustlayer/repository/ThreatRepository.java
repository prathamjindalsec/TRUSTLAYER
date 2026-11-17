package com.trustlayer.repository;

import com.trustlayer.entity.Threat;
import com.trustlayer.entity.enums.ThreatCategory;
import com.trustlayer.entity.enums.ThreatType;
import com.trustlayer.entity.enums.ThreatVerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ThreatRepository extends JpaRepository<Threat, Long> {

    Optional<Threat> findByThreatTypeAndIndicatorHash(ThreatType threatType, String indicatorHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select threat from Threat threat
            where threat.threatType = :threatType and threat.indicatorHash = :indicatorHash
            """)
    Optional<Threat> findForReportUpdate(
            @Param("threatType") ThreatType threatType,
            @Param("indicatorHash") String indicatorHash
    );

    @Query("""
            select threat from Threat threat
            where (:threatType is null or threat.threatType = :threatType)
              and (:category is null or threat.category = :category)
              and (:verificationStatus is null or threat.verificationStatus = :verificationStatus)
              and (:safeQuery is null or lower(threat.indicatorValue) like lower(concat('%', :safeQuery, '%')))
            order by threat.lastReportedAt desc, threat.id desc
            """)
    Page<Threat> search(
            @Param("threatType") ThreatType threatType,
            @Param("category") ThreatCategory category,
            @Param("verificationStatus") ThreatVerificationStatus verificationStatus,
            @Param("safeQuery") String safeQuery,
            Pageable pageable
    );

    long countByVerificationStatus(ThreatVerificationStatus verificationStatus);

    long countByCategory(ThreatCategory category);

    List<Threat> findTop20ByReportCountGreaterThanEqualOrderByReportCountDescLastReportedAtDesc(int minimumReports);

    @Query("select coalesce(sum(threat.reportCount), 0) from Threat threat")
    Long totalReportCount();
}

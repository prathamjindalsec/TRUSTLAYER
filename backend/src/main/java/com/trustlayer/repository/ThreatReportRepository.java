package com.trustlayer.repository;

import com.trustlayer.entity.ThreatReport;
import com.trustlayer.entity.enums.ThreatType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ThreatReportRepository extends JpaRepository<ThreatReport, Long> {

    boolean existsByReporter_IdAndIndicatorTypeAndIndicatorHash(Long reporterId, ThreatType indicatorType, String indicatorHash);

    List<ThreatReport> findTop6ByReporter_IdOrderByCreatedAtDescIdDesc(Long reporterId);
}

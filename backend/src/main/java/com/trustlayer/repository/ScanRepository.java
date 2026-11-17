package com.trustlayer.repository;

import com.trustlayer.entity.Scan;
import com.trustlayer.entity.enums.ScanType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScanRepository extends JpaRepository<Scan, Long> {

    long countByUser_IdAndScanType(Long userId, ScanType scanType);

    Page<Scan> findByUser_IdAndScanTypeOrderByRequestedAtDescIdDesc(Long userId, ScanType scanType, Pageable pageable);

    Optional<Scan> findByIdAndUser_IdAndScanType(Long id, Long userId, ScanType scanType);
}

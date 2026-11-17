package com.trustlayer.repository;

import com.trustlayer.entity.SimulatedTransaction;
import com.trustlayer.entity.enums.RiskClassification;
import com.trustlayer.entity.enums.TransactionAnalysisStatus;
import com.trustlayer.repository.projection.RiskClassificationCount;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SimulatedTransactionRepository extends JpaRepository<SimulatedTransaction, Long> {

    Page<SimulatedTransaction> findByUser_IdOrderByTransactionTimeDescIdDesc(Long userId, Pageable pageable);

    long countByUser_Id(Long userId);

    long countByUser_IdAndRiskClassificationIn(Long userId, Collection<RiskClassification> riskClassifications);

    Optional<SimulatedTransaction> findByIdAndUser_Id(Long id, Long userId);

    List<SimulatedTransaction> findTop50ByUser_IdAndBeneficiaryHashAndAnalysisStatusOrderByTransactionTimeDescIdDesc(
            Long userId,
            String beneficiaryHash,
            TransactionAnalysisStatus analysisStatus
    );

    List<SimulatedTransaction> findTop6ByUser_IdOrderByCreatedAtDescIdDesc(Long userId);

    @Query("""
            select transaction.riskClassification as riskClassification, count(transaction) as total
            from SimulatedTransaction transaction
            where transaction.user.id = :userId and transaction.riskClassification is not null
            group by transaction.riskClassification
            """)
    List<RiskClassificationCount> countRiskDistributionByUserId(@Param("userId") Long userId);
}

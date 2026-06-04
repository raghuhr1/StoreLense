package com.storelense.reporting.domain.repository;

import com.storelense.reporting.domain.entity.KpiDaily;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KpiDailyRepository extends JpaRepository<KpiDaily, UUID> {

    Optional<KpiDaily> findByStoreIdAndKpiDate(UUID storeId, LocalDate date);

    Page<KpiDaily> findByStoreIdOrderByKpiDateDesc(UUID storeId, Pageable pageable);

    @Query("SELECT k FROM KpiDaily k WHERE k.kpiDate BETWEEN :from AND :to ORDER BY k.storeId, k.kpiDate")
    List<KpiDaily> findByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT k FROM KpiDaily k WHERE k.storeId = :storeId AND k.kpiDate BETWEEN :from AND :to ORDER BY k.kpiDate")
    List<KpiDaily> findByStoreAndDateRange(@Param("storeId") UUID storeId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);
}

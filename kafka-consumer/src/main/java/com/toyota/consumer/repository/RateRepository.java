package com.toyota.consumer.repository;

import com.toyota.consumer.model.RateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Toyota Financial Data Platform - Rate Repository
 * 
 * JPA repository interface for financial rate data persistence.
 * Provides custom query methods for rate retrieval by time range,
 * symbol, and duplicate detection for the Toyota platform.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Repository
public interface RateRepository extends JpaRepository<RateEntity, Long> {
    
  List<RateEntity> findByRateUpdatetimeAfter(LocalDateTime dateTime);
    List<RateEntity> findByRateName(String rateName); 
    boolean existsByRateNameAndRateUpdatetime(String rateName, LocalDateTime rateUpdatetime);
}

package com.toyota.consumer.repository;

import com.toyota.consumer.model.RateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RateRepository extends JpaRepository<RateEntity, Long> {
    
  List<RateEntity> findByRateUpdatetimeAfter(LocalDateTime dateTime);
    List<RateEntity> findByRateName(String rateName); 
    boolean existsByRateNameAndRateUpdatetime(String rateName, LocalDateTime rateUpdatetime);
}

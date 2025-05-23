package com.toyota.consumer.repository;

import com.toyota.consumer.model.RateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for managing rate entities in the database.
 */
@Repository
public interface RateRepository extends JpaRepository<RateEntity, Long> {
    // Basic CRUD operations are automatically provided by JpaRepository

    List<RateEntity> findByReceivedAtAfter(LocalDateTime dateTime);
    List<RateEntity> findByRateName(String rateName); 
}

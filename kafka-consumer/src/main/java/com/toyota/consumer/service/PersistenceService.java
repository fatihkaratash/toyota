package com.toyota.consumer.service;

import com.toyota.consumer.model.RateEntity;
import com.toyota.consumer.repository.RateRepository; 
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Toyota Financial Data Platform - Persistence Service
 * 
 * Manages database persistence operations for financial rate data.
 * Provides transactional safety, duplicate detection, and bulk operations
 * for efficient storage of real-time rate information.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PersistenceService {

    private final RateRepository rateRepository; 
    
    @Transactional
    public RateEntity saveRate(RateEntity rateEntity) {
        if (rateEntity == null) {
            log.warn("Cannot save null rate entity");
            return null;
        }
        rateEntity.setDbUpdatetime(LocalDateTime.now());

        RateEntity savedEntity = rateRepository.save(rateEntity); 
        log.info("Successfully saved rate to database: {}, ID: {}", 
                    savedEntity.getRateName(), savedEntity.getId());
        
        return savedEntity;
    }

    @Transactional(readOnly = true)
    public long countRates() {
        return rateRepository.count(); 
    }

    @Transactional
    public List<RateEntity> saveAllRates(List<RateEntity> rateEntities) {
        if (rateEntities == null || rateEntities.isEmpty()) {
            log.info("Received an empty or null list of rate entities to save.");
            return new ArrayList<>();
        }
        LocalDateTime now = LocalDateTime.now();
        List<RateEntity> newEntitiesToSave = new ArrayList<>();
        List<RateEntity> duplicateEntities = new ArrayList<>();

        for (RateEntity entity : rateEntities) {
           
            if (rateRepository.existsByRateNameAndRateUpdatetime(entity.getRateName(), entity.getRateUpdatetime())) { 
                duplicateEntities.add(entity);
            } else {
                entity.setDbUpdatetime(now); 
                newEntitiesToSave.add(entity);
            }
        }

        if (!duplicateEntities.isEmpty()) {
            log.info("Found {} duplicate entities that will be skipped: {}",
                    duplicateEntities.size(),
                    duplicateEntities.stream()
                                     .map(e -> e.getRateName() + "@" + e.getRateUpdatetime())
                                     .collect(Collectors.joining(", ")));
        }

        if (newEntitiesToSave.isEmpty()) {
            log.info("No new entities to save after filtering duplicates.");
            return new ArrayList<>(); 
        }    
        
        log.info("Attempting to save {} new entities.", newEntitiesToSave.size());
        List<RateEntity> savedEntities = rateRepository.saveAll(newEntitiesToSave); 
        log.info("Successfully saved {} new entities.", savedEntities.size());
        return savedEntities;
    }
}
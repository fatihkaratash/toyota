package com.toyota.consumer.service;

import com.toyota.consumer.model.RateEntity;
import com.toyota.consumer.repository.RateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service responsible for persisting rate data to the database.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PersistenceService {

    private final RateRepository rateRepository;
    
    /**
     * Save a rate entity to the database.
     * 
     * @param rateEntity The rate entity to save
     * @throws DataAccessException If there's an error accessing the database
     */
    @Transactional
    public RateEntity saveRate(RateEntity rateEntity) {
        if (rateEntity == null) {
            log.warn("Cannot save null rate entity");
            return null;
        }
        
        // Set processed timestamp
       // rateEntity.setProcessedAt(LocalDateTime.now());
        
        // Save to database
        RateEntity savedEntity = rateRepository.save(rateEntity);
        log.info("Successfully saved rate to database: {}, ID: {}", 
                    savedEntity.getRateName(), savedEntity.getId());
        
        return savedEntity;
    }
    
    @Transactional(readOnly = true)
    public long countRates() {
        return rateRepository.count();
    }
}

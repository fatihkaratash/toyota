package com.toyota.mainapp.kafka;

import com.toyota.mainapp.dto.model.BaseRateDto;

import java.util.List;

/**
 * Toyota Financial Data Platform - Sequential Publisher Interface
 * 
 * Contract for sequential Kafka message publishing supporting immediate
 * snapshot distribution and individual rate publishing. Ensures ordered
 * delivery of financial data across the platform messaging infrastructure.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
public interface SequentialPublisher {
    
    void publishImmediateSnapshot(List<String> rateStrings, String pipelineId);
    
    void publishRate(BaseRateDto rate);
    
    void publishRawRate(BaseRateDto rawRate);
    
    void publishCalculatedRate(BaseRateDto calculatedRate);
    
}
package com.toyota.mainapp.kafka;

import com.toyota.mainapp.dto.model.BaseRateDto;

import java.util.List;

public interface SequentialPublisher {
    
    void publishImmediateSnapshot(List<String> rateStrings, String pipelineId);
    
    void publishRate(BaseRateDto rate);
    
    void publishRawRate(BaseRateDto rawRate);
    
    void publishCalculatedRate(BaseRateDto calculatedRate);
    
}
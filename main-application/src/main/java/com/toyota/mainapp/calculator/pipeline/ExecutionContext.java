package com.toyota.mainapp.calculator.pipeline;

import com.toyota.mainapp.dto.model.BaseRateDto;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ✅ EXECUTION CONTEXT: Pipeline state management
 * Contains all data needed throughout pipeline execution
 */
@Data
@Builder
public class ExecutionContext {
    
    private String pipelineId;
    private long startTime;
    private BaseRateDto triggeringRate;
    
    // Stage results storage
    @Builder.Default
    private Map<String, BaseRateDto> rawRates = new HashMap<>();
    
    @Builder.Default
    private List<BaseRateDto> calculatedRates = new ArrayList<>();
    
    @Builder.Default  
    private List<String> stageResults = new ArrayList<>();
    
    /**
     * ✅ ADD RAW RATE: Store raw rate by symbol
     */
    public void addRawRate(String symbol, BaseRateDto rate) {
        this.rawRates.put(symbol, rate);
    }
    
    /**
     * ✅ ADD CALCULATED RATE: Store calculated rate
     */
    public void addCalculatedRate(BaseRateDto rate) {
        this.calculatedRates.add(rate);
    }
    
    /**
     * ✅ ADD STAGE RESULT: Log stage completion
     */
    public void addStageResult(String result) {
        this.stageResults.add(result);
    }
    
    /**
     * ✅ GET CALCULATED RATES: For batch assembly
     */
    public List<BaseRateDto> getCalculatedRates() {
        return new ArrayList<>(calculatedRates);
    }
}

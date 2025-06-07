package com.toyota.mainapp.calculator.pipeline.stage;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.calculator.RuleEngineService;
import com.toyota.mainapp.calculator.pipeline.ExecutionContext;
import com.toyota.mainapp.calculator.pipeline.StageExecutionException;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.kafka.KafkaPublishingService;
import com.toyota.mainapp.util.SymbolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects; // ✅ FIXED: Missing import

/**
 * ✅ STAGE 2: Average Calculation
 * Calculate averages and publish to individual JSON topic
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AverageCalculationStage implements CalculationStage {

    private final RateCacheService rateCacheService;
    private final KafkaPublishingService kafkaPublishingService;
    private final RuleEngineService ruleEngineService;

    @Override
    public void execute(ExecutionContext context) throws StageExecutionException {
        try {
            BaseRateDto triggeringRate = context.getTriggeringRate();
            String pipelineId = context.getPipelineId();
            
            log.debug("Stage 2 [{}]: Processing average calculations for symbol: {}", 
                    pipelineId, triggeringRate.getSymbol());

            // ✅ CONFIG-DRIVEN: Find rules that use this symbol as input
            List<CalculationRuleDto> allRules = ruleEngineService.getRulesByInputSymbol(triggeringRate.getSymbol());
            
            for (CalculationRuleDto rule : allRules) {
                // ✅ CONFIG-DRIVEN: Check strategy type from config
                if (!"AVG".equals(rule.getStrategyType())) {
                    continue;
                }
                
                try {
                    // Collect required input rates from cache
                    Map<String, BaseRateDto> inputRates = collectInputRates(rule);
                    
                    // ✅ CONFIG-DRIVEN: Execute rule using strategy specified in config
                    Optional<BaseRateDto> avgResult = ruleEngineService.executeRule(rule, inputRates);
                    
                    if (avgResult.isPresent()) {
                        BaseRateDto avgRate = avgResult.get();
                        
                        // Ensure correct calculation type
                        avgRate.setRateType(com.toyota.mainapp.dto.model.RateType.CALCULATED);
                        
                        // Cache calculated rate
                        rateCacheService.cacheCalculatedRate(avgRate);
                        
                        // Publish to individual JSON topic
                        kafkaPublishingService.publishCalculatedJson(avgRate);
                        
                        // Add to context
                        context.addCalculatedRate(avgRate);
                        
                        log.info("Stage 2 [{}]: AVG calculated: {} -> {} (strategy: {})", 
                                pipelineId, rule.getOutputSymbol(), avgRate.getBid(), rule.getImplementation());
                    }
                    
                } catch (Exception e) {
                    log.error("Stage 2 [{}]: AVG calculation failed for rule {}: {}", 
                            pipelineId, rule.getOutputSymbol(), e.getMessage());
                    context.addError("AVG calculation failed: " + rule.getOutputSymbol());
                }
            }
            
            context.addStageResult("Stage 2: AVG calculations completed");
            
        } catch (Exception e) {
            throw new StageExecutionException("Stage 2 failed: " + e.getMessage(), e);
        }
    }
    
    // ✅ CONFIG-DRIVEN: Collect input rates based on rule configuration
    private Map<String, BaseRateDto> collectInputRates(CalculationRuleDto rule) {
        Map<String, BaseRateDto> inputRates = new HashMap<>();
        
        // Use rule's input symbols (not hard-coded logic)
        for (String inputSymbol : rule.getInputSymbols()) {
            String normalizedSymbol = SymbolUtils.normalizeSymbol(inputSymbol);
            
            // Get all raw rates for this symbol from cache
            var rawRates = rateCacheService.getRawRatesBySymbol(normalizedSymbol);
            for (BaseRateDto rate : rawRates) {
                String key = rate.getProviderName() + "_" + rate.getSymbol();
                inputRates.put(key, rate);
            }
        }
        
        log.debug("Collected {} input rates for rule: {}", inputRates.size(), rule.getOutputSymbol());
        return inputRates;
    }

    @Override
    public String getStageName() {
        return "AverageCalculationStage";
    }
    
    public boolean canExecute(ExecutionContext context) {
        return ruleEngineService.hasRules() && context.getTriggeringRate() != null;
    }
}

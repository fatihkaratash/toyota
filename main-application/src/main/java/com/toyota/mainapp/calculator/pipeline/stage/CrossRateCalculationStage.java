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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.toyota.mainapp.calculator.pipeline.StageResult;

/**
 * ✅ STAGE 3: Cross Rate Calculation  
 * Calculate cross rates and publish to individual JSON topic
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CrossRateCalculationStage implements CalculationStage {

    private final RateCacheService rateCacheService;
    private final KafkaPublishingService kafkaPublishingService;
    private final RuleEngineService ruleEngineService;

    @Override
    public void execute(ExecutionContext context) throws StageExecutionException {
        try {
            BaseRateDto triggeringRate = context.getTriggeringRate();
            String pipelineId = context.getPipelineId(); // ✅ CORRECTED: getPipelineId()
            
            log.debug("Stage 3 [{}]: Processing cross rate calculations for symbol: {}", 
                    pipelineId, triggeringRate.getSymbol());

            // ✅ CONFIG-DRIVEN: Find CROSS rules that use this symbol as input
            List<CalculationRuleDto> crossRules = ruleEngineService.getRulesByInputSymbol(triggeringRate.getSymbol());
            
            for (CalculationRuleDto rule : crossRules) {
                // ✅ CONFIG-DRIVEN: Check strategy type from rule config  
                if (!"CROSS".equals(rule.getStrategyType())) {
                    continue;
                }
                
                try {
                    // Collect required input rates (both raw and calculated)
                    Map<String, BaseRateDto> inputRates = collectInputRates(rule, context);
                    
                    // ✅ CONFIG-DRIVEN: Execute rule using strategy specified in config
                    Optional<BaseRateDto> crossResult = ruleEngineService.executeRule(rule, inputRates);
                    
                    if (crossResult.isPresent()) {
                        BaseRateDto crossRate = crossResult.get();
                        
                        // Ensure correct calculation type
                        crossRate.setRateType(com.toyota.mainapp.dto.model.RateType.CALCULATED);
                        
                        // Cache calculated rate
                        rateCacheService.cacheCalculatedRate(crossRate);
                        
                        // Publish to individual JSON topic
                        kafkaPublishingService.publishCalculatedJson(crossRate);
                        
                        // Add to context
                        context.addCalculatedRate(crossRate);
                        
                        log.info("Stage 3 [{}]: CROSS calculated: {} -> {} (strategy: {})", 
                                pipelineId, rule.getOutputSymbol(), crossRate.getBid(), rule.getImplementation());
                    }
                    
                } catch (Exception e) {
                    log.error("Stage 3 [{}]: CROSS calculation failed for rule {}: {}", 
                            pipelineId, rule.getOutputSymbol(), e.getMessage());
                    context.addError("CROSS calculation failed: " + rule.getOutputSymbol());
                }
            }
            
            context.addStageResult("Stage 3: CROSS calculations completed");
            
        } catch (Exception e) {
            throw new StageExecutionException("Stage 3 failed: " + e.getMessage(), e);
        }
    }
    
    // ✅ CONFIG-DRIVEN: Collect input rates based on rule dependencies
    private Map<String, BaseRateDto> collectInputRates(CalculationRuleDto rule, ExecutionContext context) {
        Map<String, BaseRateDto> inputRates = new HashMap<>();
        
        // Use rule's input symbols (config-driven)
        for (String inputSymbol : rule.getInputSymbols()) {
            String normalizedSymbol = SymbolUtils.normalizeSymbol(inputSymbol);
            
            // 1. Try from context first (recently calculated rates)
            for (BaseRateDto calculatedRate : context.getCalculatedRates()) {
                if (SymbolUtils.symbolsEquivalent(normalizedSymbol, calculatedRate.getSymbol())) {
                    String key = "CALCULATED_" + calculatedRate.getSymbol();
                    inputRates.put(key, calculatedRate);
                    continue;
                }
            }
            
            // 2. Try from cache (calculated rates)
            Optional<BaseRateDto> cachedCalculated = rateCacheService.getCalculatedRate(normalizedSymbol);
            if (cachedCalculated.isPresent()) {
                String key = "CACHED_CALC_" + normalizedSymbol;
                inputRates.put(key, cachedCalculated.get());
                continue;
            }
            
            // 3. Fallback to raw rates
            var rawRates = rateCacheService.getRawRatesBySymbol(normalizedSymbol);
            for (BaseRateDto rawRate : rawRates) {
                String key = rawRate.getProviderName() + "_" + rawRate.getSymbol();
                inputRates.put(key, rawRate);
            }
        }
        
        log.debug("Collected {} input rates for CROSS rule: {}", inputRates.size(), rule.getOutputSymbol());
        return inputRates;
    }
    
    @Override
    public String getStageName() {
        return "CrossRateCalculationStage";
    }
    
    public boolean canExecute(ExecutionContext context) {
        // ✅ FIXED: Correct method name
        return ruleEngineService.hasRules() && context.getTriggeringRate() != null;
    }
}

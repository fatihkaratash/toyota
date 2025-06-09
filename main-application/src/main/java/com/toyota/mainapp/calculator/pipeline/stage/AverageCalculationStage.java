package com.toyota.mainapp.calculator.pipeline.stage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.calculator.engine.CalculationStrategyFactory;
import com.toyota.mainapp.calculator.pipeline.ExecutionContext;
import com.toyota.mainapp.config.ApplicationProperties;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.config.CalculationRuleType;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.kafka.KafkaPublishingService;
import com.toyota.mainapp.util.CalculationInputUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Toyota Financial Data Platform - Average Calculation Stage
 * 
 * Pipeline stage that calculates weighted averages from multiple raw provider rates.
 * Processes affected average rules based on triggering rates and maintains
 * comprehensive rate snapshots for downstream cross-rate calculations.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
/** Average calculation stage implementation
 * Stage 2: Calculate AVG rates from multiple RAW providers with focused responsibility
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AverageCalculationStage implements CalculationStage {

    private final RateCacheService rateCacheService;
    private final KafkaPublishingService kafkaPublishingService;
    private final ApplicationProperties applicationProperties;
    private final CalculationStrategyFactory calculationStrategyFactory;
    private final CalculationInputUtils calculationInputUtils; 

    @Override
    public void execute(ExecutionContext context) {
        String stageName = "AverageCalculation";
        
        try {
            context.recordStageStart(stageName);
            
            String pipelineId = context.getPipelineId();
            BaseRateDto triggeringRate = context.getTriggeringRate();
            
            log.debug("✅ Stage 2 [{}]: Processing AVG rules for triggering rate: {}", 
                    pipelineId, triggeringRate.getSymbol());

            List<CalculationRuleDto> avgRules = findAffectedAverageRules(triggeringRate);
            
            if (avgRules.isEmpty()) {
                log.debug("No AVG calculation rules affected by: {}", triggeringRate.getSymbol());
                context.recordStageEnd(stageName);
                return;
            }

            int processedCount = 0;
            int skippedCount = 0;

            for (CalculationRuleDto rule : avgRules) {
                try {
                    log.debug("Processing AVG rule: {} (sources: {})", 
                            rule.getOutputSymbol(), rule.getRawSources());

                    // Collect required RAW inputs from cache using multiple providers
                    Map<String, BaseRateDto> rawInputs = collectRawInputsForRule(rule);

                    // Add ALL retrieved input rates to snapshot
                    context.addAllRatesToSnapshot(rawInputs.values());
                    log.debug("Added {} raw inputs to snapshot for rule: {}", 
                            rawInputs.size(), rule.getOutputSymbol());

                    //  Check if we have enough inputs for meaningful average
                    if (rawInputs.isEmpty()) {
                        context.addStageError(stageName, 
                                "No raw inputs found for " + rule.getOutputSymbol());
                        skippedCount++;
                        continue;
                    }

                    //  Get strategy from factory
                    CalculationStrategy strategy = calculationStrategyFactory.getStrategyForRule(rule);
                    if (strategy == null) {
                        context.addStageError(stageName, 
                                "No strategy found for AVG rule: " + rule.getOutputSymbol());
                        skippedCount++;
                        continue;
                    }

                    //  Calculate AVG using strategy
                    Optional<BaseRateDto> calculatedAvg = strategy.calculate(rule, rawInputs);
                    
                    if (calculatedAvg.isPresent()) {
                        BaseRateDto avgRate = calculatedAvg.get();

                        avgRate.setSymbol(rule.getOutputSymbol());
                        
                        //  Cache and publish calculated result
                        rateCacheService.cacheCalculatedRate(avgRate);
                        kafkaPublishingService.publishCalculatedRate(avgRate);
                        
                        //Add calculated result to snapshot
                        context.addRateToSnapshot(avgRate);
                        
                        processedCount++;
                        log.info("✅ AVG calculated [{}]: {} (symbol: {}) from {} inputs - ADDED TO SNAPSHOT", 
                                pipelineId, rule.getOutputSymbol(), avgRate.getSymbol(), rawInputs.size());
                        
                    } else {
                        context.addStageError(stageName, 
                                "AVG calculation returned empty for: " + rule.getOutputSymbol());
                        skippedCount++;
                    }
                    
                } catch (Exception e) {
                    log.warn("Error in AVG calculation [{}]: {} - {}", 
                            pipelineId, rule.getOutputSymbol(), e.getMessage());
                    context.addStageError(stageName, 
                            "Error calculating " + rule.getOutputSymbol() + ": " + e.getMessage());
                    skippedCount++;
                }
            }

            context.recordStageEnd(stageName);
            
            String message = String.format("AVG: %d calculated, %d skipped", processedCount, skippedCount);
            context.addStageResult(message);
            
            log.info("✅ Stage 2 [{}]: {} completed", pipelineId, message);

        } catch (Exception e) {
            context.addStageError(stageName, "Stage failed: " + e.getMessage());
            context.recordStageEnd(stageName);
            log.error("❌ Stage 2 [{}]: Average calculation stage failed", context.getPipelineId(), e);
        }
    }

    private List<CalculationRuleDto> findAffectedAverageRules(BaseRateDto triggeringRate) {
        List<CalculationRuleDto> allRules = applicationProperties.getCalculationRules();
        if (allRules == null) {
            return List.of();
        }
        
        return allRules.stream()
                .filter(rule -> CalculationRuleType.AVG.equals(rule.getTypeEnum()))
                .filter(rule -> isRuleAffectedByRate(rule, triggeringRate))
                .toList();
    }

    private boolean isRuleAffectedByRate(CalculationRuleDto rule, BaseRateDto triggeringRate) {
        if (rule.getRawSources() == null) {
            return false;
        }

        String triggeringSymbol = triggeringRate.getSymbol();
        return rule.getRawSources().stream()
                .anyMatch(source -> source.contains(triggeringSymbol) || 
                                   triggeringSymbol.contains(source));
    }

private Map<String, BaseRateDto> collectRawInputsForRule(CalculationRuleDto rule) {

    return calculationInputUtils.collectRawInputs(rule);
}
    @Override
    public String getStageName() {
        return "AverageCalculation";
    }
}

package com.toyota.mainapp.calculator.pipeline.stage;

import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.calculator.engine.CalculationStrategyFactory;
import com.toyota.mainapp.calculator.pipeline.ExecutionContext;
import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.config.ApplicationProperties;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.RateType;
import com.toyota.mainapp.kafka.KafkaPublishingService;
import com.toyota.mainapp.util.CalculationInputUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ✅ MODERNIZED: Cross rate calculation stage with strategy factory integration
 * Handles CROSS rate calculations using config-driven strategies
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CrossRateCalculationStage implements CalculationStage {

    private final ApplicationProperties applicationProperties;
    private final CalculationStrategyFactory calculationStrategyFactory; // ✅ FIXED: Use calculationStrategyFactory
    private final RateCacheService rateCacheService;
    private final KafkaPublishingService kafkaPublishingService;
    private final CalculationInputUtils calculationInputUtils;

    @Override
    public void execute(ExecutionContext context) {
        String pipelineId = context.getPipelineId();
        log.debug("🔄 CrossRateCalculationStage started for pipeline: {}", pipelineId);
        
        try {
            // ✅ FIXED: Get rules from ApplicationProperties configuration
            List<CalculationRuleDto> crossRules = applicationProperties.getCalculationRules().stream()
                    .filter(rule -> "CROSS".equals(rule.getType()))
                    .collect(Collectors.toList());

            if (crossRules.isEmpty()) {
                log.debug("No cross rate calculation rules found for pipeline: {}", pipelineId);
                return;
            }

            log.debug("Processing {} cross rate rules for pipeline: {}", crossRules.size(), pipelineId);

            // ✅ ENHANCED: Per-rule processing with error isolation
            for (CalculationRuleDto rule : crossRules) {
                try {
                    processCrossRateRule(rule, context);
                } catch (Exception e) {
                    log.warn("❌ Cross rate rule failed: {} in pipeline: {} - Error: {}", 
                            rule.getOutputSymbol(), pipelineId, e.getMessage());
                    
                    // ✅ ERROR TRACKING: Record rule-level error but continue with other rules
                    context.addStageError("CrossRateCalculationStage", 
                            String.format("Rule %s failed: %s", rule.getOutputSymbol(), e.getMessage()));
                    
                    // Check if we should continue based on configuration
                    if (!shouldContinueOnError(context)) {
                        log.warn("⚠️ Maximum stage errors reached for pipeline: {}", pipelineId);
                        break;
                    }
                }
            }

            log.debug("✅ CrossRateCalculationStage completed for pipeline: {}", pipelineId);
            
        } catch (Exception e) {
            log.error("❌ CrossRateCalculationStage failed for pipeline: {}", pipelineId, e);
            context.addStageError("CrossRateCalculationStage", 
                    String.format("Stage execution failed: %s", e.getMessage()));
        }
    }

    /**
     * ✅ ENHANCED: Process single cross rate rule with comprehensive dependency collection
     */
    private void processCrossRateRule(CalculationRuleDto rule, ExecutionContext context) {
        String outputSymbol = rule.getOutputSymbol();
        String pipelineId = context.getPipelineId();
        
        log.debug("Processing cross rate rule: {} for pipeline: {}", outputSymbol, pipelineId);

        // ✅ COLLECT RAW DEPENDENCIES: Gather required raw rates
        Map<String, BaseRateDto> rawInputs = calculationInputUtils.collectInputRates(rule, context);
        
        // ✅ COLLECT CALCULATED DEPENDENCIES: Gather required calculated rates (AVG)
        Map<String, BaseRateDto> calculatedInputs = calculationInputUtils.collectCalculatedInputRates(rule, context);
        
        // ✅ SNAPSHOT COLLECTION: Add all dependencies to snapshot for transparency
        context.addAllRatesToSnapshot(rawInputs.values());
        context.addAllRatesToSnapshot(calculatedInputs.values());
        
        log.debug("Added {} raw + {} calculated dependency rates to snapshot for rule: {}", 
                rawInputs.size(), calculatedInputs.size(), outputSymbol);

        // ✅ VALIDATION: Check if we have sufficient dependencies
        if (rawInputs.isEmpty() && calculatedInputs.isEmpty()) {
            log.warn("⚠️ No dependency rates available for cross rate rule: {} in pipeline: {}", 
                    outputSymbol, pipelineId);
            return;
        }

        // ✅ STRATEGY EXECUTION: Calculate cross rate using strategy factory
        CalculationStrategy strategy = calculationStrategyFactory.getStrategy(rule.getStrategyType());
        if (strategy == null) {
            log.error("❌ No strategy found for type: {} in rule: {}", rule.getStrategyType(), outputSymbol);
            context.addStageError("CrossRateCalculationStage", 
                    String.format("No strategy found for type: %s", rule.getStrategyType()));
            return;
        }
        
        // ✅ COMBINE INPUTS: Merge raw and calculated inputs for strategy call
        Map<String, BaseRateDto> allInputs = new HashMap<>(rawInputs);
        allInputs.putAll(calculatedInputs);
        
        Optional<BaseRateDto> calculatedRateOpt = strategy.calculate(rule, allInputs);

        if (calculatedRateOpt.isPresent()) {
            BaseRateDto calculatedRate = calculatedRateOpt.get();
            
            // ✅ CACHE: Store calculated cross rate
            rateCacheService.cacheCalculatedRate(calculatedRate);
            
            // ✅ KAFKA: Publish to individual JSON topic
            kafkaPublishingService.publishCalculatedRate(calculatedRate);
            
            // ✅ SNAPSHOT: Add calculated output to snapshot
            context.addRateToSnapshot(calculatedRate);
            context.addCalculatedRate(calculatedRate); // For backward compatibility
            
            log.info("✅ Cross rate calculated and added to snapshot: {} = {} bid, {} ask (pipeline: {})", 
                    outputSymbol, calculatedRate.getBid(), calculatedRate.getAsk(), pipelineId);
        } else {
            log.warn("⚠️ Cross rate calculation returned empty result for: {} in pipeline: {}", 
                    outputSymbol, pipelineId);
            context.addStageError("CrossRateCalculationStage", 
                    String.format("No result for rule %s", outputSymbol));
        }
    }

    /**
     * ✅ CONFIG-DRIVEN: Check if pipeline should continue on errors
     */
    private boolean shouldContinueOnError(ExecutionContext context) {
        if (!applicationProperties.getPipeline().getErrorHandling().isContinueOnStageFailure()) {
            return false;
        }
        
        int maxErrors = applicationProperties.getPipeline().getErrorHandling().getMaxStageErrors();
        return context.getStageErrorCount() < maxErrors;
    }
}

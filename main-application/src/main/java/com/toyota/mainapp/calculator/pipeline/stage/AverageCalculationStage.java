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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ✅ MODERNIZED: Average calculation stage with strategy factory integration
 * Uses ApplicationProperties and CalculationStrategyFactory for config-driven processing
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AverageCalculationStage implements CalculationStage {

    private final ApplicationProperties applicationProperties;
    private final CalculationStrategyFactory strategyFactory;
    private final RateCacheService rateCacheService;
    private final KafkaPublishingService kafkaPublishingService;
    private final CalculationInputUtils calculationInputUtils;

    @Override
    public void execute(ExecutionContext context) {
        log.debug("🔄 AverageCalculationStage started for pipeline: {}", context.getPipelineId());

        try {
            // ✅ FIXED: Use getType() for rule filtering
            List<CalculationRuleDto> avgRules = applicationProperties.getCalculationRules().stream()
                    .filter(rule -> "AVG".equalsIgnoreCase(rule.getType()))  // ✅ FIXED: Use getType()
                    .toList();

            if (avgRules.isEmpty()) {
                log.debug("No AVG rules found for averageCalculationStrategy, skipping average calculations");
                return;
            }

            log.debug("Processing {} AVG rules", avgRules.size());

            for (CalculationRuleDto rule : avgRules) {
                try {
                    processAverageRule(rule, context);
                } catch (Exception e) {
                    log.error("❌ Failed to process AVG rule: {}", rule.getOutputSymbol(), e);
                    // Continue with other rules
                }
            }

            log.debug("✅ AverageCalculationStage completed for pipeline: {}", context.getPipelineId());
            
        } catch (Exception e) {
            log.error("❌ AverageCalculationStage failed for pipeline: {}", context.getPipelineId(), e);
        }
    }

    /**
     * ✅ STRATEGY FACTORY: Process individual average rule
     */
    private void processAverageRule(CalculationRuleDto rule, ExecutionContext context) throws Exception {
        log.debug("Processing AVG rule: {} with strategy: {}", rule.getOutputSymbol(), rule.getStrategyType());

        // ✅ INPUT COLLECTION: Gather required input rates
        Map<String, BaseRateDto> inputRates = calculationInputUtils.collectInputRates(
                rule, context, rateCacheService);

        if (inputRates.isEmpty()) {
            log.warn("⚠️ No input rates available for AVG rule: {}", rule.getOutputSymbol());
            return;
        }

        log.debug("Collected {} input rates for rule: {}", inputRates.size(), rule.getOutputSymbol());

        // ✅ STRATEGY FACTORY: Get strategy dynamically
        CalculationStrategy strategy = strategyFactory.getStrategyForRule(rule);
        if (strategy == null) {
            log.error("❌ No strategy found for rule: {} with type: {}", 
                    rule.getOutputSymbol(), rule.getStrategyType());
            return;
        }

        log.debug("Using strategy: {} for rule: {}", strategy.getStrategyName(), rule.getOutputSymbol());

        // ✅ CALCULATION: Execute strategy
        Optional<BaseRateDto> result = strategy.calculate(rule, inputRates);

        if (result.isPresent()) {
            BaseRateDto avgRate = result.get();
            
            // ✅ ENRICH: Set calculation metadata
            avgRate.setRateType(RateType.CALCULATED);
            avgRate.setTimestamp(System.currentTimeMillis());
            avgRate.setProviderName("AverageCalculator");
            
            // ✅ CACHE: Store calculated rate
            rateCacheService.cacheCalculatedRate(avgRate);
            
            // ✅ KAFKA: Publish to individual JSON topic
            kafkaPublishingService.publishCalculatedRate(avgRate);
            
            // ✅ CONTEXT: Add to execution context for next stages
            context.addCalculatedRate(avgRate);
            
            log.info("✅ AVG calculation completed: {} = {} bid, {} ask", 
                    avgRate.getSymbol(), avgRate.getBid(), avgRate.getAsk());
                    
        } else {
            log.warn("⚠️ Strategy returned empty result for rule: {}", rule.getOutputSymbol());
        }
    }
}

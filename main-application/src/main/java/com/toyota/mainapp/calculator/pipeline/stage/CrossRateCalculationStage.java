package com.toyota.mainapp.calculator.pipeline.stage;

import com.toyota.mainapp.calculator.pipeline.ExecutionContext;
import com.toyota.mainapp.cache.RateCacheService;
import com.toyota.mainapp.config.ApplicationProperties;
import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.config.CalculationRuleType;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.kafka.KafkaPublishingService;
import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.calculator.engine.CalculationStrategyFactory;
import com.toyota.mainapp.util.SymbolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ✅ SIMPLIFIED: Cross rate calculation stage with consistent key handling
 * Stage 3: Calculate cross rates from ExecutionContext snapshot data with fallback to cache
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CrossRateCalculationStage implements CalculationStage {

    private final RateCacheService rateCacheService;
    private final KafkaPublishingService kafkaPublishingService;
    private final ApplicationProperties applicationProperties;
    private final CalculationStrategyFactory calculationStrategyFactory;

    @Override
    public void execute(ExecutionContext context) {
        String stageName = "CrossRateCalculation";

        try {
            context.recordStageStart(stageName);

            String pipelineId = context.getPipelineId();
            log.debug("✅ Stage 3 [{}]: Processing CROSS rules", pipelineId);

            // ✅ SIMPLIFIED: Get all CROSS type rules
            List<CalculationRuleDto> crossRules = findCrossRules();

            if (crossRules.isEmpty()) {
                log.debug("No CROSS calculation rules configured");
                context.recordStageEnd(stageName);
                return;
            }

            int processedCount = 0;
            int skippedCount = 0;

            for (CalculationRuleDto rule : crossRules) {
                try {
                    log.debug("Processing CROSS rule: {}", rule.getOutputSymbol());

                    // ✅ SIMPLIFIED: Get required inputs from snapshot with cache fallback
                    Map<String, BaseRateDto> inputRates = getInputsForCrossRate(rule, context);

                    // ✅ SIMPLIFIED: All required inputs available? Calculate. Otherwise skip.
                    if (hasAllRequiredInputs(rule, inputRates)) {

                        CalculationStrategy strategy = calculationStrategyFactory.getStrategyForRule(rule);
                        if (strategy != null) {

                            Optional<BaseRateDto> calculatedCross = strategy.calculate(rule, inputRates);

                            if (calculatedCross.isPresent()) {
                                BaseRateDto crossRate = calculatedCross.get();

                                // Cache, publish, and add to snapshot
                                rateCacheService.cacheCalculatedRate(crossRate);
                                kafkaPublishingService.publishCalculatedRate(crossRate);
                                context.addRateToSnapshot(crossRate);
                                context.addCalculatedRate(crossRate); // Legacy support

                                processedCount++;
                                log.debug("✅ CROSS calculated [{}]: {}", pipelineId, crossRate.getSymbol());
                            } else {
                                log.debug("CROSS calculation returned empty for: {}", rule.getOutputSymbol());
                                skippedCount++;
                            }
                        } else {
                            log.warn("No strategy found for CROSS rule: {}", rule.getOutputSymbol());
                            skippedCount++;
                        }
                    } else {
                        log.debug("CROSS rule skipped - missing inputs: {}", rule.getOutputSymbol());
                        skippedCount++;
                    }

                } catch (Exception e) {
                    log.warn("Error in CROSS calculation [{}]: {} - {}",
                            pipelineId, rule.getOutputSymbol(), e.getMessage());
                    skippedCount++;
                }
            }

            context.recordStageEnd(stageName);

            String message = String.format("CROSS: %d calculated, %d skipped", processedCount, skippedCount);
            context.addStageResult(message);

            log.info("✅ Stage 3 [{}]: {} completed", pipelineId, message);

        } catch (Exception e) {
            context.addStageError(stageName, "Stage failed: " + e.getMessage());
            context.recordStageEnd(stageName);
            log.error("❌ Stage 3 [{}]: Cross rate calculation stage failed", context.getPipelineId(), e);
        }
    }

    /**
     * ✅ SIMPLIFIED: Get all CROSS type rules from configuration
     */
    private List<CalculationRuleDto> findCrossRules() {
        return applicationProperties.getCalculationRules().stream()
                .filter(rule -> CalculationRuleType.CROSS.equals(rule.getTypeEnum()))
                .toList();
    }

    /**
     * ✅ SIMPLIFIED: Get inputs for cross rate with snapshot priority and cache fallback
     */
    private Map<String, BaseRateDto> getInputsForCrossRate(CalculationRuleDto rule, ExecutionContext context) {
        Map<String, BaseRateDto> inputs = new HashMap<>();
        List<String> requiredRates = rule.getRequiredCalculatedRates();
        
        if (requiredRates == null || requiredRates.isEmpty()) {
            log.warn("❌ No requiredCalculatedRates specified for CROSS rule: {}", rule.getOutputSymbol());
            return inputs;
        }

        // Build a lookup map from snapshot for efficient access
        Map<String, BaseRateDto> snapshotMap = new HashMap<>();
        for (BaseRateDto rate : context.getSnapshotRates()) {
            String key = SymbolUtils.generateSnapshotKey(rate);
            snapshotMap.put(key, rate);
            snapshotMap.put(rate.getSymbol(), rate); // Also add direct symbol access
        }
        
        log.debug("Snapshot contains {} rates", snapshotMap.size());

        for (String requiredSymbol : requiredRates) {
            // Step 1: Try to find in snapshot first
            BaseRateDto rate = findRateInSnapshot(requiredSymbol, snapshotMap);
            
            // Step 2: If not in snapshot, try to get from cache
            if (rate == null) {
                rate = rateCacheService.getCalculatedRate(requiredSymbol);
                
                if (rate != null) {
                    // Add cache-retrieved rate to snapshot for future use
                    context.addRateToSnapshot(rate);
                    log.debug("✅ Added cache-retrieved rate to snapshot: {}", requiredSymbol);
                }
            }
            
            // Step 3: If found either in snapshot or cache, add to inputs
            if (rate != null) {
                inputs.put(requiredSymbol, rate);
                log.debug("✅ Found required input: {} (source: {})", 
                    requiredSymbol, rate == snapshotMap.get(rate.getSymbol()) ? "snapshot" : "cache");
            } else {
                log.warn("❌ Required input not found for {}: {}", rule.getOutputSymbol(), requiredSymbol);
            }
        }

        log.info("🔍 Cross-rate input collection for {}: found {}/{} required inputs", 
                rule.getOutputSymbol(), inputs.size(), requiredRates.size());

        return inputs;
    }
    
    /**
     * ✅ NEW: Find rate in snapshot using standardized key approach
     */
    private BaseRateDto findRateInSnapshot(String symbol, Map<String, BaseRateDto> snapshotMap) {
        // Try standard formats (in priority order)
        String calcKey = "CALC_" + symbol;
        String directKey = symbol;
        String normalizedKey = SymbolUtils.normalizeSymbol(symbol);
        
        // Check for the rate using various key formats
        BaseRateDto rate = snapshotMap.get(calcKey);
        if (rate != null) return rate;
        
        rate = snapshotMap.get(directKey);
        if (rate != null) return rate;
        
        rate = snapshotMap.get(normalizedKey);
        if (rate != null) return rate;
        
        // Not found in snapshot
        return null;
    }

    /**
     * ✅ SIMPLIFIED: Input validation with clear logging
     */
    private boolean hasAllRequiredInputs(CalculationRuleDto rule, Map<String, BaseRateDto> availableInputs) {
        List<String> requiredInputs = rule.getRequiredCalculatedRates();

        if (requiredInputs == null || requiredInputs.isEmpty()) {
            log.warn("❌ No required inputs specified for rule: {}", rule.getOutputSymbol());
            return false;
        }

        for (String required : requiredInputs) {
            if (!availableInputs.containsKey(required)) {
                log.warn("❌ Missing required input '{}' for rule: {}", 
                        required, rule.getOutputSymbol());
                return false;
            }
        }

        log.info("✅ All {} required inputs available for rule: {}", requiredInputs.size(), rule.getOutputSymbol());
        return true;
    }

    @Override
    public String getStageName() {
        return "CrossRateCalculation";
    }
}

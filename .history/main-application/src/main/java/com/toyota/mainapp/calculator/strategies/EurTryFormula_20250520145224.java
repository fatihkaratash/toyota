package com.toyota.mainapp.calculator.strategies;

import com.toyota.mainapp.calculator.CalculationStrategy;
import com.toyota.mainapp.model.CalculatedRate;
import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.model.RateFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calculation strategy for the EUR/TRY cross rate.
 * Formula: EURTRY = USDTRY_mid * EURUSD_avg
 */
@Component
public class EurTryFormula implements CalculationStrategy {
    private static final Logger logger = LoggerFactory.getLogger(EurTryFormula.class);
    
    private static final String STRATEGY_ID = "EURTRY";
    private static final List<String> REQUIRED_SOURCE_SYMBOLS = Arrays.asList(
            "PF1_USDTRY", "PF2_USDTRY", "PF1_EURUSD", "PF2_EURUSD");
    
    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    @Override
    public String getStrategyId() {
        return STRATEGY_ID;
    }
    
    @Override
    public List<String> getRequiredSourceSymbols() {
        return REQUIRED_SOURCE_SYMBOLS;
    }
    
    @Override
    public CalculatedRate calculate(String targetSymbol, Map<String, Rate> sourceRates) {
        if (!targetSymbol.equals(STRATEGY_ID)) {
            logger.warn("Target symbol {} does not match strategy ID {}", targetSymbol, STRATEGY_ID);
            return null;
        }
        
        // Check if we have all required rates
        for (String requiredSymbol : REQUIRED_SOURCE_SYMBOLS) {
            if (!sourceRates.containsKey(requiredSymbol)) {
                logger.warn("Missing required source rate: {}", requiredSymbol);
                return null;
            }
        }
        
        try {
            // Extract required rates
            Rate pf1UsdTry = sourceRates.get("PF1_USDTRY");
            Rate pf2UsdTry = sourceRates.get("PF2_USDTRY");
            Rate pf1EurUsd = sourceRates.get("PF1_EURUSD");
            Rate pf2EurUsd = sourceRates.get("PF2_EURUSD");
            
            // Validate rate fields
            if (pf1UsdTry.getFields() == null || pf2UsdTry.getFields() == null || 
                pf1EurUsd.getFields() == null || pf2EurUsd.getFields() == null) {
                logger.warn("One or more source rates have null fields");
                return null;
            }
            
            // Step 1: Calculate USDTRY average
            BigDecimal usdTryBidAvg = pf1UsdTry.getFields().getBid()
                    .add(pf2UsdTry.getFields().getBid())
                    .divide(BigDecimal.valueOf(2), SCALE, ROUNDING_MODE);
            
            BigDecimal usdTryAskAvg = pf1UsdTry.getFields().getAsk()
                    .add(pf2UsdTry.getFields().getAsk())
                    .divide(BigDecimal.valueOf(2), SCALE, ROUNDING_MODE);
            
            // Step 2: Calculate USD mid rate
            BigDecimal usdMid = usdTryBidAvg.add(usdTryAskAvg)
                    .divide(BigDecimal.valueOf(2), SCALE, ROUNDING_MODE);
            
            // Step 3: Calculate EURUSD average
            BigDecimal eurUsdBidAvg = pf1EurUsd.getFields().getBid()
                    .add(pf2EurUsd.getFields().getBid())
                    .divide(BigDecimal.valueOf(2), SCALE, ROUNDING_MODE);
            
            BigDecimal eurUsdAskAvg = pf1EurUsd.getFields().getAsk()
                    .add(pf2EurUsd.getFields().getAsk())
                    .divide(BigDecimal.valueOf(2), SCALE, ROUNDING_MODE);
            
            // Step 4: Calculate EURTRY
            BigDecimal eurTryBid = usdMid.multiply(eurUsdBidAvg)
                    .setScale(SCALE, ROUNDING_MODE);
            
            BigDecimal eurTryAsk = usdMid.multiply(eurUsdAskAvg)
                    .setScale(SCALE, ROUNDING_MODE);
            
            // Create new rate fields with the latest timestamp from source rates
            Instant latestTimestamp = getLatestTimestamp(pf1UsdTry, pf2UsdTry, pf1EurUsd, pf2EurUsd);
            RateFields fields = new RateFields(eurTryBid, eurTryAsk, latestTimestamp);
            
            // Create calculation details
            StringBuilder details = new StringBuilder();
            details.append("USDTRY_bid_avg = (")
                   .append(pf1UsdTry.getFields().getBid()).append(" + ")
                   .append(pf2UsdTry.getFields().getBid()).append(") / 2 = ")
                   .append(usdTryBidAvg).append("\n");
            
            details.append("USDTRY_ask_avg = (")
                   .append(pf1UsdTry.getFields().getAsk()).append(" + ")
                   .append(pf2UsdTry.getFields().getAsk()).append(") / 2 = ")
                   .append(usdTryAskAvg).append("\n");
                   
            details.append("USD_mid = (")
                   .append(usdTryBidAvg).append(" + ")
                   .append(usdTryAskAvg).append(") / 2 = ")
                   .append(usdMid).append("\n");
                   
            details.append("EURUSD_bid_avg = (")
                   .append(pf1EurUsd.getFields().getBid()).append(" + ")
                   .append(pf2EurUsd.getFields().getBid()).append(") / 2 = ")
                   .append(eurUsdBidAvg).append("\n");
                   
            details.append("EURUSD_ask_avg = (")
                   .append(pf1EurUsd.getFields().getAsk()).append(" + ")
                   .append(pf2EurUsd.getFields().getAsk()).append(") / 2 = ")
                   .append(eurUsdAskAvg).append("\n");
                   
            details.append("EURTRY_bid = ")
                   .append(usdMid).append(" * ")
                   .append(eurUsdBidAvg).append(" = ")
                   .append(eurTryBid).append("\n");
                   
            details.append("EURTRY_ask = ")
                   .append(usdMid).append(" * ")
                   .append(eurUsdAskAvg).append(" = ")
                   .append(eurTryAsk);
            
            // Create source rate IDs map
            Map<String, String> sourceRateIds = new HashMap<>();
            sourceRateIds.put("PF1_USDTRY", pf1UsdTry.getPlatformName());
            sourceRateIds.put("PF2_USDTRY", pf2UsdTry.getPlatformName());
            sourceRateIds.put("PF1_EURUSD", pf1EurUsd.getPlatformName());
            sourceRateIds.put("PF2_EURUSD", pf2EurUsd.getPlatformName());
            
            return new CalculatedRate(targetSymbol, fields, STRATEGY_ID, sourceRateIds, details.toString());
            
        } catch (ArithmeticException e) {
            logger.error("Arithmetic error calculating {}: {}", targetSymbol, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Error calculating {}: {}", targetSymbol, e.getMessage(), e);
            return null;
        }
    }
    
    private Instant getLatestTimestamp(Rate... rates) {
        return Arrays.stream(rates)
                .map(rate -> rate.getFields().getTimestamp())
                .max(Instant::compareTo)
                .orElse(Instant.now());
    }
}

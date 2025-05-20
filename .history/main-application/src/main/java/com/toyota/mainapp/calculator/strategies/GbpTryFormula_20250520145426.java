package com.toyota.mainapp.calculator.strategies;

import com.toyota.mainapp.calculator.CalculationStrategy;
import com.toyota.mainapp.model.CalculatedRate;
import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.model.RateFields;
import com.toyota.mainapp.util.LoggingHelper;
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

/**
 * Calculation strategy for the GBP/TRY cross rate.
 * Formula: GBPTRY = USDTRY_mid * GBPUSD_avg
 */
@Component
public class GbpTryFormula implements CalculationStrategy {
    private static final Logger logger = LoggerFactory.getLogger(GbpTryFormula.class);
    
    private static final String STRATEGY_ID = "GBPTRY";
    private static final List<String> REQUIRED_SOURCE_SYMBOLS = Arrays.asList(
            "PF1_USDTRY", "PF2_USDTRY", "PF1_GBPUSD", "PF2_GBPUSD");
    
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
            LoggingHelper.logWarning(logger, "GbpTryFormula", 
                    "Hedef sembol " + targetSymbol + " strateji ID'sine uymuyor: " + STRATEGY_ID);
            return null;
        }
        
        // Check if we have all required rates
        for (String requiredSymbol : REQUIRED_SOURCE_SYMBOLS) {
            if (!sourceRates.containsKey(requiredSymbol)) {
                LoggingHelper.logWarning(logger, "GbpTryFormula", 
                        "Gerekli kaynak kur eksik: " + requiredSymbol);
                return null;
            }
        }
        
        try {
            // Extract required rates
            Rate pf1UsdTry = sourceRates.get("PF1_USDTRY");
            Rate pf2UsdTry = sourceRates.get("PF2_USDTRY");
            Rate pf1GbpUsd = sourceRates.get("PF1_GBPUSD");
            Rate pf2GbpUsd = sourceRates.get("PF2_GBPUSD");
            
            // Validate rate fields
            if (pf1UsdTry.getFields() == null || pf2UsdTry.getFields() == null || 
                pf1GbpUsd.getFields() == null || pf2GbpUsd.getFields() == null) {
                LoggingHelper.logWarning(logger, "GbpTryFormula", 
                        "Bir veya daha fazla kaynak kurun alanları null");
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
            
            // Step 3: Calculate GBPUSD average
            BigDecimal gbpUsdBidAvg = pf1GbpUsd.getFields().getBid()
                    .add(pf2GbpUsd.getFields().getBid())
                    .divide(BigDecimal.valueOf(2), SCALE, ROUNDING_MODE);
            
            BigDecimal gbpUsdAskAvg = pf1GbpUsd.getFields().getAsk()
                    .add(pf2GbpUsd.getFields().getAsk())
                    .divide(BigDecimal.valueOf(2), SCALE, ROUNDING_MODE);
            
            // Step 4: Calculate GBPTRY
            BigDecimal gbpTryBid = usdMid.multiply(gbpUsdBidAvg)
                    .setScale(SCALE, ROUNDING_MODE);
            
            BigDecimal gbpTryAsk = usdMid.multiply(gbpUsdAskAvg)
                    .setScale(SCALE, ROUNDING_MODE);
            
            // Create new rate fields with the latest timestamp from source rates
            Instant latestTimestamp = getLatestTimestamp(pf1UsdTry, pf2UsdTry, pf1GbpUsd, pf2GbpUsd);
            RateFields fields = new RateFields(gbpTryBid, gbpTryAsk, latestTimestamp);
            
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
                   
            details.append("GBPUSD_bid_avg = (")
                   .append(pf1GbpUsd.getFields().getBid()).append(" + ")
                   .append(pf2GbpUsd.getFields().getBid()).append(") / 2 = ")
                   .append(gbpUsdBidAvg).append("\n");
                   
            details.append("GBPUSD_ask_avg = (")
                   .append(pf1GbpUsd.getFields().getAsk()).append(" + ")
                   .append(pf2GbpUsd.getFields().getAsk()).append(") / 2 = ")
                   .append(gbpUsdAskAvg).append("\n");
                   
            details.append("GBPTRY_bid = ")
                   .append(usdMid).append(" * ")
                   .append(gbpUsdBidAvg).append(" = ")
                   .append(gbpTryBid).append("\n");
                   
            details.append("GBPTRY_ask = ")
                   .append(usdMid).append(" * ")
                   .append(gbpUsdAskAvg).append(" = ")
                   .append(gbpTryAsk);
            
            // Create source rate IDs map
            Map<String, String> sourceRateIds = new HashMap<>();
            sourceRateIds.put("PF1_USDTRY", pf1UsdTry.getPlatformName());
            sourceRateIds.put("PF2_USDTRY", pf2UsdTry.getPlatformName());
            sourceRateIds.put("PF1_GBPUSD", pf1GbpUsd.getPlatformName());
            sourceRateIds.put("PF2_GBPUSD", pf2GbpUsd.getPlatformName());
            
            LoggingHelper.logDataProcessing(logger, "Hesaplanmış Kur", STRATEGY_ID, "Başarıyla hesaplandı");
            return new CalculatedRate(targetSymbol, fields, STRATEGY_ID, sourceRateIds, details.toString());
            
        } catch (ArithmeticException e) {
            LoggingHelper.logError(logger, "GbpTryFormula", 
                    targetSymbol + " hesaplanırken aritmetik hata", e);
            return null;
        } catch (Exception e) {
            LoggingHelper.logError(logger, "GbpTryFormula", 
                    targetSymbol + " hesaplanırken hata", e);
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

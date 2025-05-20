package com.toyota.mainapp.calculator.formula;

import com.toyota.mainapp.calculator.CalculationStrategy;
import com.toyota.mainapp.model.CalculatedRate;
import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.model.RateFields;
import com.toyota.mainapp.model.RateStatus;
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
import java.util.Objects;

/**
 * Calculation strategy for EUR/TRY cross rate.
 * Formula: EUR/TRY = USD/TRY mid-rate * EUR/USD average bid/ask
 */
@Component
public class EurTryFormula implements CalculationStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(EurTryFormula.class);
    
    private static final String DERIVED_SYMBOL = "EURTRY";
    private static final List<String> REQUIRED_SYMBOLS = Arrays.asList(
        "PF1_USDTRY", "PF2_USDTRY", "PF1_EURUSD", "PF2_EURUSD"
    );
    
    private static final int SCALE = 5;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    @Override
    public CalculatedRate calculate(String targetSymbol, Map<String, Rate> sourceRates) {
        if (!DERIVED_SYMBOL.equals(targetSymbol)) {
            LoggingHelper.logWarning(logger, "EurTryFormula", 
                    "Bu formül " + DERIVED_SYMBOL + " hesaplar ancak " + targetSymbol + " istendi");
            return null;
        }
        
        // Check if all required rates are available and valid
        for (String symbol : REQUIRED_SYMBOLS) {
            Rate rate = sourceRates.get(symbol);
            if (rate == null || rate.getFields() == null || 
                rate.getFields().getBid() == null || rate.getFields().getAsk() == null) {
                logger.debug("{} hesaplaması için gerekli olan {} kuru eksik veya geçersiz", DERIVED_SYMBOL, symbol);
                return null;
            }
        }
        
        try {
            // Extract rates
            Rate pf1UsdTry = Objects.requireNonNull(sourceRates.get("PF1_USDTRY"), "PF1_USDTRY kuru null olamaz");
            Rate pf2UsdTry = Objects.requireNonNull(sourceRates.get("PF2_USDTRY"), "PF2_USDTRY kuru null olamaz");
            Rate pf1EurUsd = Objects.requireNonNull(sourceRates.get("PF1_EURUSD"), "PF1_EURUSD kuru null olamaz");
            Rate pf2EurUsd = Objects.requireNonNull(sourceRates.get("PF2_EURUSD"), "PF2_EURUSD kuru null olamaz");
            
            // Calculate USD/TRY average bid
            BigDecimal usdTryBid = pf1UsdTry.getFields().getBid()
                                  .add(pf2UsdTry.getFields().getBid())
                                  .divide(BigDecimal.valueOf(2), SCALE, ROUNDING_MODE);
            
            // Calculate USD/TRY average ask
            BigDecimal usdTryAsk = pf1UsdTry.getFields().getAsk()
                                  .add(pf2UsdTry.getFields().getAsk())
                                  .divide(BigDecimal.valueOf(2), SCALE, ROUNDING_MODE);
            
            // Calculate USD/TRY mid rate
            BigDecimal usdTryMid = usdTryBid.add(usdTryAsk)
                                  .divide(BigDecimal.valueOf(2), SCALE, ROUNDING_MODE);
            
            // Calculate EUR/USD average bid
            BigDecimal eurUsdBid = pf1EurUsd.getFields().getBid()
                                  .add(pf2EurUsd.getFields().getBid())
                                  .divide(BigDecimal.valueOf(2), SCALE, ROUNDING_MODE);
            
            // Calculate EUR/USD average ask
            BigDecimal eurUsdAsk = pf1EurUsd.getFields().getAsk()
                                  .add(pf2EurUsd.getFields().getAsk())
                                  .divide(BigDecimal.valueOf(2), SCALE, ROUNDING_MODE);
            
            // Calculate EUR/TRY bid and ask
            BigDecimal eurTryBid = usdTryMid.multiply(eurUsdBid)
                                  .setScale(SCALE, ROUNDING_MODE);
            
            BigDecimal eurTryAsk = usdTryMid.multiply(eurUsdAsk)
                                  .setScale(SCALE, ROUNDING_MODE);
            
            // Find the latest timestamp among source rates
            Instant latestTimestamp = getLatestTimestamp(pf1UsdTry, pf2UsdTry, pf1EurUsd, pf2EurUsd);
            
            // Create rate fields
            RateFields fields = new RateFields(eurTryBid, eurTryAsk, latestTimestamp);
            
            // Create calculation details
            StringBuilder details = new StringBuilder();
            details.append("USDTRY_bid_avg = (")
                   .append(pf1UsdTry.getFields().getBid()).append(" + ")
                   .append(pf2UsdTry.getFields().getBid()).append(") / 2 = ")
                   .append(usdTryBid).append("\n");
            
            details.append("USDTRY_ask_avg = (")
                   .append(pf1UsdTry.getFields().getAsk()).append(" + ")
                   .append(pf2UsdTry.getFields().getAsk()).append(") / 2 = ")
                   .append(usdTryAsk).append("\n");
                   
            details.append("USD_mid = (")
                   .append(usdTryBid).append(" + ")
                   .append(usdTryAsk).append(") / 2 = ")
                   .append(usdTryMid).append("\n");
                   
            details.append("EURUSD_bid_avg = (")
                   .append(pf1EurUsd.getFields().getBid()).append(" + ")
                   .append(pf2EurUsd.getFields().getBid()).append(") / 2 = ")
                   .append(eurUsdBid).append("\n");
                   
            details.append("EURUSD_ask_avg = (")
                   .append(pf1EurUsd.getFields().getAsk()).append(" + ")
                   .append(pf2EurUsd.getFields().getAsk()).append(") / 2 = ")
                   .append(eurUsdAsk).append("\n");
                   
            details.append("EURTRY_bid = ")
                   .append(usdTryMid).append(" * ")
                   .append(eurUsdBid).append(" = ")
                   .append(eurTryBid).append("\n");
                   
            details.append("EURTRY_ask = ")
                   .append(usdTryMid).append(" * ")
                   .append(eurUsdAsk).append(" = ")
                   .append(eurTryAsk);
            
            // Create a map of source rates
            Map<String, String> sourceRateIds = new HashMap<>();
            sourceRateIds.put("PF1_USDTRY", pf1UsdTry.getPlatformName());
            sourceRateIds.put("PF2_USDTRY", pf2UsdTry.getPlatformName());
            sourceRateIds.put("PF1_EURUSD", pf1EurUsd.getPlatformName());
            sourceRateIds.put("PF2_EURUSD", pf2EurUsd.getPlatformName());
            
            // Create the calculated rate
            CalculatedRate calculatedRate = new CalculatedRate(
                DERIVED_SYMBOL,
                fields,
                DERIVED_SYMBOL,
                sourceRateIds,
                details.toString()
            );
            
            LoggingHelper.logDataProcessing(logger, "Hesaplanmış Kur", DERIVED_SYMBOL, 
                    "Alış=" + eurTryBid + ", Satış=" + eurTryAsk);
            
            return calculatedRate;
        } catch (NullPointerException e) {
            LoggingHelper.logError(logger, "EurTryFormula", 
                    DERIVED_SYMBOL + " hesaplanırken null kaynak kur hatası", e);
            return null;
        } catch (Exception e) {
            LoggingHelper.logError(logger, "EurTryFormula", 
                    DERIVED_SYMBOL + " hesaplanırken hata", e);
            return null;
        }
    }
    
    @Override
    public List<String> getRequiredSourceSymbols() {
        return REQUIRED_SYMBOLS;
    }
    
    @Override
    public String getStrategyId() {
        return DERIVED_SYMBOL;
    }
    
    private Instant getLatestTimestamp(Rate... rates) {
        return Arrays.stream(rates)
                .map(rate -> rate.getFields().getTimestamp())
                .max(Instant::compareTo)
                .orElse(Instant.now());
    }
}

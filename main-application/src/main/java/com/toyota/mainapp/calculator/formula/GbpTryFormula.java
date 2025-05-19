package com.toyota.mainapp.calculator.formula;

import com.toyota.mainapp.calculator.CalculationStrategy;
import com.toyota.mainapp.model.CalculatedRate;
import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.model.RateFields;
import com.toyota.mainapp.model.RateStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Calculation strategy for GBP/TRY cross rate.
 * Formula: GBP/TRY = USD/TRY mid-rate * GBP/USD average bid/ask
 */
public class GbpTryFormula implements CalculationStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(GbpTryFormula.class);
    
    private static final String DERIVED_SYMBOL = "GBPTRY";
    private static final List<String> REQUIRED_SYMBOLS = Arrays.asList(
        "PF1_USDTRY", "PF2_USDTRY", "PF1_GBPUSD", "PF2_GBPUSD" 
    );
    
    private static final int SCALE = 5;
    
    @Override
    public CalculatedRate calculate(String targetSymbol, Map<String, Rate> sourceRates) {
        if (!DERIVED_SYMBOL.equals(targetSymbol)) {
            logger.warn("Bu formül {} hesaplar ancak {} istendi", DERIVED_SYMBOL, targetSymbol);
            return null;
        }
        
        // Check if all required rates are available and valid
        for (String symbol : REQUIRED_SYMBOLS) {
            Rate rate = sourceRates.get(symbol);
            if (rate == null || rate.getFields() == null || rate.getFields().getBid() == null || rate.getFields().getAsk() == null) {
                logger.debug("{} hesaplaması için gerekli olan {} kuru eksik veya geçersiz", DERIVED_SYMBOL, symbol);
                return null;
            }
        }
        
        try {
            // Extract rates
            Rate pf1UsdTry = Objects.requireNonNull(sourceRates.get("PF1_USDTRY"), "PF1_USDTRY kuru null olamaz");
            Rate pf2UsdTry = Objects.requireNonNull(sourceRates.get("PF2_USDTRY"), "PF2_USDTRY kuru null olamaz");
            Rate pf1GbpUsd = Objects.requireNonNull(sourceRates.get("PF1_GBPUSD"), "PF1_GBPUSD kuru null olamaz");
            Rate pf2GbpUsd = Objects.requireNonNull(sourceRates.get("PF2_GBPUSD"), "PF2_GBPUSD kuru null olamaz");
            
            // Calculate USD/TRY average bid
            BigDecimal usdTryBid = pf1UsdTry.getFields().getBid()
                                  .add(pf2UsdTry.getFields().getBid())
                                  .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
            
            // Calculate USD/TRY average ask
            BigDecimal usdTryAsk = pf1UsdTry.getFields().getAsk()
                                  .add(pf2UsdTry.getFields().getAsk())
                                  .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
            
            // Calculate USD/TRY mid rate
            BigDecimal usdTryMid = usdTryBid.add(usdTryAsk)
                                  .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
            
            // Calculate GBP/USD average bid
            BigDecimal gbpUsdBid = pf1GbpUsd.getFields().getBid()
                                  .add(pf2GbpUsd.getFields().getBid())
                                  .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
            
            // Calculate GBP/USD average ask
            BigDecimal gbpUsdAsk = pf1GbpUsd.getFields().getAsk()
                                  .add(pf2GbpUsd.getFields().getAsk())
                                  .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
            
            // Calculate GBP/TRY bid and ask
            BigDecimal gbpTryBid = usdTryMid.multiply(gbpUsdBid)
                                  .setScale(SCALE, RoundingMode.HALF_UP);
            
            BigDecimal gbpTryAsk = usdTryMid.multiply(gbpUsdAsk)
                                  .setScale(SCALE, RoundingMode.HALF_UP);
            
            // Create rate fields
            RateFields fields = new RateFields(
                gbpTryBid,
                gbpTryAsk,
                Instant.now() // Use current time for calculated rate
            );
            
            // Get the list of source symbols
            List<String> sourceSymbols = REQUIRED_SYMBOLS;
            
            // Create the calculated rate
            CalculatedRate calculatedRate = new CalculatedRate(
                DERIVED_SYMBOL,
                fields,
                RateStatus.ACTIVE,
                getStrategyId(),
                sourceSymbols
            );
            
            logger.debug("{} hesaplandı: alış={}, satış={}", 
                        DERIVED_SYMBOL, gbpTryBid, gbpTryAsk);
            
            return calculatedRate;
        } catch (NullPointerException e) {
            logger.error("{} hesaplanırken null kaynak kur hatası: {}", DERIVED_SYMBOL, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("{} hesaplanırken hata: {}", DERIVED_SYMBOL, e.getMessage(), e);
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
}

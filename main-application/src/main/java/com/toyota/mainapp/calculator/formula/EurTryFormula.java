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
import java.util.Objects; // Added import
import java.util.stream.Collectors;

/**
 * Calculation strategy for EUR/TRY cross rate.
 * Formula: EUR/TRY = USD/TRY mid-rate * EUR/USD average bid/ask
 */
public class EurTryFormula implements CalculationStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(EurTryFormula.class);
    
    private static final String DERIVED_SYMBOL = "EURTRY";
    private static final List<String> REQUIRED_SYMBOLS = Arrays.asList(
        "PF1_USDTRY", "PF2_USDTRY", "PF1_EURUSD", "PF2_EURUSD"
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
            Rate pf1UsdTry = Objects.requireNonNull(sourceRates.get("PF1_USDTRY"));
            Rate pf2UsdTry = Objects.requireNonNull(sourceRates.get("PF2_USDTRY"));
            Rate pf1EurUsd = Objects.requireNonNull(sourceRates.get("PF1_EURUSD"));
            Rate pf2EurUsd = Objects.requireNonNull(sourceRates.get("PF2_EURUSD"));
            
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
            
            // Calculate EUR/USD average bid
            BigDecimal eurUsdBid = pf1EurUsd.getFields().getBid()
                                  .add(pf2EurUsd.getFields().getBid())
                                  .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
            
            // Calculate EUR/USD average ask
            BigDecimal eurUsdAsk = pf1EurUsd.getFields().getAsk()
                                  .add(pf2EurUsd.getFields().getAsk())
                                  .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
            
            // Calculate EUR/TRY bid and ask
            BigDecimal eurTryBid = usdTryMid.multiply(eurUsdBid)
                                  .setScale(SCALE, RoundingMode.HALF_UP);
            
            BigDecimal eurTryAsk = usdTryMid.multiply(eurUsdAsk)
                                  .setScale(SCALE, RoundingMode.HALF_UP);
            
            // Create rate fields
            RateFields fields = new RateFields(
                eurTryBid,
                eurTryAsk,
                Instant.now()
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
                        DERIVED_SYMBOL, eurTryBid, eurTryAsk);
            
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

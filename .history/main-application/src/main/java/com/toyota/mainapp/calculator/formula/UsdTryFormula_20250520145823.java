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
import java.util.stream.Collectors;

/**
 * Calculation strategy for USD/TRY.
 * This formula calculates USD/TRY by averaging rates from multiple providers.
 */
@Component
public class UsdTryFormula implements CalculationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(UsdTryFormula.class);

    private static final String DERIVED_SYMBOL = "USDTRY";
    // We now require both PF1 and PF2 USD/TRY rates to calculate an average
    private static final List<String> REQUIRED_SYMBOLS = Arrays.asList("PF1_USDTRY", "PF2_USDTRY");
    private static final int SCALE = 5;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Override
    public CalculatedRate calculate(String targetSymbol, Map<String, Rate> sourceRates) {
        if (!DERIVED_SYMBOL.equals(targetSymbol)) {
            LoggingHelper.logWarning(logger, "UsdTryFormula", 
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
            Rate pf1UsdTry = sourceRates.get("PF1_USDTRY");
            Rate pf2UsdTry = sourceRates.get("PF2_USDTRY");
            
            // Calculate USD/TRY average bid
            BigDecimal usdTryBid = pf1UsdTry.getFields().getBid()
                                  .add(pf2UsdTry.getFields().getBid())
                                  .divide(BigDecimal.valueOf(2), SCALE, ROUNDING_MODE);
            
            // Calculate USD/TRY average ask
            BigDecimal usdTryAsk = pf1UsdTry.getFields().getAsk()
                                  .add(pf2UsdTry.getFields().getAsk())
                                  .divide(BigDecimal.valueOf(2), SCALE, ROUNDING_MODE);
            
            // Find the latest timestamp among source rates
            Instant latestTimestamp = getLatestTimestamp(pf1UsdTry, pf2UsdTry);

            // Create rate fields
            RateFields fields = new RateFields(usdTryBid, usdTryAsk, latestTimestamp);

            // Log calculation details
            String calculationDetails = "USDTRY_bid = (" + pf1UsdTry.getFields().getBid() + 
                                     " + " + pf2UsdTry.getFields().getBid() + 
                                     ") / 2 = " + usdTryBid + "\n" +
                                     "USDTRY_ask = (" + pf1UsdTry.getFields().getAsk() + 
                                     " + " + pf2UsdTry.getFields().getAsk() + 
                                     ") / 2 = " + usdTryAsk;
            
            // Create a map of source rates
            Map<String, String> sourceRateIds = new HashMap<>();
            sourceRateIds.put("PF1_USDTRY", pf1UsdTry.getPlatformName());
            sourceRateIds.put("PF2_USDTRY", pf2UsdTry.getPlatformName());
            
            // Create the calculated rate
            CalculatedRate calculatedRate = new CalculatedRate(
                DERIVED_SYMBOL,
                fields,
                DERIVED_SYMBOL,
                sourceRateIds,
                calculationDetails
            );

            LoggingHelper.logDataProcessing(logger, "Hesaplanmış Kur", DERIVED_SYMBOL, 
                    "Alış=" + usdTryBid + ", Satış=" + usdTryAsk);
            
            return calculatedRate;

        } catch (Exception e) {
            LoggingHelper.logError(logger, "UsdTryFormula", 
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

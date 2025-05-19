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

public class UsdTryFormula implements CalculationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(UsdTryFormula.class);

    private static final String DERIVED_SYMBOL = "USDTRY";
    private static final List<String> REQUIRED_SYMBOLS = Arrays.asList(
            "PF1_USDTRY", "PF2_USDTRY"
    );
    private static final int SCALE = 5; // Consistent scale for calculations

    @Override
    public CalculatedRate calculate(String targetSymbol, Map<String, Rate> sourceRates) {
        if (!DERIVED_SYMBOL.equals(targetSymbol)) {
            logger.warn("Bu formül {} hesaplar ancak {} istendi", DERIVED_SYMBOL, targetSymbol);
            return null;
        }

        for (String symbol : REQUIRED_SYMBOLS) {
            Rate rate = sourceRates.get(symbol);
            if (rate == null || rate.getFields() == null || rate.getFields().getBid() == null || rate.getFields().getAsk() == null) {
                logger.debug("{} hesaplaması için gerekli olan {} kuru eksik veya geçersiz", DERIVED_SYMBOL, symbol);
                return null;
            }
        }

        try {
            Rate pf1UsdTry = Objects.requireNonNull(sourceRates.get("PF1_USDTRY"), "PF1_USDTRY kuru null olamaz");
            Rate pf2UsdTry = Objects.requireNonNull(sourceRates.get("PF2_USDTRY"), "PF2_USDTRY kuru null olamaz");

            BigDecimal avgBid = pf1UsdTry.getFields().getBid()
                    .add(pf2UsdTry.getFields().getBid())
                    .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);

            BigDecimal avgAsk = pf1UsdTry.getFields().getAsk()
                    .add(pf2UsdTry.getFields().getAsk())
                    .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);

            RateFields fields = new RateFields(avgBid, avgAsk, Instant.now());
            CalculatedRate calculatedRate = new CalculatedRate(
                    DERIVED_SYMBOL,
                    fields,
                    RateStatus.ACTIVE,
                    getStrategyId(),
                    REQUIRED_SYMBOLS // Source symbols used for this calculation
            );
            logger.debug("{} hesaplandı: alış={}, satış={}", DERIVED_SYMBOL, avgBid, avgAsk);
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
        return DERIVED_SYMBOL; // Strategy ID is the symbol it calculates
    }
}

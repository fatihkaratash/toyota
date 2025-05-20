package com.toyota.mainapp.validation.rule.impl;

import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.model.RateFields;
import com.toyota.mainapp.validation.ValidationRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BidAskRule implements ValidationRule {
    
    private static final Logger logger = LoggerFactory.getLogger(BidAskRule.class);
    
    @Value("${rate.validation.min-spread:0.0}")
    private double minSpread; // Consider using BigDecimal for precision if config values can be very precise
    
    @Value("${rate.validation.max-spread:10.0}")
    private double maxSpread; // Consider using BigDecimal
    
    @Override
    public boolean validate(Rate rate) {
        if (rate == null || rate.getFields() == null || rate.getFields().getBid() == null || rate.getFields().getAsk() == null) {
            logger.warn("Geçersiz kur verisi (null rate, fields, bid, veya ask), {} için alış/satış ilişkisi doğrulanamıyor", 
                        rate != null ? rate.getSymbol() : "bilinmeyen sembol");
            return false;
        }
        
        RateFields fields = rate.getFields();
        BigDecimal bid = fields.getBid();
        BigDecimal ask = fields.getAsk();
        String symbol = rate.getSymbol(); // Cache symbol for logging

        if (bid.compareTo(ask) >= 0) {
            logger.warn("Alış ({}) {} kuru için satıştan ({}) düşük veya eşit değil", bid, symbol, ask);
            return false;
        }
        
        // Avoid division by zero if bid is zero.
        // If bid is zero, spread percentage calculation is problematic.
        // Rule: if bid is zero, ask must be positive. Spread limits don't apply in this edge case.
        if (bid.compareTo(BigDecimal.ZERO) == 0) {
            if (ask.compareTo(BigDecimal.ZERO) > 0) {
                logger.debug("{} kuru için alış sıfır, satış pozitif. Spread limitleri atlandı.", symbol);
                return true; 
            } else {
                logger.warn("{} kuru için alış sıfır ve satış pozitif değil ({})", symbol, ask);
                return false;
            }
        }

        double spreadPercentage = calculateSpreadPercentage(bid, ask);
        
        if (spreadPercentage < minSpread) {
            logger.warn("Spread ({}%) {} kuru için izin verilen minimumdan ({}%) düşük", 
                       spreadPercentage, symbol, minSpread);
            return false;
        }
        
        if (spreadPercentage > maxSpread) {
            logger.warn("Spread ({}%) {} kuru için izin verilen maksimumdan ({}%) yüksek", 
                       spreadPercentage, symbol, maxSpread);
            return false;
        }
        
        logger.debug("{} kuru alış/satış doğrulamasını spread ({}%) ile geçti", 
                   symbol, spreadPercentage);
        return true;
    }
    
    private double calculateSpreadPercentage(BigDecimal bid, BigDecimal ask) {
        // Spread percentage = ((ask - bid) / bid) * 100
        BigDecimal spread = ask.subtract(bid);
        BigDecimal spreadPercentage = spread.divide(bid, 6, java.math.RoundingMode.HALF_UP)
                                            .multiply(BigDecimal.valueOf(100));
        return spreadPercentage.doubleValue();
    }
    
    @Override
    public String getName() {
        return "BidAskRule";
    }
    
    @Override
    public String getDescription() {
        return "Alışın satıştan düşük olduğunu ve spreadin kabul edilebilir sınırlar içinde olduğunu doğrular";
    }
}

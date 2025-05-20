package com.toyota.mainapp.validation.rule.impl;

import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.model.RateFields;
import com.toyota.mainapp.validation.ValidationRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Validation rule that ensures the bid price is less than the ask price
 * and that the spread is within acceptable limits.
 */
@Component
public class BidAskRule implements ValidationRule {
    
    private static final Logger logger = LoggerFactory.getLogger(BidAskRule.class);
    
    @Value("${rate.validation.min-spread:0.0}")
    private double minSpread;
    
    @Value("${rate.validation.max-spread:10.0}")
    private double maxSpread;
    
    @Override
    public boolean validate(Rate rate) {
        if (rate == null || rate.getFields() == null) {
            logger.warn("Kur veya kur alanları null, alış/satış ilişkisi doğrulanamıyor");
            return false;
        }
        
        RateFields fields = rate.getFields();
        BigDecimal bid = fields.getBid();
        BigDecimal ask = fields.getAsk();
        
        if (bid == null || ask == null) {
            logger.warn("{} kuru için alış veya satış null", rate.getSymbol());
            return false;
        }
        
        // Check that bid is less than ask
        if (bid.compareTo(ask) >= 0) {
            logger.warn("Alış ({}) {} kuru için satıştan ({}) düşük değil", 
                       bid, rate.getSymbol(), ask);
            return false;
        }
        
        // Calculate spread percentage
        if (bid.compareTo(BigDecimal.ZERO) == 0) { // Avoid division by zero
            logger.warn("{} kuru için alış fiyatı sıfır, spread yüzdesi hesaplanamıyor", rate.getSymbol());
            // Depending on requirements, zero bid might be invalid on its own.
            // For now, if bid is zero, we can't calculate spread percentage, so consider it a pass for this part
            // or return false if zero bid is inherently invalid. Let's assume it passes if ask > 0.
            return ask.compareTo(BigDecimal.ZERO) > 0;
        }
        double spreadPercentage = calculateSpreadPercentage(bid, ask);
        
        // Check that spread is within acceptable limits
        if (spreadPercentage < minSpread) {
            logger.warn("Spread ({}) {} kuru için izin verilen minimumdan ({}) düşük", 
                       spreadPercentage, rate.getSymbol(), minSpread);
            return false;
        }
        
        if (spreadPercentage > maxSpread) {
            logger.warn("Spread ({}) {} kuru için izin verilen maksimumdan ({}) yüksek", 
                       spreadPercentage, rate.getSymbol(), maxSpread);
            return false;
        }
        
        logger.debug("{} kuru alış/satış doğrulamasını spread ile geçti: {}", 
                   rate.getSymbol(), spreadPercentage);
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

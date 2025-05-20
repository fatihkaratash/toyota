package com.toyota.mainapp.validation.impl;

import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.validation.RateValidator;
import com.toyota.mainapp.validation.ValidationRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of the RateValidator interface.
 * Applies a list of validation rules to rates.
 */
@Service
public class DefaultRateValidator implements RateValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultRateValidator.class);
    
    private final Map<String, ValidationRule> rules = new ConcurrentHashMap<>();
    private final List<ValidationRule> rulesList = new ArrayList<>();
    
    @Autowired(required = false)
    private List<ValidationRule> autoconfiguredRules;
    
    @PostConstruct
    public void init() {
        // Add any validation rules from Spring context
        if (autoconfiguredRules != null && !autoconfiguredRules.isEmpty()) {
            autoconfiguredRules.forEach(this::addRule);
            logger.info("DefaultRateValidator {} doğrulama kuralı ile başlatıldı", autoconfiguredRules.size());
        } else {
            logger.info("DefaultRateValidator başlatıldı (doğrulama kuralı olmadan)");
        }
    }
    
    @Override
    public boolean validate(Rate rate) {
        if (rate == null) {
            logger.warn("Null kur doğrulanmaya çalışıldı");
            return false;
        }
        
        if (rulesList.isEmpty()) {
            logger.debug("Yapılandırılmış doğrulama kuralı yok, {} kuru varsayılan olarak geçerli kabul ediliyor", rate.getSymbol());
            return true;
        }
        
        for (ValidationRule rule : rulesList) {
            try {
                if (!rule.validate(rate)) {
                    logger.warn("{} kuru '{}' doğrulama kuralını geçemedi: {}", 
                               rate.getSymbol(), rule.getName(), rule.getDescription());
                    return false;
                }
            } catch (Exception e) {
                logger.error("'{}' doğrulama kuralı {} kuruna uygulanırken hata: {}", 
                            rule.getName(), rate.getSymbol(), e.getMessage(), e);
                return false; // Consider a failed rule application as a validation failure
            }
        }
        
        logger.debug("{} kuru tüm doğrulama kurallarını geçti", rate.getSymbol());
        return true;
    }
    
    @Override
    public void addRule(ValidationRule rule) {
        if (rule == null) {
            logger.warn("Null doğrulama kuralı eklenmeye çalışıldı");
            return;
        }
        
        String ruleName = rule.getName();
        if (rules.containsKey(ruleName)) {
            logger.warn("'{}' doğrulama kuralı zaten mevcut, değiştiriliyor", ruleName);
            removeRule(ruleName); // Remove existing before adding new to ensure list consistency
        }
        
        rules.put(ruleName, rule);
        // Ensure rulesList is also updated consistently if rules can be added/removed dynamically after init
        if (!rulesList.contains(rule)) { // Simple check, might need more robust if order matters or rules are replaced
            rulesList.add(rule);
        }
        logger.info("Doğrulama kuralı eklendi: {}", ruleName);
    }
    
    @Override
    public boolean removeRule(String ruleName) {
        ValidationRule rule = rules.remove(ruleName);
        if (rule != null) {
            rulesList.remove(rule);
            logger.info("Doğrulama kuralı kaldırıldı: {}", ruleName);
            return true;
        }
        logger.warn("'{}' doğrulama kuralı bulunamadı, kaldırılamıyor", ruleName);
        return false;
    }
}

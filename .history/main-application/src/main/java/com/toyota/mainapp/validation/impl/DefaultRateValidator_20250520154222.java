package com.toyota.mainapp.validation.impl;

import com.toyota.mainapp.model.Rate;
import com.toyota.mainapp.validation.RateValidator;
import com.toyota.mainapp.validation.ValidationRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default implementation of the RateValidator interface.
 * Applies a list of validation rules to rates.
 */
@Service
public class DefaultRateValidator implements RateValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultRateValidator.class);
    
    // Using a list to maintain order of execution from autoconfiguration
    private final List<ValidationRule> rulesList = new ArrayList<>();
    // Using a map for quick lookup by name, e.g., for removal or replacement
    private final Map<String, ValidationRule> rulesMap = new ConcurrentHashMap<>();
    
    @Value("${validation.messages.validator-initialized:DefaultRateValidator {} doğrulama kuralı ile başlatıldı: [{}]}")
    private String validatorInitializedMessage;
    
    @Value("${validation.messages.validator-initialized-no-rules:DefaultRateValidator başlatıldı (yapılandırılmış doğrulama kuralı olmadan)}")
    private String validatorInitializedNoRulesMessage;
    
    @Autowired(required = false)
    private List<ValidationRule> autoconfiguredRules; // Injected by Spring
    
    @PostConstruct
    public void init() {
        if (autoconfiguredRules != null && !autoconfiguredRules.isEmpty()) {
            autoconfiguredRules.forEach(this::addRuleInternal); // Use internal add to avoid logging "replaced"
            logger.info(validatorInitializedMessage, 
                rulesList.size(), 
                rulesList.stream().map(ValidationRule::getName).collect(Collectors.joining(", ")));
        } else {
            logger.info(validatorInitializedNoRulesMessage);
        }
    }
    
    @Override
    public boolean validate(Rate rate) {
        if (rate == null) {
            logger.warn("Doğrulama için null kur alındı, geçersiz sayılıyor.");
            return false;
        }
        
        if (rulesList.isEmpty()) {
            logger.debug("Yapılandırılmış doğrulama kuralı yok, {} kuru varsayılan olarak geçerli kabul ediliyor", rate.getSymbol());
            return true; // No rules means valid by default
        }
        
        for (ValidationRule rule : rulesList) {
            try {
                if (!rule.validate(rate)) {
                    logger.warn("{} kuru '{}' doğrulama kuralını geçemedi. Kural açıklaması: {}", 
                               rate.getSymbol(), rule.getName(), rule.getDescription());
                    return false; // First rule failure invalidates the rate
                }
            } catch (Exception e) {
                logger.error("'{}' doğrulama kuralı {} kuruna uygulanırken istisna oluştu: {}", 
                            rule.getName(), rate.getSymbol(), e.getMessage(), e);
                return false; // Rule application error means validation failure
            }
        }
        
        logger.debug("{} kuru tüm {} doğrulama kuralını geçti", rate.getSymbol(), rulesList.size());
        return true;
    }
    
    private void addRuleInternal(ValidationRule rule) {
        // This method is for internal use, like @PostConstruct, to avoid certain logs or overwrite logic.
        // Assumes rules added at init time are not duplicates or replacement is fine.
        if (rule == null || rule.getName() == null) {
            logger.warn("Null veya isimsiz doğrulama kuralı eklenemiyor.");
            return;
        }
        if (!rulesMap.containsKey(rule.getName())) {
            rulesList.add(rule);
        } else { // Rule with this name already exists, replace it in the list
            rulesList.removeIf(r -> r.getName().equals(rule.getName()));
            rulesList.add(rule); // Add new instance, order might change if not careful
        }
        rulesMap.put(rule.getName(), rule);
    }

    @Override
    public void addRule(ValidationRule rule) {
        if (rule == null || rule.getName() == null) {
            logger.warn("Null veya isimsiz doğrulama kuralı eklenemiyor.");
            return;
        }
        
        String ruleName = rule.getName();
        ValidationRule existingRule = rulesMap.put(ruleName, rule);
        
        if (existingRule != null) {
            logger.info("'{}' doğrulama kuralı yenisiyle değiştirildi.", ruleName);
            // Replace in list while trying to maintain order, or append if not found (should not happen if map had it)
            boolean removed = rulesList.removeIf(r -> r.getName().equals(ruleName));
            rulesList.add(rule); // Add the new rule. Order might change for replaced rules.
                                 // If order is critical, more complex list management is needed.
        } else {
            rulesList.add(rule);
            logger.info("Doğrulama kuralı eklendi: {}", ruleName);
        }
    }
    
    @Override
    public boolean removeRule(String ruleName) {
        if (ruleName == null) {
            logger.warn("Null kural adı kaldırılamaz.");
            return false;
        }
        ValidationRule removedRule = rulesMap.remove(ruleName);
        if (removedRule != null) {
            rulesList.removeIf(rule -> rule.getName().equals(ruleName));
            logger.info("Doğrulama kuralı kaldırıldı: {}", ruleName);
            return true;
        }
        logger.warn("'{}' doğrulama kuralı bulunamadı, kaldırılamıyor", ruleName);
        return false;
    }
    
    @Override
    public Optional<ValidationRule> getRule(String ruleName) {
        return Optional.ofNullable(rulesMap.get(ruleName));
    }
    
    @Override
    public List<ValidationRule> getAllRules() {
        return new ArrayList<>(rulesList);
    }
}

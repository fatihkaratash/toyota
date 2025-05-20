package com.toyota.mainapp.calculator.engine;

import com.toyota.mainapp.calculator.CalculationStrategy;
import com.toyota.mainapp.model.CalculatedRate;
import com.toyota.mainapp.model.Rate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GroovyScriptEngine {
    private static final Logger logger = LoggerFactory.getLogger(GroovyScriptEngine.class);
    private final Resource scriptsDirectory;

    public GroovyScriptEngine(Resource scriptsDirectory) {
        this.scriptsDirectory = scriptsDirectory;
        logger.info("GroovyScriptEngine initialized with scripts directory: {}", scriptsDirectory.getDescription());
    }

    public void registerScript(String symbol, String scriptName, List<String> sourceSymbols) {
        logger.warn("Groovy script registration for symbol '{}', script '{}' is a placeholder. Actual script loading not implemented.", symbol, scriptName);
        // In a real implementation, load and compile the Groovy script here.
        // Store it in a map for later retrieval by getStrategy.
    }

    public CalculationStrategy getStrategy(String symbol) {
        logger.warn("Groovy getStrategy for symbol '{}' is a placeholder. Returning a dummy strategy.", symbol);
        // Return a real strategy if one was registered and compiled.
        return new CalculationStrategy() {
            @Override
            public CalculatedRate calculate(String targetSymbol, Map<String, Rate> sourceRates) {
                logger.warn("Dummy Groovy strategy for {} called. No calculation performed.", targetSymbol);
                return null;
            }
            @Override
            public List<String> getRequiredSourceSymbols() { return Collections.emptyList(); }
            @Override
            public String getStrategyId() { return symbol; }
        };
    }
}

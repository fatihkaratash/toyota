package com.toyota.mainapp.calculator.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class CalculationConfigLoader {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    public CalculationConfigLoader(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    public List<CalculationRuleDto> loadCalculationRules(String configPath) {
        log.info("Loading calculation rules from: {}", configPath);
        Resource resource = resourceLoader.getResource(configPath);
        if (!resource.exists()) {
            log.error("Calculation configuration file not found at path: {}", configPath);
            throw new RuntimeException("Calculation configuration file not found: " + configPath);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            // Assuming the JSON structure is {"calculationRules": [...]}
            Map<String, List<CalculationRuleDto>> configRoot = objectMapper.readValue(inputStream, new TypeReference<>() {});
            List<CalculationRuleDto> rules = configRoot.get("calculationRules");

            if (rules == null) {
                log.warn("No 'calculationRules' key found in the configuration file: {}", configPath);
                return Collections.emptyList();
            }

            // Sort rules by priority (ascending)
            rules.sort(Comparator.comparingInt(CalculationRuleDto::getPriority));

            log.info("Successfully loaded and sorted {} calculation rules.", rules.size());
            rules.forEach(rule -> log.debug("Loaded rule: {}", rule));
            return rules;

        } catch (IOException e) {
            log.error("Failed to load or parse calculation rules from {}: {}", configPath, e.getMessage(), e);
            throw new RuntimeException("Failed to load calculation rules from " + configPath, e);
        }
    }
}

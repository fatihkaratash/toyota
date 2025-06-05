package com.toyota.mainapp.calculator.engine;

import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.config.CalculationRuleDto;

import java.util.Map;
import java.util.Optional;


 //Farklı hesaplama stratejileri için arayüz
public interface CalculationStrategy {

    Optional<BaseRateDto> calculate(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates);
    String getStrategyName();
}


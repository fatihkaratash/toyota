package com.toyota.mainapp.util;

import com.toyota.mainapp.dto.config.CalculationRuleDto;
import com.toyota.mainapp.dto.model.BaseRateDto;
import com.toyota.mainapp.dto.model.InputRateInfo;
import com.toyota.mainapp.dto.model.RateType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RateCalculationUtils {

    public static BaseRateDto calculateAverage(CalculationRuleDto rule, Map<String, BaseRateDto> inputRates) {
        if (inputRates == null || inputRates.isEmpty()) {
            return null;
        }

        BigDecimal totalBid = BigDecimal.ZERO;
        BigDecimal totalAsk = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        
        List<InputRateInfo> calculationInputs = new ArrayList<>();
        long latestTimestamp = 0;

        for (Map.Entry<String, BaseRateDto> entry : inputRates.entrySet()) {
            BaseRateDto rate = entry.getValue();
            if (rate.getBid() == null || rate.getAsk() == null) {
                continue;
            }

            BigDecimal weight = BigDecimal.valueOf(rule.getWeightForSymbol(entry.getKey()));
            
            totalBid = totalBid.add(rate.getBid().multiply(weight));
            totalAsk = totalAsk.add(rate.getAsk().multiply(weight));
            totalWeight = totalWeight.add(weight);
            
            calculationInputs.add(InputRateInfo.fromBaseRateDto(rate));
            
            if (rate.getTimestamp() != null && rate.getTimestamp() > latestTimestamp) {
                latestTimestamp = rate.getTimestamp();
            }
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal avgBid = totalBid.divide(totalWeight, 5, RoundingMode.HALF_UP);
        BigDecimal avgAsk = totalAsk.divide(totalWeight, 5, RoundingMode.HALF_UP);

        BaseRateDto result = new BaseRateDto();
        result.setSymbol(rule.getOutputSymbol());
        result.setRateType(RateType.CALCULATED);
        result.setBid(avgBid);
        result.setAsk(avgAsk);
        result.setTimestamp(latestTimestamp > 0 ? latestTimestamp : System.currentTimeMillis());
        result.setProviderName("CALCULATED");
        result.setCalculationInputs(calculationInputs);

        return result;
    }
    public static boolean isValidRate(BaseRateDto rate) {
        if (rate == null || rate.getBid() == null || rate.getAsk() == null) {
            return false;
        }
        
        return rate.getBid().compareTo(BigDecimal.ZERO) > 0 && 
               rate.getAsk().compareTo(BigDecimal.ZERO) > 0 &&
               rate.getAsk().compareTo(rate.getBid()) >= 0; // Ask >= Bid
    }
}

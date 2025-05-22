package com.toyota.mainapp.calculator.engine.impl;

import com.toyota.mainapp.calculator.engine.CalculationStrategy;
import com.toyota.mainapp.dto.CalculatedRateDto;
import com.toyota.mainapp.dto.CalculationRuleDto;
import com.toyota.mainapp.dto.RawRateDto;
import com.toyota.mainapp.dto.common.InputRateInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component("averageUsdTryStrategy")
@Slf4j
public class AverageUsdTryStrategy implements CalculationStrategy {

    private static final int SCALE = 8; // Or from config

    @Override
    public Optional<CalculatedRateDto> calculate(CalculationRuleDto rule, Map<String, RawRateDto> inputRates) {
        log.debug("Executing AverageUsdTryStrategy for rule: {}", rule.getOutputSymbol());

        if (inputRates == null || inputRates.isEmpty()) {
            log.warn("Kural için giriş oranları sağlanmadı: {}. Hesaplama atlanıyor.", rule.getOutputSymbol());
            return Optional.empty();
        }

        // Check if all required symbols are present
        String[] requiredSymbols = rule.getDependsOnRaw();
        if (requiredSymbols != null && requiredSymbols.length > 0) {
            boolean missingSymbols = false;
            StringBuilder missingSymbolsMsg = new StringBuilder("Eksik semboller: ");
            
            for (String symbol : requiredSymbols) {
                if (!inputRates.containsKey(symbol)) {
                    missingSymbols = true;
                    missingSymbolsMsg.append(symbol).append(", ");
                    log.debug("Gerekli sembol bulunamadı: {} (Kural: {})", symbol, rule.getOutputSymbol());
                }
            }
            
            if (missingSymbols) {
                log.warn("{} kuralı için gerekli bazı semboller eksik. {}Hesaplama atlanıyor.", 
                       rule.getOutputSymbol(), missingSymbolsMsg);
                return Optional.empty();
            }
        }

        List<RawRateDto> sourceRates = new ArrayList<>(inputRates.values());
        List<InputRateInfo> calculationInputs = new ArrayList<>();

        for (RawRateDto rawRate : sourceRates) {
            if (rawRate == null) {
                continue;
            }
            
            calculationInputs.add(InputRateInfo.builder()
                    .symbol(rawRate.getSymbol())
                    .rateType("RAW")
                    .providerName(rawRate.getProviderName())
                    .bid(rawRate.getBid())
                    .ask(rawRate.getAsk())
                    .timestamp(rawRate.getTimestamp())
                    .build());
        }

        if (sourceRates.isEmpty()) {
            log.warn("Önbellekten kuralı kontrol ettikten sonra kaynak kur bulunamadı: {}. Hesaplama atlanıyor.", rule.getOutputSymbol());
            return Optional.empty();
        }

        BigDecimal sumBid = BigDecimal.ZERO;
        BigDecimal sumAsk = BigDecimal.ZERO;

        for (RawRateDto rate : sourceRates) {
            if (rate.getBid() == null || rate.getAsk() == null) {
                log.warn("{} sağlayıcısından {} kurunun teklif/talep değeri boş. Bu kur ortalama hesaplaması için atlanıyor (Kural: {}).",
                        rate.getSymbol(), rate.getProviderName(), rule.getOutputSymbol());
                // Decide if one null rate should fail the whole calculation or just be skipped
                // For now, we'll skip it, but if all are skipped, it will result in division by zero or empty.
                continue;
            }
            sumBid = sumBid.add(rate.getBid());
            sumAsk = sumAsk.add(rate.getAsk());
        }
        
        long validSourceRatesCount = sourceRates.stream().filter(r -> r.getBid() != null && r.getAsk() != null).count();

        if (validSourceRatesCount == 0) {
            log.warn("Kural için geçerli teklif/talep değeri olan kaynak kur bulunamadı: {}. Hesaplama atlanıyor.", rule.getOutputSymbol());
            return Optional.empty();
        }

        BigDecimal avgBid = sumBid.divide(BigDecimal.valueOf(validSourceRatesCount), SCALE, RoundingMode.HALF_UP);
        BigDecimal avgAsk = sumAsk.divide(BigDecimal.valueOf(validSourceRatesCount), SCALE, RoundingMode.HALF_UP);

        CalculatedRateDto calculatedRate = CalculatedRateDto.builder()
                .symbol(rule.getOutputSymbol())
                .bid(avgBid)
                .ask(avgAsk)
                .timestamp(System.currentTimeMillis())
                .calculationInputs(calculationInputs)
                .calculatedByStrategy(this.getClass().getName())
                .build();

        log.info("{} için ortalama kur başarıyla hesaplandı: {}", rule.getOutputSymbol(), calculatedRate);
        return Optional.of(calculatedRate);
    }

    @Override
    public String getStrategyName() {
        return "averageUsdTryStrategy";
    }
}

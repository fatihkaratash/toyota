package com.toyota.restserver.service;

import com.toyota.restserver.exception.RateNotFoundException;
import com.toyota.restserver.logging.LoggingHelper;
import com.toyota.restserver.model.Rate;
import org.springframework.stereotype.Service;

/**
 * Toyota Financial Data Platform - Rate Service
 * 
 * Core business service for retrieving and processing financial rate data.
 * Combines configuration-based base rates with dynamic simulation to provide
 * realistic rate data for the Toyota financial data platform.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Service
public class RateService {

    private static final LoggingHelper log = new LoggingHelper(RateService.class);

    private final RateConfigLoader rateConfigLoader;
    private final RateSimulationService rateSimulationService;

    public RateService(RateConfigLoader rateConfigLoader, RateSimulationService rateSimulationService) {
        this.rateConfigLoader = rateConfigLoader;
        this.rateSimulationService = rateSimulationService;
    }

    public Rate getRateByName(String pairName) {
        log.debug(LoggingHelper.OPERATION_SERVICE_CALL, LoggingHelper.PLATFORM_REST, pairName, null, "Kur verisine erisim istegi alindi.");
        Rate baseRate = rateConfigLoader.getInitialRate(pairName);

        if (baseRate == null) {
            log.warn(LoggingHelper.OPERATION_SERVICE_CALL, LoggingHelper.PLATFORM_REST, pairName, null, "Istenen kur baslangic yapilandirmasinda bulunamadi.");
            throw new RateNotFoundException("Kur ciftine ait veri bulunamadi: " + pairName);
        }

        Rate fluctuatedRate = rateSimulationService.simulateFluctuation(baseRate);
        log.info(LoggingHelper.OPERATION_SERVICE_CALL, LoggingHelper.PLATFORM_REST, pairName,
                String.format("BID:%.5f ASK:%.5f TS:%s", fluctuatedRate.getBid(), fluctuatedRate.getAsk(), fluctuatedRate.getTimestamp()),
                "Kur basariyla getirildi ve dalgalandirildi.");
        return fluctuatedRate;
    }
}

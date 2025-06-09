package com.toyota.restserver.service;

import com.toyota.restserver.logging.LoggingHelper;
import com.toyota.restserver.model.Rate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * Toyota Financial Data Platform - Rate Simulation Service
 * 
 * Service for simulating realistic financial rate fluctuations with configurable
 * volatility and spread parameters. Provides dynamic rate movements while
 * maintaining market-realistic bid/ask relationships for testing purposes.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
@Service
public class RateSimulationService {

    private static final LoggingHelper log = new LoggingHelper(RateSimulationService.class);
    private final Random random = new Random();

    @Value("${rate.simulation.volatility:0.001}")
    private double volatility;

    @Value("${rate.simulation.minSpread:0.0001}")
    private double minSpread;

    @Value("${rate.simulation.maxRetries:10}")
    private int maxRetries;


    public Rate simulateFluctuation(Rate baseRate) {
        Rate fluctuatedRate = baseRate.copy(); // Kopya uzerinde calis
        double originalBid = fluctuatedRate.getBid();
        double originalAsk = fluctuatedRate.getAsk();
        double midPrice = (originalBid + originalAsk) / 2.0;

        if (midPrice <= 0) midPrice = Math.max(originalBid, 0.00001); // Sifir/negatif durumunu isle

        double newBid = 0, newAsk = 0;
        boolean validPrice = false;
        int retries = 0;

        while(!validPrice && retries < maxRetries) {
            double change = (random.nextDouble() - 0.5) * 2 * midPrice * volatility; // Simetrik degisim
            newBid = originalBid + change;
            newBid = Math.max(0.000001, newBid); // Pozitif bid 

            double spreadFactor = minSpread + (random.nextDouble() * volatility); 
            newAsk = newBid * (1 + spreadFactor);
            newAsk = Math.max(newAsk, newBid + minSpread); 

            if (newBid > 0 && newAsk > newBid) {
                validPrice = true;
            } else {
                retries++;
                // Yeniden deniyorsa, kotu degerlerden kaynakli sapmayi onlemek icin orjinale sifirla
                newBid = originalBid; 
                newAsk = originalAsk;
            }
        }

        if (validPrice) {
            fluctuatedRate.setBid(newBid);
            fluctuatedRate.setAsk(newAsk);
        } else {
            log.warn(LoggingHelper.OPERATION_SIMULATE_RATE, LoggingHelper.PLATFORM_REST, fluctuatedRate.getPairName(), null,
                    maxRetries + " deneme sonrasinda gecerli dalgalanmis fiyat olusturulamadi. Orijinal bid/ask kullaniliyor.");
        }

        fluctuatedRate.setCurrentTimestamp();
        log.debug(LoggingHelper.OPERATION_SIMULATE_RATE, LoggingHelper.PLATFORM_REST, fluctuatedRate.getPairName(),
                String.format("Onceki BID:%.5f ASK:%.5f -> Yeni BID:%.5f ASK:%.5f", originalBid, originalAsk, fluctuatedRate.getBid(), fluctuatedRate.getAsk()),
                "Kur dalgalandirmasi yapildi");
        return fluctuatedRate;
    }
}

package com.toyota.tcpserver.service;

import com.toyota.tcpserver.config.ConfigurationReader;
import com.toyota.tcpserver.model.Rate;
import com.toyota.tcpserver.logging.LoggingHelper;
import java.util.Random;

/**
 * Toyota Financial Data Platform - Rate Fluctuation Simulator
 * 
 * Simulates realistic financial rate fluctuations with configurable volatility
 * and spread parameters. Provides market-like rate movements for testing and
 * demonstration purposes within the TCP rate provider service.
 * 
 * @author Fatih Karataş
 * @version 1.0
 * @since 2025
 */
public class RateFluctuationSimulator {
    private static final LoggingHelper log = new LoggingHelper(RateFluctuationSimulator.class);
    private final Random random = new Random();
    private final double volatility;
    private final double minSpread;
    private final int maxRetries;


    public RateFluctuationSimulator(ConfigurationReader config) {
        this.volatility = config.getFluctuationVolatility();
        this.minSpread = config.getMinSpread();
        this.maxRetries = config.getFluctuationMaxRetries();
    }

    public Rate fluctuateRate(Rate originalRate) {
        Rate newRate = originalRate.copy(); 
        double basePrice = (newRate.getBid() + newRate.getAsk()) / 2.0;
        if (basePrice <= 0) basePrice = Math.abs(newRate.getBid()); // Sıfır veya negatif 
        if (basePrice <= 0) basePrice = 0.0001; 

        double newBid = 0, newAsk = 0;
        boolean validPrice = false;
        int retries = 0;

        while(!validPrice && retries < maxRetries) {
            // Alış fiyatını dalgalandır
            double change = (random.nextDouble() - 0.5) * 2 * basePrice * volatility; // Simetrik değişim
            newBid = newRate.getBid() + change;

            // Alış fiyatının pozitif olduğundan emin ol
            newBid = Math.max(0.000001, newBid);

            // Minimum spread ile satış hesapla
            double spread = Math.max(minSpread, newBid * (0.001 + random.nextDouble() * 0.002)); // %0.1 ile %0.3 arasında spread
            newAsk = newBid + spread;
            
            if (newBid > 0 && newAsk > newBid) {
                validPrice = true;
            } else {
                retries++;
                String rateInfo = String.format("BID:%.5f ASK:%.5f", newBid, newAsk);
                log.trace(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_PF1, newRate.getPairName(), rateInfo,
                        "Geçersiz fiyat oluşturuldu. Yeniden deneniyor... (" + retries + "/" + maxRetries + ")");
                newBid = originalRate.getBid();
                newAsk = originalRate.getAsk();
            }
        }

        if (!validPrice) {
            log.warn(LoggingHelper.OPERATION_ALERT, LoggingHelper.PLATFORM_PF1, newRate.getPairName(), 
                    maxRetries + " deneme sonrasında geçerli bir dalgalanmış fiyat oluşturulamadı. Orijinal fiyatlar kullanılıyor.");
            newRate.setBid(originalRate.getBid()); 
            newRate.setAsk(originalRate.getAsk());
        } else {
            newRate.setBid(newBid);
            newRate.setAsk(newAsk);
        }

        newRate.setCurrentTimestamp();
        String rateInfo = String.format("BID:%.5f ASK:%.5f", newRate.getBid(), newRate.getAsk());
        log.trace(LoggingHelper.OPERATION_UPDATE, LoggingHelper.PLATFORM_PF1, newRate.getPairName(), rateInfo,
                "Dalgalanmış kur oluşturuldu, Zaman Damgası=" + newRate.getTimestamp());
        return newRate;
    }
}

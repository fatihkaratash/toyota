package com.toyota.tcpserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

public class RateFluctuationSimulator {
    private static final Logger logger = LogManager.getLogger(RateFluctuationSimulator.class);
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
        Rate newRate = originalRate.copy(); // Bir kopya üzerinde çalış
        double basePrice = (newRate.getBid() + newRate.getAsk()) / 2.0;
        if (basePrice <= 0) basePrice = Math.abs(newRate.getBid()); // Sıfır veya negatif taban durumunu ele al
        if (basePrice <= 0) basePrice = 0.0001; // Mutlak yedek değer

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
                logger.trace("{} için geçersiz fiyat oluşturuldu, alış={}, satış={}. Yeniden deneniyor... ({}/{})", newRate.getPairName(), newBid, newAsk, retries, maxRetries);
                // Yeniden denemeler oluyorsa, kötü değerlerden kaynaklanan sapmayı önlemek için orjinale sıfırla
                newBid = originalRate.getBid();
                newAsk = originalRate.getAsk();
            }
        }

        if (!validPrice) {
            logger.warn("{} için {} deneme sonrasında geçerli bir dalgalanmış fiyat oluşturulamadı. Orijinal fiyatlar kullanılıyor.", newRate.getPairName(), maxRetries);
            newRate.setBid(originalRate.getBid()); // Hala geçersizse orijinale geri dön
            newRate.setAsk(originalRate.getAsk());
        } else {
            newRate.setBid(newBid);
            newRate.setAsk(newAsk);
        }

        newRate.setCurrentTimestamp();
        logger.trace("{} için dalgalanmış kur: Alış={}, Satış={}, Zaman Damgası={}",
                newRate.getPairName(), newRate.getBid(), newRate.getAsk(), newRate.getTimestamp());
        return newRate;
    }
}

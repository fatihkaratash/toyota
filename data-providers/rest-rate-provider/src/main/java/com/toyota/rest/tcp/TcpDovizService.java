package com.toyota.provider.tcp;

import com.toyota.provider.common.Doviz;
import com.toyota.provider.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class TcpDovizService {

    private static final Logger logger = LoggerFactory.getLogger(TcpDovizService.class);
    private final Map<String, Doviz> liveRates = new HashMap<>();
    private final List<String> availableRates;
    private final ApplicationProperties applicationProperties;
    private final Random random = new Random();

    public TcpDovizService(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
        this.availableRates = applicationProperties.getTcp().getAvailableRates();
        initializeRates();
    }

    private void initializeRates() {
        for (String rateName : availableRates) {
            liveRates.put(rateName, generateRate(rateName));
        }
    }

    @Scheduled(fixedRateString = "${rate-provider.tcp.publish-interval-ms:1000}")
    public void generateLiveDoviz() {
        for (String rateName : availableRates) {
            Doviz doviz = generateRate(rateName);
            liveRates.put(rateName, doviz);
        }
        logger.debug("TCP Service: New currency data generated for: {}", availableRates);
    }

    public Doviz getLatestDoviz(String rateName) {
        return liveRates.get(rateName);
    }

    public boolean isValidRate(String rateName) {
        return availableRates.contains(rateName);
    }

    private Doviz generateRate(String rateName) {
        ApplicationProperties.RateConfig config = applicationProperties.getInitialRates().get(rateName);
        double basePrice = (config != null) ? config.getBase() : getDefaultBasePrice(rateName);
        double volatility = (config != null) ? config.getVolatility() : getDefaultVolatility(rateName);

        double priceChange = (random.nextDouble() - 0.5) * volatility; // Small random change
        double bid = basePrice + priceChange;
        double ask = bid + (bid * 0.002); // 0.2% spread

        // Ensure bid/ask are positive
        bid = Math.max(0.0001, bid);
        ask = Math.max(bid + 0.0001, ask);


        Doviz doviz = new Doviz(rateName, bid, ask, null);
        doviz.setCurrentTimestamp(); // Sets current time in ISO 8601
        return doviz;
    }

    private double getDefaultBasePrice(String rateName) {
        if (rateName.contains("EURUSD")) return 1.08;
        if (rateName.contains("USDTRY")) return 33.0;
        return 1.0;
    }

    private double getDefaultVolatility(String rateName) {
        if (rateName.contains("EURUSD")) return 0.01;
        if (rateName.contains("USDTRY")) return 0.5;
        return 0.1;
    }

    public List<String> getAvailableRates() {
        return availableRates;
    }
}

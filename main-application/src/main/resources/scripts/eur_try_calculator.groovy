import java.math.BigDecimal
import java.math.RoundingMode

// These variables are injected by GroovyScriptCalculationStrategy:
// cache (RateCacheService)
// log (Logger)
// outputSymbol (String, e.g., "EUR/TRY_GROOVY")
// eurUsdSourceKey (String, from inputParameters, e.g., "PF1_EURUSD")
// usdTryAvgSourceKey (String, from inputParameters, e.g., "USD/TRY_AVG_PF")
// defaultScale (String, from inputParameters, e.g., "8")

log.info("EUR/TRY hesaplaması başlatılıyor: ${outputSymbol}")
log.debug("Input parameters: eurUsdSourceKey=${eurUsdSourceKey}, usdTryAvgSourceKey=${usdTryAvgSourceKey}, defaultScale=${defaultScale}")

def scale = defaultScale.toInteger()
def roundingMode = RoundingMode.HALF_UP

// Gerekli kurları önbellekten al
def eurUsd = cache.getRawRate(eurUsdSourceKey).orElse(null)
def usdTryAvg = cache.getCalculatedRate(usdTryAvgSourceKey).orElse(null)

if (!eurUsd || !usdTryAvg) {
    log.warn("EUR/TRY hesaplaması için gerekli kurlar eksik: ${eurUsdSourceKey} veya ${usdTryAvgSourceKey}")
    return null
}

// EUR/TRY hesapla
def eurTryBid = eurUsd.getBid().multiply(usdTryAvg.getBid())
def eurTryAsk = eurUsd.getAsk().multiply(usdTryAvg.getAsk())

// Hesaplama girdilerini oluştur
def inputs = [
    [
        symbol: eurUsdSourceKey,
        rateType: "RAW",
        providerName: eurUsd.getProviderName(),
        bid: eurUsd.getBid(),
        ask: eurUsd.getAsk(),
        timestamp: eurUsd.getTimestamp()
    ],
    [
        symbol: usdTryAvgSourceKey,
        rateType: "CALCULATED",
        providerName: "CALCULATOR",
        bid: usdTryAvg.getBid(),
        ask: usdTryAvg.getAsk(),
        timestamp: usdTryAvg.getTimestamp()
    ]
]

log.info("Successfully calculated ${outputSymbol}: Bid=${eurTryBid}, Ask=${eurTryAsk}")

// Sonuç
return [
    symbol: outputSymbol,
    bid: eurTryBid,
    ask: eurTryAsk,
    timestamp: System.currentTimeMillis(),
    calculationInputs: inputs,
    calculatedByStrategy: "scripts/eur_try_calculator.groovy"
]

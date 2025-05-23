import java.math.BigDecimal
import java.math.RoundingMode

// These variables are injected by GroovyScriptCalculationStrategy:
// cache (RateCacheService)
// log (Logger)
// outputSymbol (String, e.g., "EUR/TRY")
// inputRates (Map<String, BaseRateDto>)
// eurUsdSource1Key (String, from inputParameters, e.g., "PF1_EURUSD")
// eurUsdSource2Key (String, from inputParameters, e.g., "PF2_EURUSD")
// usdTryAvgSourceKey (String, from inputParameters, e.g., "USD/TRY_AVG")
// defaultScale (String, from inputParameters, e.g., "8")

log.info("EUR/TRY hesaplaması başlatılıyor: ${outputSymbol}")
log.debug("Input parameters: eurUsdSource1Key=${eurUsdSource1Key}, eurUsdSource2Key=${eurUsdSource2Key}, usdTryAvgSourceKey=${usdTryAvgSourceKey}, defaultScale=${defaultScale}")

def scale = defaultScale.toInteger()
def roundingMode = RoundingMode.HALF_UP

// Get USD/TRY average rate from calculated rates
def usdTryAvg = cache.getCalculatedRate(usdTryAvgSourceKey).orElse(null)

if (!usdTryAvg) {
    log.warn("EUR/TRY hesaplaması için gerekli ortalama USD/TRY kuru eksik: ${usdTryAvgSourceKey}")
    return null
}

// Get EUR/USD rates from both providers
def eurUsdSource1 = cache.getRate(eurUsdSource1Key).orElse(null)
def eurUsdSource2 = cache.getRate(eurUsdSource2Key).orElse(null)

// Create inputs list for tracking calculation sources
def inputs = []

// Add USD/TRY average to inputs
inputs.add([
    symbol: usdTryAvgSourceKey,
    rateType: "CALCULATED",
    providerName: "CALCULATOR",
    bid: usdTryAvg.getBid(),
    ask: usdTryAvg.getAsk(),
    timestamp: usdTryAvg.getTimestamp()
])

// Calculate EUR/USD average if both providers are available
BigDecimal eurUsdBid = null
BigDecimal eurUsdAsk = null
int validSources = 0

// Process first EUR/USD source if available
if (eurUsdSource1) {
    if (eurUsdBid == null) eurUsdBid = BigDecimal.ZERO
    if (eurUsdAsk == null) eurUsdAsk = BigDecimal.ZERO
    
    eurUsdBid = eurUsdBid.add(eurUsdSource1.getBid())
    eurUsdAsk = eurUsdAsk.add(eurUsdSource1.getAsk())
    validSources++
    
    inputs.add([
        symbol: eurUsdSource1Key,
        rateType: "RAW",
        providerName: eurUsdSource1.getProviderName(),
        bid: eurUsdSource1.getBid(),
        ask: eurUsdSource1.getAsk(),
        timestamp: eurUsdSource1.getTimestamp()
    ])
    
    log.debug("${eurUsdSource1Key} kullanılıyor: Bid=${eurUsdSource1.getBid()}, Ask=${eurUsdSource1.getAsk()}")
}

// Process second EUR/USD source if available
if (eurUsdSource2) {
    if (eurUsdBid == null) eurUsdBid = BigDecimal.ZERO
    if (eurUsdAsk == null) eurUsdAsk = BigDecimal.ZERO
    
    eurUsdBid = eurUsdBid.add(eurUsdSource2.getBid())
    eurUsdAsk = eurUsdAsk.add(eurUsdSource2.getAsk())
    validSources++
    
    inputs.add([
        symbol: eurUsdSource2Key,
        rateType: "RAW",
        providerName: eurUsdSource2.getProviderName(),
        bid: eurUsdSource2.getBid(),
        ask: eurUsdSource2.getAsk(),
        timestamp: eurUsdSource2.getTimestamp()
    ])
    
    log.debug("${eurUsdSource2Key} kullanılıyor: Bid=${eurUsdSource2.getBid()}, Ask=${eurUsdSource2.getAsk()}")
}

// If no valid EUR/USD sources, fail
if (validSources == 0) {
    log.warn("EUR/TRY hesaplaması için gerekli EUR/USD kurları eksik.")
    return null
}

// Calculate average EUR/USD rate if multiple sources are available
if (validSources > 1) {
    eurUsdBid = eurUsdBid.divide(BigDecimal.valueOf(validSources), scale, roundingMode)
    eurUsdAsk = eurUsdAsk.divide(BigDecimal.valueOf(validSources), scale, roundingMode)
    log.debug("EUR/USD ortalama hesaplandı: Bid=${eurUsdBid}, Ask=${eurUsdAsk}")
}

// Calculate EUR/TRY
def eurTryBid = eurUsdBid.multiply(usdTryAvg.getBid()).setScale(scale, roundingMode)
def eurTryAsk = eurUsdAsk.multiply(usdTryAvg.getAsk()).setScale(scale, roundingMode)
log.debug("EUR/TRY hesaplandı: Bid=${eurTryBid}, Ask=${eurTryAsk}")

log.info("Successfully calculated ${outputSymbol}: Bid=${eurTryBid}, Ask=${eurTryAsk}")

// Result - return as a map that will be converted to BaseRateDto
return [
    symbol: outputSymbol,
    bid: eurTryBid,
    ask: eurTryAsk,
    timestamp: System.currentTimeMillis(),
    calculationInputs: inputs,
    calculatedByStrategy: "scripts/eur_try_calculator.groovy",
    rateType: "CALCULATED"
]

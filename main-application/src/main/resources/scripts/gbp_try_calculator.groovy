import java.math.BigDecimal
import java.math.RoundingMode

// These variables are injected by GroovyScriptCalculationStrategy:
// cache (RateCacheService)
// log (Logger)
// outputSymbol (String, e.g., "GBP/TRY_GROOVY")
// gbpUsdSourceKey (String, from inputParameters, e.g., "PF1_GBPUSD")
// usdTryAvgSourceKey (String, from inputParameters, e.g., "USD/TRY_AVG_PF")
// defaultScale (String, from inputParameters, e.g., "8")

log.debug("Executing gbp_try_calculator.groovy for outputSymbol: ${outputSymbol}")
log.debug("Input parameters: gbpUsdSourceKey=${gbpUsdSourceKey}, usdTryAvgSourceKey=${usdTryAvgSourceKey}, defaultScale=${defaultScale}")

def scale = defaultScale.toInteger()
def roundingMode = RoundingMode.HALF_UP

def gbpUsdRateOpt = cache.getRawRate(gbpUsdSourceKey)
if (!gbpUsdRateOpt.isPresent()) {
    log.warn("GBP/USD rate not found in cache for key: ${gbpUsdSourceKey}. Cannot calculate ${outputSymbol}.")
    return [:] // Return empty map to indicate calculation failure
}
def gbpUsdRate = gbpUsdRateOpt.get()
log.debug("Fetched GBP/USD rate: ${gbpUsdRate}")

if (gbpUsdRate.bid == null || gbpUsdRate.ask == null) {
    log.warn("GBP/USD rate (${gbpUsdSourceKey}) has null bid/ask. Cannot calculate ${outputSymbol}.")
    return [:]
}

def usdTryAvgRateOpt = cache.getCalculatedRate(usdTryAvgSourceKey)
if (!usdTryAvgRateOpt.isPresent()) {
    log.warn("USD/TRY_AVG_PF rate not found in cache for key: ${usdTryAvgSourceKey}. Cannot calculate ${outputSymbol}.")
    return [:] // Return empty map
}
def usdTryAvgRate = usdTryAvgRateOpt.get()
log.debug("Fetched USD/TRY_AVG_PF rate: ${usdTryAvgRate}")

if (usdTryAvgRate.bid == null || usdTryAvgRate.ask == null) {
    log.warn("USD/TRY_AVG_PF rate (${usdTryAvgSourceKey}) has null bid/ask. Cannot calculate ${outputSymbol}.")
    return [:]
}

def calculatedBid = (gbpUsdRate.bid * usdTryAvgRate.bid).setScale(scale, roundingMode)
def calculatedAsk = (gbpUsdRate.ask * usdTryAvgRate.ask).setScale(scale, roundingMode)
def currentTimestamp = System.currentTimeMillis()

def calculationInputs = [
    [
        symbol: gbpUsdRate.symbol,
        rateType: 'RAW',
        providerName: gbpUsdRate.providerName,
        bid: gbpUsdRate.bid,
        ask: gbpUsdRate.ask,
        timestamp: gbpUsdRate.rateTimestamp
    ],
    [
        symbol: usdTryAvgRate.symbol,
        rateType: 'CALCULATED',
        providerName: null, // Calculated rates don't have a single provider name in this context
        bid: usdTryAvgRate.bid,
        ask: usdTryAvgRate.ask,
        timestamp: usdTryAvgRate.timestamp
    ]
]

log.info("Successfully calculated ${outputSymbol}: Bid=${calculatedBid}, Ask=${calculatedAsk}")

return [
    symbol: outputSymbol,
    bid: calculatedBid,
    ask: calculatedAsk,
    timestamp: currentTimestamp,
    calculationInputs: calculationInputs
]

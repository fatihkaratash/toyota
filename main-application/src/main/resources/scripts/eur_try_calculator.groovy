import com.toyota.mainapp.dto.BaseRateDto
import com.toyota.mainapp.dto.RateType
import com.toyota.mainapp.dto.common.InputRateInfo

import java.math.BigDecimal
import java.math.RoundingMode

// These variables are injected by GroovyScriptCalculationStrategy:
// cache (RateCacheService) - Still available but might not be needed for primary inputs
// log (Logger)
// outputSymbol (String, e.g., "EUR/TRY")
// inputRates (Map<String, BaseRateDto>) // This map will contain EURUSD_AVG and USD/TRY_AVG
// eurUsdAvgKey (String, from inputParameters, e.g., "EURUSD_AVG") 
// usdTryAvgSourceKey (String, from inputParameters, e.g., "USD/TRY_AVG")
// defaultScale (String, from inputParameters, e.g., "8")

log.info("EUR/TRY çapraz kur hesaplaması başlatılıyor: ${outputSymbol}")
log.debug("Input parameters: eurUsdAvgKey=${eurUsdAvgKey}, usdTryAvgSourceKey=${usdTryAvgSourceKey}, defaultScale=${defaultScale}")
log.debug("Available input rates: ${inputRates.keySet()}")

def scale = defaultScale.toInteger()
def roundingMode = RoundingMode.HALF_UP
def calculationInputs = []

// 1. Get USD/TRY average rate from inputRates map (passed by RuleEngine)
def usdTryAvgRate = inputRates.get(usdTryAvgSourceKey)

if (!usdTryAvgRate) {
    log.warn("EUR/TRY hesaplaması için gerekli ortalama USD/TRY kuru eksik: ${usdTryAvgSourceKey} (inputRates üzerinden alınamadı)")
    log.debug("Available keys in inputRates: ${inputRates.keySet()}")
    return null
}
if (usdTryAvgRate.bid == null || usdTryAvgRate.ask == null) {
    log.warn("USD/TRY_AVG rate (${usdTryAvgSourceKey}) has null bid/ask. Cannot calculate ${outputSymbol}.")
    return null
}
calculationInputs.add(
    InputRateInfo.builder()
        .symbol(usdTryAvgRate.symbol)
        .rateType(usdTryAvgRate.rateType.toString())
        .providerName(usdTryAvgRate.providerName ?: "Calculated")
        .bid(usdTryAvgRate.bid)
        .ask(usdTryAvgRate.ask)
        .timestamp(usdTryAvgRate.timestamp) // Use the timestamp from the DTO
        .build()
)

// 2. Get EUR/USD average rate from inputRates map (passed by RuleEngine)
def eurUsdAvgRate = inputRates.get(eurUsdAvgKey)

if (!eurUsdAvgRate) {
    log.warn("EUR/TRY hesaplaması için gerekli ortalama EUR/USD kuru eksik: ${eurUsdAvgKey} (inputRates üzerinden alınamadı)")
    log.debug("Available keys in inputRates: ${inputRates.keySet()}")
    return null
}
if (eurUsdAvgRate.bid == null || eurUsdAvgRate.ask == null) {
    log.warn("EUR/USD_AVG rate (${eurUsdAvgKey}) has null bid/ask. Cannot calculate ${outputSymbol}.")
    return null
}
calculationInputs.add(
    InputRateInfo.builder()
        .symbol(eurUsdAvgRate.symbol)
        .rateType(eurUsdAvgRate.rateType.toString())
        .providerName(eurUsdAvgRate.providerName ?: "Calculated")
        .bid(eurUsdAvgRate.bid)
        .ask(eurUsdAvgRate.ask)
        .timestamp(eurUsdAvgRate.timestamp) // Use the timestamp from the DTO
        .build()
)

// 3. Calculate EUR/TRY cross rate: (EUR/USD_AVG) * (USD/TRY_AVG)
def calculatedBid = (eurUsdAvgRate.bid * usdTryAvgRate.bid).setScale(scale, roundingMode)
def calculatedAsk = (eurUsdAvgRate.ask * usdTryAvgRate.ask).setScale(scale, roundingMode)
def currentTimestamp = System.currentTimeMillis()

log.info("Hesaplanan EUR/TRY (${outputSymbol}): Bid=${calculatedBid}, Ask=${calculatedAsk}")

return [
    symbol: outputSymbol,
    bid: calculatedBid,
    ask: calculatedAsk,
    rateTimestamp: currentTimestamp,
    rateType: RateType.CALCULATED.toString(),
    providerName: "EurTryScriptCalculator",
    calculationInputs: calculationInputs,
    calculatedByStrategy: "scripts/eur_try_calculator.groovy"
]
import com.toyota.mainapp.dto.BaseRateDto
import com.toyota.mainapp.dto.RateType
import com.toyota.mainapp.dto.common.InputRateInfo

import java.math.BigDecimal
import java.math.RoundingMode

// These variables are injected by GroovyScriptCalculationStrategy:
// cache (RateCacheService) - Still available but might not be needed for primary inputs
// log (Logger)
// outputSymbol (String, e.g., "GBP/TRY")
// inputRates (Map<String, BaseRateDto>) // This map will contain GBPUSD_AVG and USD/TRY_AVG
// gbpUsdAvgKey (String, from inputParameters, e.g., "GBPUSD_AVG")
// usdTryAvgSourceKey (String, from inputParameters, e.g., "USD/TRY_AVG")
// defaultScale (String, from inputParameters, e.g., "8")

log.info("GBP/TRY hesaplaması başlatılıyor: ${outputSymbol}")
log.debug("Input parameters: gbpUsdAvgKey=${gbpUsdAvgKey}, usdTryAvgSourceKey=${usdTryAvgSourceKey}, defaultScale=${defaultScale}")

def scale = defaultScale.toInteger()
def roundingMode = RoundingMode.HALF_UP
def calculationInputs = []

// Get USD/TRY average rate from inputRates map (passed by RuleEngine)
def usdTryAvgRate = inputRates.get(usdTryAvgSourceKey)

if (!usdTryAvgRate) {
    log.warn("GBP/TRY hesaplaması için gerekli ortalama USD/TRY kuru eksik: ${usdTryAvgSourceKey} (inputRates üzerinden alınamadı)")
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

// Get GBP/USD average rate from inputRates map (passed by RuleEngine)
def gbpUsdAvgRate = inputRates.get(gbpUsdAvgKey)

if (!gbpUsdAvgRate) {
    log.warn("GBP/TRY hesaplaması için gerekli ortalama GBP/USD kuru eksik: ${gbpUsdAvgKey} (inputRates üzerinden alınamadı)")
    return null
}
if (gbpUsdAvgRate.bid == null || gbpUsdAvgRate.ask == null) {
    log.warn("GBP/USD_AVG rate (${gbpUsdAvgKey}) has null bid/ask. Cannot calculate ${outputSymbol}.")
    return null
}
calculationInputs.add(
    InputRateInfo.builder()
        .symbol(gbpUsdAvgRate.symbol)
        .rateType(gbpUsdAvgRate.rateType.toString())
        .providerName(gbpUsdAvgRate.providerName ?: "Calculated")
        .bid(gbpUsdAvgRate.bid)
        .ask(gbpUsdAvgRate.ask)
        .timestamp(gbpUsdAvgRate.timestamp) // Use the timestamp from the DTO
        .build()
)

// Calculate GBP/TRY cross rate: (GBP/USD_AVG) * (USD/TRY_AVG)
def calculatedBid = (gbpUsdAvgRate.bid * usdTryAvgRate.bid).setScale(scale, roundingMode)
def calculatedAsk = (gbpUsdAvgRate.ask * usdTryAvgRate.ask).setScale(scale, roundingMode)
def currentTimestamp = System.currentTimeMillis()

log.info("Hesaplanan GBP/TRY (${outputSymbol}): Bid=${calculatedBid}, Ask=${calculatedAsk}")

return [
    symbol: outputSymbol,
    bid: calculatedBid,
    ask: calculatedAsk,
    rateTimestamp: currentTimestamp,
    rateType: RateType.CALCULATED.toString(),
    providerName: "GbpTryScriptCalculator",
    calculationInputs: calculationInputs,
    calculatedByStrategy: "scripts/gbp_try_calculator.groovy"
]
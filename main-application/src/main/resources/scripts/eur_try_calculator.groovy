import com.toyota.mainapp.dto.model.BaseRateDto
import com.toyota.mainapp.dto.model.RateType
import com.toyota.mainapp.dto.model.InputRateInfo

import java.math.BigDecimal
import java.math.RoundingMode

// ✅ SCRIPT PARAMETERS FROM CONFIG
def eurUsdAvgKey = eurUsdAvgKey ?: "EURUSD_AVG"
def usdTryAvgSourceKey = usdTryAvgSourceKey ?: "USDTRY_AVG"
def defaultScale = defaultScale ?: "5"

log.info("EUR/TRY çapraz kur hesaplaması başlatılıyor: {}", outputSymbol)
log.info("İşlem parametreleri: eurUsdAvgKey={}, usdTryAvgSourceKey={}, defaultScale={}",
    eurUsdAvgKey, usdTryAvgSourceKey, defaultScale)

// Log available input rates
if (inputRates) {
    log.info("Kullanılabilir giriş kurları ({}): {}", inputRates.size(),
        inputRates.keySet().join(", "))
} else {
    log.warn("EUR/TRY için inputRates null veya boş!")
}

def scale = defaultScale.toInteger()
def roundingMode = RoundingMode.HALF_UP
def calculationInputs = []

// Get USD/TRY average rate from inputRates map
def usdTryAvgRate = inputRates.get(usdTryAvgSourceKey)
log.debug("İlk bakışta USD/TRY kuru için {} anahtarı ile sonuç: {}", 
    usdTryAvgSourceKey, usdTryAvgRate != null ? "BULUNDU" : "BULUNAMADI")

if (!usdTryAvgRate) {
    log.error("EUR/TRY hesaplaması için gerekli USD/TRY kuru eksik: {} (inputRates üzerinden alınamadı)", 
        usdTryAvgSourceKey)
    log.debug("Kullanılabilir anahtarlar: {}", inputRates.keySet().join(", "))
    return null
}

// Get EUR/USD average rate from inputRates map
def eurUsdAvgRate = inputRates.get(eurUsdAvgKey)
log.debug("İlk bakışta EUR/USD kuru için {} anahtarı ile sonuç: {}", 
    eurUsdAvgKey, eurUsdAvgRate != null ? "BULUNDU" : "BULUNAMADI")

if (!eurUsdAvgRate) {
    log.error("EUR/TRY hesaplaması için gerekli EUR/USD kuru eksik: {} (inputRates üzerinden alınamadı)", 
        eurUsdAvgKey)
    log.debug("Kullanılabilir anahtarlar: {}", inputRates.keySet().join(", "))
    return null
}

// ✅ VALIDATION: Check rate data quality
if (usdTryAvgRate.bid == null || usdTryAvgRate.ask == null) {
    log.error("❌ USD/TRY rate has null bid/ask values")
    return null
}

if (eurUsdAvgRate.bid == null || eurUsdAvgRate.ask == null) {
    log.error("❌ EUR/USD rate has null bid/ask values") 
    return null
}

log.info("USD/TRY Kuru BULUNDU: bid={}, ask={}, timestamp={}", 
    usdTryAvgRate.bid, usdTryAvgRate.ask, usdTryAvgRate.timestamp)

calculationInputs.add(
    InputRateInfo.builder()
        .symbol(usdTryAvgRate.symbol)
        .rateType(usdTryAvgRate.rateType.toString())
        .providerName(usdTryAvgRate.providerName ?: "Calculated")
        .bid(usdTryAvgRate.bid)
        .ask(usdTryAvgRate.ask)
        .timestamp(usdTryAvgRate.timestamp)
        .build()
)

log.info("EUR/USD Kuru BULUNDU: bid={}, ask={}, timestamp={}", 
    eurUsdAvgRate.bid, eurUsdAvgRate.ask, eurUsdAvgRate.timestamp)

calculationInputs.add(
    InputRateInfo.builder()
        .symbol(eurUsdAvgRate.symbol)
        .rateType(eurUsdAvgRate.rateType.toString())
        .providerName(eurUsdAvgRate.providerName ?: "Calculated")
        .bid(eurUsdAvgRate.bid)
        .ask(eurUsdAvgRate.ask)
        .timestamp(eurUsdAvgRate.timestamp)
        .build()
)

// Calculate EUR/TRY cross rate: (EUR/USD_AVG) * (USD/TRY_AVG)
def calculatedBid = null
def calculatedAsk = null

// Ensure BigDecimal calculations
if (eurUsdAvgRate.bid instanceof BigDecimal && usdTryAvgRate.bid instanceof BigDecimal) {
    calculatedBid = eurUsdAvgRate.bid.multiply(usdTryAvgRate.bid).setScale(scale, roundingMode)
    calculatedAsk = eurUsdAvgRate.ask.multiply(usdTryAvgRate.ask).setScale(scale, roundingMode)
} else {
    // Convert to BigDecimal if needed
    BigDecimal eurUsdBid = new BigDecimal(eurUsdAvgRate.bid.toString())
    BigDecimal eurUsdAsk = new BigDecimal(eurUsdAvgRate.ask.toString())
    BigDecimal usdTryBid = new BigDecimal(usdTryAvgRate.bid.toString())
    BigDecimal usdTryAsk = new BigDecimal(usdTryAvgRate.ask.toString())
    
    calculatedBid = eurUsdBid.multiply(usdTryBid).setScale(scale, roundingMode)
    calculatedAsk = eurUsdAsk.multiply(usdTryAsk).setScale(scale, roundingMode)
}

log.info("EUR/TRY Calculation: {}*{}={} and {}*{}={}", 
    eurUsdAvgRate.bid, usdTryAvgRate.bid, calculatedBid,
    eurUsdAvgRate.ask, usdTryAvgRate.ask, calculatedAsk)

log.info("Hesaplanan EUR/TRY ({}): Bid={}, Ask={}", outputSymbol, calculatedBid, calculatedAsk)

// ✅ RETURN: Optimized format for pipeline
return [
    symbol: outputSymbol ?: "EURTRY_CROSS",
    bid: calculatedBid,
    ask: calculatedAsk,
    rateTimestamp: System.currentTimeMillis(),
    rateType: RateType.CALCULATED.toString(),
    providerName: "EurTryScriptCalculator",
    calculationInputs: calculationInputs,
    calculatedByStrategy: "scripts/eur_try_calculator.groovy"
]
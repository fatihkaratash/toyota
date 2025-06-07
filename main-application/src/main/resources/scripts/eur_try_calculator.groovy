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

// ✅ SMART INPUT RESOLUTION: Try multiple key formats
def usdTryAvgRate = null
def eurUsdAvgRate = null

// Find USD/TRY input with multiple key formats
def usdTryKeys = [
    usdTryAvgSourceKey,
    "USDTRY_AVG", 
    "USD/TRY_AVG",
    "USDTRY",
    "USD/TRY"
]

for (String key : usdTryKeys) {
    if (inputRates.containsKey(key)) {
        usdTryAvgRate = inputRates.get(key)
        log.info("✅ USD/TRY input found with key: {}", key)
        break
    }
}

if (!usdTryAvgRate) {
    log.error("❌ USD/TRY input not found. Available keys: {}", inputRates.keySet().join(", "))
    return null
}

// Find EUR/USD input with multiple key formats
def eurUsdKeys = [
    eurUsdAvgKey,
    "EURUSD_AVG",
    "EUR/USD_AVG", 
    "EURUSD",
    "EUR/USD"
]

for (String key : eurUsdKeys) {
    if (inputRates.containsKey(key)) {
        eurUsdAvgRate = inputRates.get(key)
        log.info("✅ EUR/USD input found with key: {}", key)
        break
    }
}

if (!eurUsdAvgRate) {
    log.error("❌ EUR/USD input not found. Available keys: {}", inputRates.keySet().join(", "))
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
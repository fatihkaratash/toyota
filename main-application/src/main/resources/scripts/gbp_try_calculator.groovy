import com.toyota.mainapp.dto.model.BaseRateDto  // ✅ CORRECT PATH
import com.toyota.mainapp.dto.model.RateType      // ✅ CORRECT PATH  
import com.toyota.mainapp.dto.model.InputRateInfo // ✅ CORRECT PATH

import java.math.BigDecimal
import java.math.RoundingMode

// ✅ SCRIPT PARAMETERS FROM CONFIG - Default values if not provided  
def gbpUsdAvgKey = gbpUsdAvgKey ?: "GBPUSD_AVG"
def usdTryAvgSourceKey = usdTryAvgSourceKey ?: "USDTRY_AVG"
def defaultScale = defaultScale ?: "5"

// Log all input variables at the start of the script
log.info("GBP/TRY çapraz kur hesaplaması başlatılıyor: {}", outputSymbol)
log.info("İşlem parametreleri: gbpUsdAvgKey={}, usdTryAvgSourceKey={}, defaultScale={}",
    gbpUsdAvgKey, usdTryAvgSourceKey, defaultScale)

// Log all available input rates for debugging
if (inputRates) {
    log.info("Kullanılabilir giriş kurları ({}): {}", inputRates.size(),
        inputRates.keySet().join(", "))
} else {
    log.warn("GBP/TRY için inputRates null veya boş!")
}

def scale = defaultScale.toInteger()
def roundingMode = RoundingMode.HALF_UP
def calculationInputs = []

// ✅ ENHANCED: Smart input resolution using multiple key formats
def usdTryAvgRate = null
def gbpUsdAvgRate = null

// ✅ CONFIG-DRIVEN: Try multiple key formats for USD/TRY input
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

// ✅ CONFIG-DRIVEN: Try multiple key formats for GBP/USD input
def gbpUsdKeys = [
    gbpUsdAvgKey,
    "GBPUSD_AVG",
    "GBP/USD_AVG", 
    "GBPUSD",
    "GBP/USD"
]

for (String key : gbpUsdKeys) {
    if (inputRates.containsKey(key)) {
        gbpUsdAvgRate = inputRates.get(key)
        log.info("✅ GBP/USD input found with key: {}", key)
        break
    }
}

if (!gbpUsdAvgRate) {
    log.error("❌ GBP/USD input not found. Available keys: {}", inputRates.keySet().join(", "))
    return null
}

// ✅ VALIDATION: Check rate data quality
if (usdTryAvgRate.bid == null || usdTryAvgRate.ask == null) {
    log.error("❌ USD/TRY rate has null bid/ask values")
    return null
}

if (gbpUsdAvgRate.bid == null || gbpUsdAvgRate.ask == null) {
    log.error("❌ GBP/USD rate has null bid/ask values") 
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

log.info("GBP/USD Kuru BULUNDU: bid={}, ask={}, timestamp={}", 
    gbpUsdAvgRate.bid, gbpUsdAvgRate.ask, gbpUsdAvgRate.timestamp)

calculationInputs.add(
    InputRateInfo.builder()
        .symbol(gbpUsdAvgRate.symbol)
        .rateType(gbpUsdAvgRate.rateType.toString())
        .providerName(gbpUsdAvgRate.providerName ?: "Calculated")
        .bid(gbpUsdAvgRate.bid)
        .ask(gbpUsdAvgRate.ask)
        .timestamp(gbpUsdAvgRate.timestamp)
        .build()
)

// Calculate GBP/TRY cross rate: (GBP/USD_AVG) * (USD/TRY_AVG)
// Convert to BigDecimal if needed - ensure proper numeric calculation
def calculatedBid = null  // ✅ FIX: calculatedBid instead of calculateBid
def calculatedAsk = null  // ✅ FIX: calculatedAsk instead of calculateAsk

// Ensure we're working with BigDecimal for all calculations
if (gbpUsdAvgRate.bid instanceof BigDecimal && usdTryAvgRate.bid instanceof BigDecimal) {
    // Direct calculation with BigDecimal
    calculatedBid = gbpUsdAvgRate.bid.multiply(usdTryAvgRate.bid).setScale(scale, roundingMode)
    calculatedAsk = gbpUsdAvgRate.ask.multiply(usdTryAvgRate.ask).setScale(scale, roundingMode)
} else {
    // Convert to BigDecimal if needed (defensive)
    BigDecimal gbpUsdBid = new BigDecimal(gbpUsdAvgRate.bid.toString())
    BigDecimal gbpUsdAsk = new BigDecimal(gbpUsdAvgRate.ask.toString())
    BigDecimal usdTryBid = new BigDecimal(usdTryAvgRate.bid.toString())
    BigDecimal usdTryAsk = new BigDecimal(usdTryAvgRate.ask.toString())
    
    calculatedBid = gbpUsdBid.multiply(usdTryBid).setScale(scale, roundingMode)
    calculatedAsk = gbpUsdAsk.multiply(usdTryAsk).setScale(scale, roundingMode)
}

// Verify calculation with log message
log.info("GBP/TRY Calculation: {}*{}={} and {}*{}={}", 
    gbpUsdAvgRate.bid, usdTryAvgRate.bid, calculatedBid,
    gbpUsdAvgRate.ask, usdTryAvgRate.ask, calculatedAsk)
    
def currentTimestamp = System.currentTimeMillis()

log.info("Hesaplanan GBP/TRY ({}): Bid={}, Ask={}", outputSymbol, calculatedBid, calculatedAsk)

// Make sure we're always using the expected format
String finalOutputSymbol = outputSymbol ?: "GBP/TRY"

// ✅ ARCHITECTURE: Return format optimized for pipeline
return [
    symbol: outputSymbol ?: "GBPTRY_CROSS",
    bid: calculatedBid,
    ask: calculatedAsk,
    rateTimestamp: System.currentTimeMillis(),
    rateType: RateType.CALCULATED.toString(),
    providerName: "GbpTryScriptCalculator",
    calculationInputs: calculationInputs,
    calculatedByStrategy: "scripts/gbp_try_calculator.groovy"
]
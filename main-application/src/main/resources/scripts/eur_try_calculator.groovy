import com.toyota.mainapp.dto.model.BaseRateDto  // ✅ CORRECT PATH
import com.toyota.mainapp.dto.model.RateType      // ✅ CORRECT PATH
import com.toyota.mainapp.dto.model.InputRateInfo // ✅ CORRECT PATH

import java.math.BigDecimal
import java.math.RoundingMode

// ✅ SCRIPT PARAMETERS FROM CONFIG - Default values if not provided
def eurUsdAvgKey = eurUsdAvgKey ?: "EURUSD_AVG"
def usdTryAvgSourceKey = usdTryAvgSourceKey ?: "USDTRY_AVG"  
def defaultScale = defaultScale ?: "5"

// Log all input variables at the start of the script
log.info("EUR/TRY çapraz kur hesaplaması başlatılıyor: {}", outputSymbol)
log.info("İşlem parametreleri: eurUsdAvgKey={}, usdTryAvgSourceKey={}, defaultScale={}",
    eurUsdAvgKey, usdTryAvgSourceKey, defaultScale)

// Log all available input rates for debugging
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
    // Try both with and without slash format
    def altUsdTryKey = usdTryAvgSourceKey.contains("/") ? 
        usdTryAvgSourceKey.replace("/", "") : 
        usdTryAvgSourceKey.substring(0, 3) + "/" + usdTryAvgSourceKey.substring(3)
    
    log.info("USD/TRY kuru '{}' anahtarıyla bulunamadı, alternatif anahtar deneniyor: {}", 
        usdTryAvgSourceKey, altUsdTryKey)
    usdTryAvgRate = inputRates.get(altUsdTryKey)
    
    if (!usdTryAvgRate) {
        log.error("EUR/TRY hesaplaması için gerekli ortalama USD/TRY kuru eksik: {} ve {} (inputRates üzerinden alınamadı)", 
            usdTryAvgSourceKey, altUsdTryKey)
        log.debug("Kullanılabilir anahtarlar: {}", inputRates.keySet().join(", "))
        return null
    }
    log.info("USD/TRY kuru '{}' alternatif anahtarıyla BULUNDU", altUsdTryKey)
}

if (usdTryAvgRate.bid == null || usdTryAvgRate.ask == null) {
    log.error("USD/TRY_AVG kuru ({}) için bid/ask değerleri null. {} hesaplanamıyor.", 
        usdTryAvgSourceKey, outputSymbol)
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

// Get EUR/USD average rate from inputRates map
def eurUsdAvgRate = inputRates.get(eurUsdAvgKey)
log.debug("İlk bakışta EUR/USD kuru için {} anahtarı ile sonuç: {}", 
    eurUsdAvgKey, eurUsdAvgRate != null ? "BULUNDU" : "BULUNAMADI")

if (!eurUsdAvgRate) {
    // Try both with and without slash format
    def altEurUsdKey = eurUsdAvgKey.contains("/") ? 
        eurUsdAvgKey.replace("/", "") : 
        eurUsdAvgKey.substring(0, 3) + "/" + eurUsdAvgKey.substring(3)
    
    log.info("EUR/USD kuru '{}' anahtarıyla bulunamadı, alternatif anahtar deneniyor: {}", 
        eurUsdAvgKey, altEurUsdKey)
    eurUsdAvgRate = inputRates.get(altEurUsdKey)
    
    if (!eurUsdAvgRate) {
        log.error("EUR/TRY hesaplaması için gerekli ortalama EUR/USD kuru eksik: {} ve {} (inputRates üzerinden alınamadı)", 
            eurUsdAvgKey, altEurUsdKey)
        log.debug("Kullanılabilir anahtarlar: {}", inputRates.keySet().join(", "))
        return null
    }
    log.info("EUR/USD kuru '{}' alternatif anahtarıyla BULUNDU", altEurUsdKey)
}

if (eurUsdAvgRate.bid == null || eurUsdAvgRate.ask == null) {
    log.error("EUR/USD_AVG kuru ({}) için bid/ask değerleri null. {} hesaplanamıyor.", 
        eurUsdAvgKey, outputSymbol)
    return null
}

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
// Convert to BigDecimal if needed - ensure proper numeric calculation
def calculatedBid = null  // ✅ FIX: calculatedBid instead of calculateBid
def calculatedAsk = null  // ✅ FIX: calculatedAsk instead of calculateAsk

// Ensure we're working with BigDecimal for all calculations
if (eurUsdAvgRate.bid instanceof BigDecimal && usdTryAvgRate.bid instanceof BigDecimal) {
    // Direct calculation with BigDecimal
    calculatedBid = eurUsdAvgRate.bid.multiply(usdTryAvgRate.bid).setScale(scale, roundingMode)
    calculatedAsk = eurUsdAvgRate.ask.multiply(usdTryAvgRate.ask).setScale(scale, roundingMode)
} else {
    // Convert to BigDecimal if needed (defensive)
    BigDecimal eurUsdBid = new BigDecimal(eurUsdAvgRate.bid.toString())
    BigDecimal eurUsdAsk = new BigDecimal(eurUsdAvgRate.ask.toString())
    BigDecimal usdTryBid = new BigDecimal(usdTryAvgRate.bid.toString())
    BigDecimal usdTryAsk = new BigDecimal(usdTryAvgRate.ask.toString())
    
    calculatedBid = eurUsdBid.multiply(usdTryBid).setScale(scale, roundingMode)
    calculatedAsk = eurUsdAsk.multiply(usdTryAsk).setScale(scale, roundingMode)
}

// Verify calculation with log message
log.info("EUR/TRY Calculation: {}*{}={} and {}*{}={}", 
    eurUsdAvgRate.bid, usdTryAvgRate.bid, calculatedBid,
    eurUsdAvgRate.ask, usdTryAvgRate.ask, calculatedAsk)
    
def currentTimestamp = System.currentTimeMillis()

log.info("Hesaplanan EUR/TRY ({}): Bid={}, Ask={}", outputSymbol, calculatedBid, calculatedAsk)

// Make sure we're always using the expected format
String finalOutputSymbol = outputSymbol ?: "EUR/TRY"

return [
    symbol: finalOutputSymbol,
    bid: calculatedBid,
    ask: calculatedAsk,
    rateTimestamp: currentTimestamp,
    rateType: RateType.CALCULATED.toString(),
    providerName: "EurTryScriptCalculator",
    calculationInputs: calculationInputs,
    calculatedByStrategy: "scripts/eur_try_calculator.groovy"
]
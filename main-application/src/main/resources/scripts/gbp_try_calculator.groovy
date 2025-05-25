import com.toyota.mainapp.dto.BaseRateDto
import com.toyota.mainapp.dto.RateType
import com.toyota.mainapp.dto.common.InputRateInfo

import java.math.BigDecimal
import java.math.RoundingMode

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
        log.error("GBP/TRY hesaplaması için gerekli ortalama USD/TRY kuru eksik: {} ve {} (inputRates üzerinden alınamadı)", 
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

// Get GBP/USD average rate from inputRates map
def gbpUsdAvgRate = inputRates.get(gbpUsdAvgKey)
log.debug("İlk bakışta GBP/USD kuru için {} anahtarı ile sonuç: {}", 
    gbpUsdAvgKey, gbpUsdAvgRate != null ? "BULUNDU" : "BULUNAMADI")

if (!gbpUsdAvgRate) {
    // Try both with and without slash format
    def altGbpUsdKey = gbpUsdAvgKey.contains("/") ? 
        gbpUsdAvgKey.replace("/", "") : 
        gbpUsdAvgKey.substring(0, 3) + "/" + gbpUsdAvgKey.substring(3)
    
    log.info("GBP/USD kuru '{}' anahtarıyla bulunamadı, alternatif anahtar deneniyor: {}", 
        gbpUsdAvgKey, altGbpUsdKey)
    gbpUsdAvgRate = inputRates.get(altGbpUsdKey)
    
    if (!gbpUsdAvgRate) {
        log.error("GBP/TRY hesaplaması için gerekli ortalama GBP/USD kuru eksik: {} ve {} (inputRates üzerinden alınamadı)", 
            gbpUsdAvgKey, altGbpUsdKey)
        log.debug("Kullanılabilir anahtarlar: {}", inputRates.keySet().join(", "))
        return null
    }
    log.info("GBP/USD kuru '{}' alternatif anahtarıyla BULUNDU", altGbpUsdKey)
}

if (gbpUsdAvgRate.bid == null || gbpUsdAvgRate.ask == null) {
    log.error("GBP/USD_AVG kuru ({}) için bid/ask değerleri null. {} hesaplanamıyor.", 
        gbpUsdAvgKey, outputSymbol)
    return null
}

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
def calculatedBid = (gbpUsdAvgRate.bid * usdTryAvgRate.bid).setScale(scale, roundingMode)
def calculatedAsk = (gbpUsdAvgRate.ask * usdTryAvgRate.ask).setScale(scale, roundingMode)
def currentTimestamp = System.currentTimeMillis()

log.info("Hesaplanan GBP/TRY ({}): Bid={}, Ask={}", outputSymbol, calculatedBid, calculatedAsk)

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
import com.toyota.mainapp.dto.model.BaseRateDto
import com.toyota.mainapp.dto.model.RateType
import com.toyota.mainapp.dto.model.InputRateInfo

import java.math.BigDecimal
import java.math.RoundingMode

def gbpUsdAvgKey = gbpUsdAvgKey ?: "GBPUSD_AVG"
def usdTryAvgSourceKey = usdTryAvgSourceKey ?: "USDTRY_AVG"
def defaultScale = defaultScale ?: "5"

def scale = defaultScale.toInteger()
def roundingMode = RoundingMode.HALF_UP
def calculationInputs = []

// Get USD/TRY rate
def usdTryAvgRate = inputRates.get(usdTryAvgSourceKey)
if (!usdTryAvgRate || usdTryAvgRate.bid == null || usdTryAvgRate.ask == null) {
    log.error("Missing USD/TRY rate for GBP/TRY calculation")
    return null
}

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

// Get GBP/USD rate
def gbpUsdAvgRate = inputRates.get(gbpUsdAvgKey)
if (!gbpUsdAvgRate || gbpUsdAvgRate.bid == null || gbpUsdAvgRate.ask == null) {
    log.error("Missing GBP/USD rate for GBP/TRY calculation")
    return null
}

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

// Calculate GBP/TRY cross rate
def calculatedBid = null
def calculatedAsk = null

if (gbpUsdAvgRate.bid instanceof BigDecimal && usdTryAvgRate.bid instanceof BigDecimal) {
    calculatedBid = gbpUsdAvgRate.bid.multiply(usdTryAvgRate.bid).setScale(scale, roundingMode)
    calculatedAsk = gbpUsdAvgRate.ask.multiply(usdTryAvgRate.ask).setScale(scale, roundingMode)
} else {
    BigDecimal gbpUsdBid = new BigDecimal(gbpUsdAvgRate.bid.toString())
    BigDecimal gbpUsdAsk = new BigDecimal(gbpUsdAvgRate.ask.toString())
    BigDecimal usdTryBid = new BigDecimal(usdTryAvgRate.bid.toString())
    BigDecimal usdTryAsk = new BigDecimal(usdTryAvgRate.ask.toString())
    
    calculatedBid = gbpUsdBid.multiply(usdTryBid).setScale(scale, roundingMode)
    calculatedAsk = gbpUsdAsk.multiply(usdTryAsk).setScale(scale, roundingMode)
}

return [
    symbol: outputSymbol ?: "GBP/TRY",
    bid: calculatedBid,
    ask: calculatedAsk,
    rateTimestamp: System.currentTimeMillis(),
    rateType: RateType.CALCULATED.toString(),
    providerName: "GbpTryScriptCalculator",
    calculationInputs: calculationInputs,
    calculatedByStrategy: "scripts/gbp_try_calculator.groovy"
]
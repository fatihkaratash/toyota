import com.toyota.mainapp.dto.model.BaseRateDto
import com.toyota.mainapp.dto.model.RateType
import com.toyota.mainapp.dto.model.InputRateInfo

import java.math.BigDecimal
import java.math.RoundingMode

def eurUsdAvgKey = eurUsdAvgKey ?: "EURUSD_AVG"
def usdTryAvgSourceKey = usdTryAvgSourceKey ?: "USDTRY_AVG"
def defaultScale = defaultScale ?: "5"

def scale = defaultScale.toInteger()
def roundingMode = RoundingMode.HALF_UP
def calculationInputs = []

def usdTryAvgRate = inputRates.get(usdTryAvgSourceKey)
if (!usdTryAvgRate || usdTryAvgRate.bid == null || usdTryAvgRate.ask == null) {
    log.error("Missing USD/TRY rate for EUR/TRY calculation")
    return null
}

def eurUsdAvgRate = inputRates.get(eurUsdAvgKey)
if (!eurUsdAvgRate || eurUsdAvgRate.bid == null || eurUsdAvgRate.ask == null) {
    log.error("Missing EUR/USD rate for EUR/TRY calculation")
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

def calculatedBid = null
def calculatedAsk = null

if (eurUsdAvgRate.bid instanceof BigDecimal && usdTryAvgRate.bid instanceof BigDecimal) {
    calculatedBid = eurUsdAvgRate.bid.multiply(usdTryAvgRate.bid).setScale(scale, roundingMode)
    calculatedAsk = eurUsdAvgRate.ask.multiply(usdTryAvgRate.ask).setScale(scale, roundingMode)
} else {
    BigDecimal eurUsdBid = new BigDecimal(eurUsdAvgRate.bid.toString())
    BigDecimal eurUsdAsk = new BigDecimal(eurUsdAvgRate.ask.toString())
    BigDecimal usdTryBid = new BigDecimal(usdTryAvgRate.bid.toString())
    BigDecimal usdTryAsk = new BigDecimal(usdTryAvgRate.ask.toString())
    
    calculatedBid = eurUsdBid.multiply(usdTryBid).setScale(scale, roundingMode)
    calculatedAsk = eurUsdAsk.multiply(usdTryAsk).setScale(scale, roundingMode)
}

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
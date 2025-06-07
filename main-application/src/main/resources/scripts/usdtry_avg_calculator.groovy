import com.toyota.mainapp.dto.model.BaseRateDto
import com.toyota.mainapp.dto.model.RateType
import java.math.BigDecimal
import java.math.RoundingMode

log.info("USD/TRY AVG calculation started: {}", outputSymbol)

def scale = Integer.parseInt(defaultScale ?: "5")
def roundingMode = RoundingMode.HALF_UP

// Get all USDTRY rates from input
def usdTryRates = inputRates.values().findAll { 
    it.symbol?.contains("USDTRY") || it.symbol?.contains("USD/TRY")
}

if (usdTryRates.isEmpty()) {
    log.error("No USDTRY rates found for average calculation")
    return null
}

log.info("Found {} USDTRY rates for averaging", usdTryRates.size())

// Calculate average bid/ask
BigDecimal totalBid = BigDecimal.ZERO
BigDecimal totalAsk = BigDecimal.ZERO
int count = 0

usdTryRates.each { rate ->
    if (rate.bid != null && rate.ask != null) {
        totalBid = totalBid.add(new BigDecimal(rate.bid.toString()))
        totalAsk = totalAsk.add(new BigDecimal(rate.ask.toString()))
        count++
        log.debug("Added rate: bid={}, ask={}", rate.bid, rate.ask)
    }
}

if (count == 0) {
    log.error("No valid bid/ask values found")
    return null
}

BigDecimal avgBid = totalBid.divide(new BigDecimal(count), scale, roundingMode)
BigDecimal avgAsk = totalAsk.divide(new BigDecimal(count), scale, roundingMode)

log.info("USD/TRY Average calculated: bid={}, ask={} (from {} rates)", avgBid, avgAsk, count)

return [
    symbol: "USDTRY",
    bid: avgBid,
    ask: avgAsk,
    timestamp: System.currentTimeMillis(),
    rateType: RateType.CALCULATED.toString(),
    providerName: "AvgCalculator"
]

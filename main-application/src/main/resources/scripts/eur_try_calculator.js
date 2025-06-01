/**
 * EUR/TRY çapraz kuru hesaplama fonksiyonu
 */
function calculateRate(inputRates, outputSymbol) {
    console.log("EUR/TRY çapraz kur hesaplaması başlatılıyor: " + outputSymbol);
    console.log("İşlem parametreleri: eurUsdAvgKey=" + eurUsdAvgKey + 
                ", usdTryAvgSourceKey=" + usdTryAvgSourceKey + 
                ", defaultScale=" + defaultScale);

    var scale = parseInt(defaultScale);
    var calculationInputs = [];
    
    // 1. USD/TRY ortalama kurunu al
    var usdTryAvgRate = inputRates[usdTryAvgSourceKey];
    if (!usdTryAvgRate) {
        // Alternatif formatta veya doğrudan anahtar ile dene
        var altKeys = Object.keys(inputRates).filter(k => k.includes("USDTRY") || k.includes("USD/TRY"));
        if (altKeys.length > 0) {
            console.log("USD/TRY için alternatif anahtar kullanılıyor: " + altKeys[0]);
            usdTryAvgRate = inputRates[altKeys[0]];
        }
        
        if (!usdTryAvgRate) {
            console.error("EUR/TRY hesaplaması için gerekli USD/TRY kuru bulunamadı");
            return null;
        }
    }
    
    // 2. EUR/USD ortalama kurunu al
    var eurUsdAvgRate = inputRates[eurUsdAvgKey];
    if (!eurUsdAvgRate) {
        // Alternatif formatta veya doğrudan anahtar ile dene
        var altKeys = Object.keys(inputRates).filter(k => k.includes("EURUSD") || k.includes("EUR/USD"));
        if (altKeys.length > 0) {
            console.log("EUR/USD için alternatif anahtar kullanılıyor: " + altKeys[0]);
            eurUsdAvgRate = inputRates[altKeys[0]];
        }
        
        if (!eurUsdAvgRate) {
            console.error("EUR/TRY hesaplaması için gerekli EUR/USD kuru bulunamadı");
            return null;
        }
    }
    
    // Giriş kurlarını hesaplama girdilerine ekle
    calculationInputs.push({
        symbol: usdTryAvgRate.symbol,
        rateType: usdTryAvgRate.rateType,
        providerName: usdTryAvgRate.providerName || "Calculated",
        bid: usdTryAvgRate.bid,
        ask: usdTryAvgRate.ask,
        timestamp: usdTryAvgRate.timestamp
    });
    
    calculationInputs.push({
        symbol: eurUsdAvgRate.symbol,
        rateType: eurUsdAvgRate.rateType,
        providerName: eurUsdAvgRate.providerName || "Calculated",
        bid: eurUsdAvgRate.bid,
        ask: eurUsdAvgRate.ask,
        timestamp: eurUsdAvgRate.timestamp
    });
    
    // ÖNEMLİ DÜZELTME: String değerleri Number'a çevir ve sonra çarp
    var bidPrice = Number(eurUsdAvgRate.bid) * Number(usdTryAvgRate.bid);
    var askPrice = Number(eurUsdAvgRate.ask) * Number(usdTryAvgRate.ask);
    
    // Ondalık kısmı doğru formatla (10^scale ile çarpıp bölerek)
    bidPrice = Math.round(bidPrice * Math.pow(10, scale)) / Math.pow(10, scale);
    askPrice = Math.round(askPrice * Math.pow(10, scale)) / Math.pow(10, scale);
    
    console.log("EUR/TRY hesaplaması tamamlandı: bid=" + bidPrice + ", ask=" + askPrice);
    
    // Sonucu döndür
    return {
        bid: bidPrice,
        ask: askPrice,
        calculationInputs: calculationInputs
    };
}
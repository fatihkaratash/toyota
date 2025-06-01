/**
 * GBP/TRY çapraz kuru hesaplama fonksiyonu
 */
function calculateRate(inputRates, outputSymbol) {
    console.log("GBP/TRY çapraz kur hesaplaması başlatılıyor: " + outputSymbol);
    console.log("İşlem parametreleri: gbpUsdAvgKey=" + gbpUsdAvgKey + 
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
            console.error("GBP/TRY hesaplaması için gerekli USD/TRY kuru bulunamadı");
            return null;
        }
    }
    
    // 2. GBP/USD ortalama kurunu al
    var gbpUsdAvgRate = inputRates[gbpUsdAvgKey];
    if (!gbpUsdAvgRate) {
        // Alternatif formatta veya doğrudan anahtar ile dene
        var altKeys = Object.keys(inputRates).filter(k => k.includes("GBPUSD") || k.includes("GBP/USD"));
        if (altKeys.length > 0) {
            console.log("GBP/USD için alternatif anahtar kullanılıyor: " + altKeys[0]);
            gbpUsdAvgRate = inputRates[altKeys[0]];
        }
        
        if (!gbpUsdAvgRate) {
            console.error("GBP/TRY hesaplaması için gerekli GBP/USD kuru bulunamadı");
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
        symbol: gbpUsdAvgRate.symbol,
        rateType: gbpUsdAvgRate.rateType,
        providerName: gbpUsdAvgRate.providerName || "Calculated",
        bid: gbpUsdAvgRate.bid,
        ask: gbpUsdAvgRate.ask,
        timestamp: gbpUsdAvgRate.timestamp
    });
    
    // ÖNEMLİ DÜZELTME: String değerleri Number'a çevir ve sonra çarp
    var bidPrice = Number(gbpUsdAvgRate.bid) * Number(usdTryAvgRate.bid);
    var askPrice = Number(gbpUsdAvgRate.ask) * Number(usdTryAvgRate.ask);
    
    // Ondalık kısmı doğru formatla
    bidPrice = Math.round(bidPrice * Math.pow(10, scale)) / Math.pow(10, scale);
    askPrice = Math.round(askPrice * Math.pow(10, scale)) / Math.pow(10, scale);
    
    console.log("GBP/TRY hesaplaması tamamlandı: bid=" + bidPrice + ", ask=" + askPrice);
    
    // Sonucu döndür
    return {
        bid: bidPrice,
        ask: askPrice,
        calculationInputs: calculationInputs
    };
}
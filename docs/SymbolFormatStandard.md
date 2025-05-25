# Sembol Format Standardı

Bu doküman, Toyota kur sistemi içinde kullanılan sembol formatlarını standartlaştırır.

## Ham (Raw) Kur Sembolleri
Ham kurlar, veri sağlayıcılarından doğrudan alınan kurlardır.

**Format:** `{PROVIDER_NAME}_{BASE_CURRENCY}{QUOTE_CURRENCY}`

Örnekler:
- `RESTProvider1_USDTRY` - REST sağlayıcısından USD/TRY kuru
- `TCPProvider2_EURUSD` - TCP sağlayıcısından EUR/USD kuru

## Hesaplanmış (Calculated) Kur Sembolleri
Hesaplanmış kurlar, bir veya daha fazla ham kurdan türetilmiş kurlardır.

### 1. Ortalama Kurlar
**Format:** `{BASE_CURRENCY}{QUOTE_CURRENCY}_AVG`

Örnekler:
- `USDTRY_AVG` - USD/TRY için hesaplanmış ortalama kur
- `EURUSD_AVG` - EUR/USD için hesaplanmış ortalama kur

### 2. Çapraz Kurlar
**Format:** `{BASE_CURRENCY}/{QUOTE_CURRENCY}` veya `{BASE_CURRENCY}/{QUOTE_CURRENCY}_CROSS`

Örnekler:
- `EUR/TRY` - EUR/TRY çapraz kuru (EURUSD_AVG ve USDTRY_AVG kullanılarak hesaplanır)
- `GBP/TRY` - GBP/TRY çapraz kuru (GBPUSD_AVG ve USDTRY_AVG kullanılarak hesaplanır)

## Sembol Dönüşümleri
Sistemin bazı bileşenleri, eğik çizgi (slash) içeren formatları desteklemek için aşağıdaki dönüşümleri gerçekleştirir:

1. Eğik çizgili format: `USD/TRY_AVG` (görsel olarak daha okunabilir)
2. Eğik çizgisiz format: `USDTRY_AVG` (teknik işlemler için tercih edilir)

Her iki format da sistemde eşdeğer olarak kabul edilir ve gerektiğinde otomatik olarak dönüştürülür.

## Önbellek Anahtarları
RateCache servisindeki anahtarlar aşağıdaki formatta olmalıdır:

- Ham kurlar için: `{PROVIDER_NAME}_{BASE_CURRENCY}{QUOTE_CURRENCY}`
- Hesaplanmış kurlar için: `{BASE_CURRENCY}{QUOTE_CURRENCY}_AVG` veya `{BASE_CURRENCY}/{QUOTE_CURRENCY}`

## Kurallar ve Kısıtlamalar
1. Para birimi kodları her zaman 3 karakter uzunluğunda olmalıdır (ISO 4217 standardı)
2. Sağlayıcı adları, alt çizgi (_) içermemelidir
3. Hesaplama tipleri (_AVG, _CROSS gibi) büyük harfle yazılmalıdır

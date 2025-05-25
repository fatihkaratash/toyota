Market Trend Simulation Implementation Guide
Overview
This document outlines the plan for implementing market trend simulation capabilities in both TCP and REST rate providers. The goal is to create a system where financial market conditions (bull market, bear market, or neutral market) can be simulated with configurable parameters.

Core Requirements
Basic Trend Modes:

BULL - Upward trending market (prices tend to rise)
NEUTRAL - No specific trend direction (current default behavior)
BEAR - Downward trending market (prices tend to fall)
Centralized Configuration: Both providers should follow the same market trend

Runtime Control: The ability to change market conditions during runtime

Docker Integration: Configure initial trend settings via Docker environment variables

Architecture
┌─────────────────────┐      ┌───────────────────────┐
│                     │      │                       │
│  TCP Rate Provider  │◄────►│        Redis          │◄─────┐
│                     │      │                       │      │
└─────────────────────┘      └───────────────────────┘      │
                                                            │
                             ┌───────────────────────┐      │
                             │                       │      │
                             │  REST Rate Provider   │◄─────┘
                             │                       │
                             └───────────────────────┘
                                     │
                                     │
                                     ▼
                             ┌───────────────────────┐
                             │                       │
                             │    Admin API (REST)   │
                             │                       │
                             └───────────────────────┘

Common Library Components
1. MarketTrendMode Enum
public enum MarketTrendMode {
    BULL,   // Upward trend
    NEUTRAL, // No trend
    BEAR    // Downward trend
}
2. TrendConfiguration Class
public class TrendConfiguration {
    private MarketTrendMode currentMode = MarketTrendMode.NEUTRAL;
    private double currentStrength = 0.5; // 0.0 to 1.0
}
3. MarketTrendEngine Class
public class MarketTrendEngine {
    private TrendConfiguration activeConfig;
    
    public void updateConfiguration(TrendConfiguration newConfig);
    public BigDecimal applyTrend(BigDecimal basePrice, String symbol);
}

Redis Data Model
market:trend:current_mode - Current market trend mode (STRING: "BULL", "NEUTRAL", "BEAR")
market:trend:current_strength - Current trend strength (STRING: "0.0" to "1.0")

Docker Configuration
environment:
  MARKET_TREND_INITIAL_MODE: ${MARKET_TREND_INITIAL_MODE:-NEUTRAL}
  MARKET_TREND_INITIAL_STRENGTH: ${MARKET_TREND_INITIAL_STRENGTH:-0.5}
  TREND_REDIS_POLL_INTERVAL_SECONDS: ${TREND_REDIS_POLL_INTERVAL_SECONDS:-10}

REST API Endpoints (REST Provider Only)
Get Current Market Trend
GET /admin/market/trend
{
  "mode": "BULL",
  "strength": 0.7
}

Set Market Trend
{
  "mode": "BEAR",
  "strength": 0.8
}

Implementation Roadmap
Phase 1: Basic Trend Engine
Create common library module (common-market-trend)

Implement MarketTrendMode enum
Implement TrendConfiguration class with basic properties
Implement MarketTrendEngine with basic trend application logic
Update both providers to use the common library

Integrate MarketTrendEngine into RateSimulationService (REST)
Integrate MarketTrendEngine into RateFluctuationSimulator (TCP)
Apply trend bias to rate generation logic
Configure initial trend from Docker environment variables

Add application.yml configurations to read environment variables
Initialize MarketTrendEngine with these configurations
Test basic trend functionality

Verify that rates change according to trend settings
Test different trend modes and strengths
Output: Both providers can independently simulate market trends using Docker environment variables.

Phase 2: Redis Integration for Central Configuration
Add Redis dependency to both providers

Configure Redis connection in application.yml
Add Redis configuration class
Implement Redis-based trend synchronization

Create MarketTrendSynchronizer class in common library
Implement reading from Redis (periodic polling)
Update MarketTrendEngine with values from Redis
Implement REST API in REST provider for trend management

Create controller with GET and PUT endpoints
Update Redis when trend settings change via API
Handle Redis failure scenarios

Implement fallback to last known configuration
Add logging for Redis connection issues
Output: Both providers synchronize their market trend settings via Redis. REST API can control the trend settings.

Phase 3 (Optional): Automatic Trend Cycling
Enhance common library with cycling capability

Add TrendCycleManager class
Implement scheduled task to cycle between trends
Configure cycle parameters via Docker

Add cycle-related environment variables
Update Redis with cycle status and parameters
Expose cycle control via REST API

Add endpoints to enable/disable cycling
Add endpoints to configure cycle duration
Output: Automatic cycling between market trends with configurable parameters.

Integration with Existing Code
RateSimulationService (REST)
Location to modify:
// c:\Projects\toyota\data-providers\rest-rate-provider\src\main\java\com\toyota\restserver\service\RateSimulationService.java
public Rate simulateFluctuation(Rate baseRate) {
    // Existing code
    double change = (random.nextDouble() - 0.5) * 2 * midPrice * volatility;
    
    // Add trend bias using MarketTrendEngine
    double trendBias = marketTrendEngine.calculateTrendBias(baseRate.getPairName());
    change += trendBias;
    
    // Continue with existing code
}
RateFluctuationSimulator (TCP)
Similar integration in the TCP provider's simulator class.

Add appropriate logging to track:

Market trend changes
Redis synchronization events
Application of trend bias to rates


/*Genel Sistem Mimarisi & Temel Gereksinimler (Aynı Kalıyor)
Temel Gereksinimler:
REST ve TCP provider'ları için ortak trend motoru.
BULL, NEUTRAL, BEAR modları.
(Başlangıç için Opsiyonel, İleri Aşama) Otomatik döngü.
Docker üzerinden merkezi yapılandırma.
Sistem Mimarisi:
Ortak Kütüphane Yaklaşımı: Kesinlikle evet.
Merkezi Yapılandırma: Docker Compose environment değişkenleri üzerinden başlangıç değerleri.
Durum Senkronizasyonu (Basitleştirilmiş): Redis üzerinden sadece mevcut trend modunun ve gücünün paylaşımı. Provider'lar periyodik olarak Redis'i okur.
Çalışma Zamanı Kontrolü (Odaklı): Sadece REST Rate Provider üzerinden yönetim endpoint'leri (bu endpoint'ler Redis'i günceller).
Ortak Kütüphane Tasarımı (common-trend-simulator gibi bir modül)
2.1. Temel Bileşenler:
MarketTrendMode.java (enum: BULL, NEUTRAL, BEAR).
TrendConfiguration.java (POJO):
currentMode (MarketTrendMode)
currentStrength (double, 0.0-1.0)
Not: Döngü, volatilite, sembol bazlı ayarlar bu ilk basit versiyonda olmayacak.
MarketTrendEngine.java:
private TrendConfiguration activeConfig;
public void updateConfiguration(TrendConfiguration newConfig);
public BigDecimal applyTrend(BigDecimal basePrice, String symbol); // Şimdilik sembolü kullanmayabilir, genel trendi uygular.
2.2. Bileşen Sorumlulukları:
MarketTrendEngine: activeConfig'e göre fiyat sapması hesaplar. updateConfiguration ile dışarıdan güncellenebilir.
Trend Algoritması (Basit ve Etkili)
3.1. Bull Mode: Fiyata pozitif bir sapma ekler. Sapma miktarı = basePrice * trendStrength * (pozitif_bir_katsayı) * rastgele_faktör.
3.2. Bear Mode: Fiyata negatif bir sapma ekler. Sapma miktarı = basePrice * trendStrength * (negatif_bir_katsayı) * rastgele_faktör.
3.3. Neutral Mode: Mevcut rastgele dalgalanma korunur veya çok küçük, yönsüz bir sapma eklenir.
Not: Spread ayarlamaları bu ilk basit versiyonda olmayacak.
Döngü Mekanizması Tasarımı (Bu İlk Basit Versiyonda YOK)
Otomatik döngü, yumuşak geçiş, olay bildirimi gibi özellikler sonraya bırakılacak. Amacımız önce temel modları çalıştırmak.
Redis Senkronizasyon Tasarımı (Basitleştirilmiş)
5.1. Veri Modeli (Redis'te):
market:trend:current_mode (String: "BULL", "NEUTRAL", "BEAR")
market:trend:current_strength (String: "0.0" - "1.0")
5.2. Senkronizasyon Mekanizması:
Yazma (Sadece REST Provider Yönetim API'si yapar): REST API üzerinden bir trend değişikliği komutu geldiğinde, REST provider bu iki anahtarı Redis'te günceller.
Okuma (Hem TCP hem de REST Provider yapar):
Her iki provider da periyodik olarak (örn: application.yml'den ayarlanabilir bir trend.redis.poll-interval-seconds ile her 5-10 saniyede bir) Redis'teki current_mode ve current_strength değerlerini okur.
Okuduğu bu değerlerle kendi içindeki MarketTrendEngine'in activeConfig'ini günceller.
Not: Redis Pub/Sub bu ilk basit versiyonda olmayacak.
Docker Yapılandırma Tasarımı
6.1. Environment Değişkenleri (docker-compose.yml ve .env dosyası):
MARKET_TREND_INITIAL_MODE (Default: NEUTRAL)
MARKET_TREND_INITIAL_STRENGTH (Default: 0.5)
TREND_REDIS_POLL_INTERVAL_SECONDS (Default: 10)
(Döngü ile ilgili değişkenler şimdilik yok)
6.2. Docker Compose Entegrasyonu:
Her iki provider servisi de bu environment değişkenlerini alır.
Provider'lar başlangıçta bu "initial" değerleri Redis'e yazabilir (eğer Redis'te henüz bu anahtarlar yoksa, lider seçimi gibi bir karmaşıklığa girmeden, örneğin REST provider bunu yapabilir).
REST API Yönetim Arayüzü (SADECE rest-rate-provider için)
7.1. Endpoint'ler:
GET /admin/market/trend: Redis'teki mevcut current_mode ve current_strength'i okuyup döndürür.
PUT /admin/market/trend:
Request Body: { "mode": "BULL", "strength": 0.7 }
Bu endpoint, Redis'teki market:trend:current_mode ve market:trend:current_strength anahtarlarını günceller.
7.2. TCP Rate Provider: Bu API'yi sunmaz. Sadece Redis'i dinleyerek güncellenir.
Entegrasyon Stratejisi
8.1. RateSimulationService (REST) ve RateFluctuationSimulator (TCP):
Her ikisi de ortak kütüphanedeki MarketTrendEngine'i kullanır.
Başlangıçta Docker environment değişkenlerinden gelen INITIAL_MODE ve INITIAL_STRENGTH ile TrendConfiguration'ı ve dolayısıyla MarketTrendEngine'i başlatırlar.
Periyodik olarak (veya her fiyat üretiminden önce) Redis'ten current_mode ve current_strength'i okuyarak kendi MarketTrendEngine'lerini güncellerler.
Fiyat üretirken MarketTrendEngine.applyTrend() metodunu çağırırlar.
İş Planı ve Öncelikler (Basitleştirilmiş Fazlar):
Faz 1: Temel Trend Motoru ve Manuel Başlangıç Ayarları
Ortak kütüphaneyi oluştur: MarketTrendMode, TrendConfiguration (sadece currentMode, currentStrength ile), MarketTrendEngine (basit applyTrend ile).
Her iki provider da başlangıçta Docker environment değişkenlerinden (MARKET_TREND_INITIAL_MODE, MARKET_TREND_INITIAL_STRENGTH) trendi okuyup, kendi MarketTrendEngine'lerini bu ayarlarla başlatsın. Fiyatlar bu trende göre değişsin.
Bu fazda Redis veya API yok. Sadece başlangıçta ayarlanan trend çalışır.
Faz 2: Redis ile Merkezi Trend Okuma ve REST API ile Manuel Değiştirme
docker-compose.yml'e Redis servisini ekle.
REST Provider:
PUT /admin/market/trend endpoint'ini ekle. Bu endpoint, Redis'teki market:trend:current_mode ve market:trend:current_strength anahtarlarını güncellesin.
GET /admin/market/trend endpoint'ini ekle (Redis'ten okur).
Hem TCP hem de REST Provider:
Periyodik olarak Redis'teki bu iki anahtarı okuyup kendi MarketTrendEngine'lerini güncellesinler.
Başlangıçta, eğer Redis'te bu anahtarlar yoksa, Docker environment'tan gelen INITIAL değerleri kullansınlar (ve REST provider bunları Redis'e yazabilir).
Faz 3 (İleri Seviye - Eğer Zaman Kalırsa): Otomatik Döngü
Ortak kütüphaneye basit bir TrendCycleManager ekle (BULL->NEUTRAL->BEAR->NEUTRAL döngüsü).
REST Provider: @Scheduled bir task ile bu döngüyü çalıştırsın ve her trend değişiminde Redis'teki market:trend:current_mode'u güncellesin. Döngünün aktif olup olmadığı ve süresi Docker environment'tan (MARKET_TREND_CYCLE_ENABLED, MARKET_TREND_CYCLE_MINUTES) alınabilir ve Redis'e yazılabilir.
TCP provider yine Redis'i okuyarak döngüye dahil olur.
Dikkat Edilmesi Gereken Noktalar (Aynı Kalıyor, Önemli!)
Kod Tekrarı Önlenmeli.
Hata Toleransı (Redis bağlantısı koparsa ne olacak? Provider'lar varsayılan/son bilinen trendle devam edebilir veya NEUTRAL'a dönebilir).*/
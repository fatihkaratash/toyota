# Common Market Trend Module

This module provides market trend simulation capabilities for rate providers.

## Overview

The common-market-trend module implements market simulation functionality that can be shared between
different rate provider services (TCP, REST, etc.). It allows for centrally managed market conditions
that affect how rate fluctuations are generated.

## Core Components

### Domain Classes

- **MarketTrendMode** - Enum defining market trend directions:
  - `BULL` - Upward trending market (prices tend to rise)
  - `NEUTRAL` - No specific trend direction
  - `BEAR` - Downward trending market (prices tend to fall)

- **TrendConfiguration** - Configuration class with:
  - Current mode
  - Trend strength (0.0 to 1.0)

- **MarketTrendEngine** - Core logic for applying trends to rates:
  - Calculates bias based on mode and strength
  - Thread-safe configuration updates

### Redis Integration

- **RedisTrendClient** - Interface for Redis operations
- **JedisRedisTrendClient** - Implementation using Jedis
- **MarketTrendSynchronizer** - Centralized trend management via Redis:
  - Periodic Redis polling
  - Fallback configuration
  - Redis publishing

## Integration

### Maven Dependency

```xml
<dependency>
    <groupId>com.toyota</groupId>
    <artifactId>common-market-trend</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Basic Usage

```java
// Create an engine with default settings (NEUTRAL mode)
MarketTrendEngine engine = new MarketTrendEngine();

// Apply trend bias to price calculations
double basePrice = 100.0;
double trendBias = engine.calculateTrendBias(basePrice, "USDTRY");
double fluctuatedPrice = basePrice + randomChange + trendBias;
```

### With Redis Synchronization

```java
// Create Redis client implementation
JedisPool jedisPool = new JedisPool("redis-host", 6379);
RedisTrendClient redisClient = new JedisRedisTrendClient(jedisPool);

// Initial fallback configuration
TrendConfiguration initialConfig = new TrendConfiguration(
    MarketTrendMode.NEUTRAL, 0.5
);

// Create trend engine
MarketTrendEngine engine = new MarketTrendEngine(initialConfig);

// Create synchronizer
MarketTrendSynchronizer synchronizer = new MarketTrendSynchronizer(
    engine, redisClient, initialConfig
);

// Start synchronization (poll every 10 seconds)
synchronizer.startSynchronization(10);
```

## Redis Data Model

- `market:trend:current_mode` - Current market trend mode (STRING: "BULL", "NEUTRAL", "BEAR")
- `market:trend:current_strength` - Current trend strength (STRING: "0.0" to "1.0")

## Environment Variables

The module supports configuration via environment variables:

- `MARKET_TREND_INITIAL_MODE` - Initial market trend mode (default: NEUTRAL)
- `MARKET_TREND_INITIAL_STRENGTH` - Initial market trend strength (default: 0.5)
- `TREND_REDIS_POLL_INTERVAL_SECONDS` - How often to poll Redis (default: 10)

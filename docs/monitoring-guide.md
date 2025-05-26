# Market Trend Monitoring Guide

This guide explains how to monitor and control the market trend simulation across both REST and TCP rate providers.

## REST API Endpoints

The REST provider offers these endpoints for trend monitoring and control:

### 1. Get Current Trend
```
GET /admin/market/trend
```

Sample response:
```json
{
  "mode": "BULL",
  "strength": 0.7
}
```

### 2. Update Trend
```
PUT /admin/market/trend
```

Request body:
```json
{
  "mode": "BEAR",
  "strength": 0.6
}
```

Sample response:
```json
{
  "mode": "BEAR",
  "strength": 0.6,
  "redisUpdated": true
}
```

Valid modes: `BULL`, `NEUTRAL`, `BEAR`  
Valid strength: 0.0 to 1.0 (where 1.0 is maximum effect)

### 3. Force Synchronization
```
POST /admin/market/trend/sync
```

Sample response:
```json
{
  "synchronized": true,
  "mode": "BULL",
  "strength": 0.7
}
```

## Redis CLI Monitoring

You can directly monitor trend settings in Redis using the Redis CLI:

```bash
# Connect to Redis
redis-cli

# Get current mode
GET market:trend:current_mode

# Get current strength
GET market:trend:current_strength

# Get last update timestamp
GET market:trend:last_update
```

## Log Monitoring

Both providers output logs that track trend changes and applications:

### Market Trend Updates
Look for these log patterns:

```
[UPDATE] [REST] - Market trend updated: TrendConfiguration{currentMode=BULL, currentStrength=0.7}, Redis updated: true
```

### Trend Application to Rates
Look for these log patterns (TCP Provider):

```
[UPDATE] [PF1] [PF1_USDTRY] [TREND:BULL STRENGTH:0.70 BIAS:0.000123] - Trend bias applied to rate
```

## Docker Configuration

You can configure initial trend settings through Docker:

```yaml
environment:
  MARKET_TREND_INITIAL_MODE: BULL
  MARKET_TREND_INITIAL_STRENGTH: 0.5
  TREND_REDIS_POLL_INTERVAL_SECONDS: 10
```

## Testing Trend Effects

To verify trends are working:

1. Monitor rate movements using the log output or by subscribing to rates
2. Set trend to `BULL` and observe if prices tend to rise over time
3. Set trend to `BEAR` and observe if prices tend to fall over time
4. Set trend to `NEUTRAL` to return to random movements

Remember that trend effects are probabilistic - they influence the direction but individual fluctuations may still go against the trend momentarily.

package com.toyota.market.trend.redis.impl;

import com.toyota.market.trend.redis.RedisTrendClient;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of RedisTrendClient using Jedis library.
 */
public class JedisRedisTrendClient implements RedisTrendClient {
    private static final Logger logger = Logger.getLogger(JedisRedisTrendClient.class.getName());
    private final JedisPool jedisPool;

    public JedisRedisTrendClient(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        logger.info("JedisRedisTrendClient initialized with pool: " + jedisPool);
    }

    @Override
    public String getString(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(key);
            logger.fine("Redis GET " + key + " = " + value);
            return value;
        } catch (JedisException e) {
            logger.log(Level.WARNING, "Error getting string from Redis: " + key, e);
            return null;
        }
    }

    @Override
    public void setString(String key, String value) {
        if (key == null || key.isEmpty() || value == null) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(key, value);
            logger.fine("Redis SET " + key + " = " + value);
        } catch (JedisException e) {
            logger.log(Level.WARNING, "Error setting string in Redis: " + key, e);
        }
    }

    @Override
    public boolean isAvailable() {
        try (Jedis jedis = jedisPool.getResource()) {
            String response = jedis.ping();
            boolean available = "PONG".equalsIgnoreCase(response);
            logger.fine("Redis availability check: " + available);
            return available;
        } catch (JedisException e) {
            logger.log(Level.WARNING, "Redis is not available", e);
            return false;
        }
    }
}

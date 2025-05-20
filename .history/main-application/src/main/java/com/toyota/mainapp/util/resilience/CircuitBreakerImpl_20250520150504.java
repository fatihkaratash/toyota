package com.toyota.mainapp.util.resilience;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the Circuit Breaker pattern.
 * In a production environment, consider using a library like Resilience4j.
 */
public class CircuitBreakerImpl implements CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerImpl.class);

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    
    private final int failureThreshold;
    private final int successThreshold;
    private final long resetTimeoutMs;
    
    private Instant resetTime;
    
    /**
     * Creates a new circuit breaker with the given settings.
     * 
     * @param failureThreshold Number of consecutive failures before opening the circuit
     * @param successThreshold Number of consecutive successes to close a half-open circuit
     * @param resetTimeoutMs Timeout in milliseconds before transitioning from open to half-open
     */
    public CircuitBreakerImpl(int failureThreshold, int successThreshold, long resetTimeoutMs) {
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
    }
    
    @Override
    public State getState() {
        // If circuit is open but reset time has passed, consider it half-open
        if (state.get() == State.OPEN && resetTime != null && 
            Instant.now().isAfter(resetTime)) {
            state.compareAndSet(State.OPEN, State.HALF_OPEN);
            return State.HALF_OPEN;
        }
        return state.get();
    }
    
    @Override
    public void onSuccess() {
        // Reset failure count on success
        failureCount.set(0);
        
        // If circuit is half-open, increment success count
        if (state.get() == State.HALF_OPEN) {
            int currentSuccesses = successCount.incrementAndGet();
            if (currentSuccesses >= successThreshold) {
                logger.info("Circuit breaker reset to CLOSED after {} consecutive successes", currentSuccesses);
                // Transition to closed if success threshold reached
                state.set(State.CLOSED);
                successCount.set(0);
            }
        }
    }
    
    @Override
    public void onFailure() {
        // Reset success count on failure
        successCount.set(0);
        
        // If circuit is closed, increment failure count
        if (state.get() == State.CLOSED) {
            int currentFailures = failureCount.incrementAndGet();
            if (currentFailures >= failureThreshold) {
                logger.warn("Circuit breaker tripped OPEN after {} consecutive failures", currentFailures);
                // Transition to open if failure threshold reached
                state.set(State.OPEN);
                failureCount.set(0);
                resetTime = Instant.now().plusMillis(resetTimeoutMs);
            }
        } else if (state.get() == State.HALF_OPEN) {
            // If circuit is half-open, any failure transitions to open
            logger.warn("Circuit breaker returned to OPEN state after failure in HALF_OPEN state");
            state.set(State.OPEN);
            successCount.set(0);
            resetTime = Instant.now().plusMillis(resetTimeoutMs);
        }
    }
    
    @Override
    public boolean isCallPermitted() {
        State currentState = getState();
        return currentState == State.CLOSED || currentState == State.HALF_OPEN;
    }
    
    @Override
    public <T> T execute(Supplier<T> supplier) throws Exception {
        if (!isCallPermitted()) {
            throw new CircuitBreakerOpenException("Circuit breaker is open");
        }
        
        try {
            T result = supplier.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }
}

package com.toyota.mainapp.util.resilience;

import java.util.function.Supplier;

/**
 * Conceptual interface for a Circuit Breaker pattern.
 * In a real application, use a library like Resilience4j.
 */
public interface CircuitBreaker {

    enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    /**
     * @return The current state of the circuit breaker.
     */
    State getState();

    /**
     * Records a successful call.
     */
    void onSuccess();

    /**
     * Records a failed call.
     */
    void onFailure();

    /**
     * Checks if the circuit breaker allows calls.
     * @return true if calls are permitted, false otherwise.
     */
    boolean isCallPermitted();

    /**
     * Executes the given supplier if the circuit breaker is closed or half-open.
     *
     * @param supplier The operation to execute.
     * @param <T> The type of the result.
     * @return The result of the supplier.
     * @throws CircuitBreakerOpenException if the circuit is open.
     * @throws Exception if the supplier throws an exception.
     */
    <T> Texecute(Supplier<T> supplier) throws Exception;
}

class CircuitBreakerOpenException extends RuntimeException {
    public CircuitBreakerOpenException(String message) {
        super(message);
    }
}

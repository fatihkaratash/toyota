package com.toyota.mainapp.util.resilience;

import java.util.function.Supplier;

/**
 * Conceptual interface for a Retry Handler pattern.
 * In a real application, use a library like Spring Retry or Resilience4j.
 */
public interface RetryHandler {

    /**
     * Executes the given supplier, retrying on failure according to the configured policy.
     *
     * @param supplier The operation to execute.
     * @param <T> The type of the result.
     * @return The result of the supplier if successful within retry attempts.
     * @throws Exception if the operation fails after all retry attempts.
     */
    <T> T executeWithRetries(Supplier<T> supplier) throws Exception;

    /**
     * Configures the maximum number of retry attempts.
     * @param maxAttempts Maximum attempts.
     */
    void setMaxAttempts(int maxAttempts);

    /**
     * Configures the delay between retry attempts.
     * @param delayMillis Delay in milliseconds.
     */
    void setDelayMillis(long delayMillis);
}
